/*
 * Copyright (C) 2026 Daniel Georg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.car.pingpong

import android.graphics.Rect
import android.os.SystemClock
import org.json.JSONException

/**
 * When to ask Jev, and what to do with the answer. At most one question is in flight; a new one
 * goes out once [PongConfig.jevIntervalMs] have passed or the ball's course changed (a wall or
 * a paddle), whichever comes first. Between answers the paddle keeps driving to the last lane.
 * While the ball moves away, no question is asked and the paddle returns to the middle.
 *
 * A question stuck past [PongConfig.jevStaleAfterMs] is given up on, so the paddle keeps asking.
 *
 * Shell main thread only; the gateway posts its outcome back here.
 */
class PongJevBrain(
    private val config: PongConfig,
    private val gateway: PongJevGateway,
    private val clock: () -> Long = SystemClock::uptimeMillis,
) {
    sealed interface Status {
        object Idle : Status
        object NoKey : Status
        data class Failed(val reason: String) : Status
        data class Answered(val latencyMs: Long, val confidence: Double, val lane: Int) : Status
    }

    var status: Status = Status.Idle
        private set

    private var requestInFlight = false
    private var lastRequestAt: Long? = null
    private var courseChangedSinceRequest = false

    // Bumped on every request and captured by its callback. A callback whose generation has
    // moved on belongs to a request given up on as stale, and its outcome is dropped.
    private var requestGeneration = 0

    /** Every frame: steer [paddle] for the ball as it is now. */
    fun observe(
        ball: PongBall,
        court: Rect,
        paddle: PongPaddleDrive,
        opponentPaddle: Rect,
        mySide: PongSide,
        courseChanged: Boolean,
    ) {
        if (courseChanged) courseChangedSinceRequest = true
        if (!ball.approaches(mySide)) {
            paddle.targetCenterY = court.exactCenterY()
            // The last answer is outdated. A failure stays up: it is the only place its reason
            // is shown.
            if (status is Status.Answered) status = Status.Idle
            return
        }
        val now = clock()
        // An HTTP call that outlives its own timeouts (e.g. on a half-open socket) may never call
        // back. Give up on it: free the slot for a new question and bump the generation so a
        // late outcome is dropped.
        if (requestInFlight) {
            val staleSince = lastRequestAt
            if (staleSince != null && now - staleSince >= config.jevStaleAfterMs) {
                requestInFlight = false
                requestGeneration++
                status = Status.Failed("no answer after ${config.jevStaleAfterMs}ms")
            } else {
                return
            }
        }
        val previous = lastRequestAt
        val due = previous == null || now - previous >= config.jevIntervalMs
        if (!due && !courseChangedSinceRequest) return

        requestInFlight = true
        lastRequestAt = now
        courseChangedSinceRequest = false
        val generation = ++requestGeneration
        val request = PongJevQuestion.request(
            config.jevModel, court, ball, paddle.bounds, mySide, opponentPaddle, config.lanes)
        val courtAtAsk = Rect(court)
        gateway.ask(config.jevEndpoint, request) { outcome ->
            // The staleness check above may have given up on this request already.
            if (generation != requestGeneration) return@ask
            requestInFlight = false
            status = when (outcome) {
                is PongJevOutcome.NoKey -> Status.NoKey
                is PongJevOutcome.Failure -> Status.Failed(outcome.reason)
                is PongJevOutcome.Answer -> answered(outcome, courtAtAsk, paddle)
            }
        }
    }

    private fun answered(
        outcome: PongJevOutcome.Answer,
        court: Rect,
        paddle: PongPaddleDrive,
    ): Status {
        val answer = try {
            PongJevQuestion.parse(outcome.body, config.lanes)
        } catch (e: JSONException) {
            return Status.Failed(oneLineReason("unexpected answer: ${e.message}"))
        }
        paddle.targetCenterY = PongJevQuestion.laneCenterY(court, config.lanes, answer.lane)
        return Status.Answered(outcome.latencyMs, answer.confidence, answer.lane)
    }
}
