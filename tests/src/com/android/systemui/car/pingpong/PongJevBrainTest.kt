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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

/** [PongJevBrain] against a gateway the test answers by hand and a clock it moves by hand. */
@RunWith(AndroidJUnit4::class)
class PongJevBrainTest {

    private class FakeGateway : PongJevGateway {
        class Question(
            val endpoint: String,
            val request: JSONObject,
            val onOutcome: (PongJevOutcome) -> Unit,
        )

        val questions = ArrayList<Question>()

        override fun ask(endpoint: String, request: JSONObject, onOutcome: (PongJevOutcome) -> Unit) {
            questions.add(Question(endpoint, request, onOutcome))
        }
    }

    private val config = PongConfig.coerced(
        PongTestValues.sane.copy(jevIntervalMs = 200L, jevStaleAfterMs = 8_000L, lanes = 4))
    private val court = Rect(0, 0, 2000, 1000)
    private val opponent = Rect(100, 400, 124, 620)
    private val paddle = PongPaddleDrive("pong_right_paddle", Rect(1876, 400, 1900, 620), "open")
    private val gateway = FakeGateway()
    private var now = 0L
    private val brain = PongJevBrain(config, gateway) { now }

    private val approaching = PongBall(1000f, 500f, 400f, 0f, halfWidth = 20f, halfHeight = 20f)
    private val leaving = approaching.copy(velocityX = -400f)

    private fun observe(ball: PongBall, courseChanged: Boolean = false) {
        brain.observe(ball, court, paddle, opponent, PongSide.RIGHT, courseChanged)
    }

    private fun answerBody(lane: Int, confidence: Double = 0.9): String =
        "{\"answers\":{\"landing_lane\":{\"choice\":\"lane_$lane\",\"confidence\":$confidence}}}"

    private fun laneCenter(lane: Int): Float = PongJevQuestion.laneCenterY(court, config.lanes, lane)

    @Test
    fun observe_ballLeaving_asksNothingAndReturnsToTheMiddle() {
        observe(leaving)

        assertThat(gateway.questions).isEmpty()
        assertThat(paddle.targetCenterY).isEqualTo(court.exactCenterY())
        assertThat(brain.status).isEqualTo(PongJevBrain.Status.Idle)
    }

    @Test
    fun observe_ballApproaching_asksOnceAtTheConfiguredEndpoint() {
        observe(approaching)
        now += 50
        observe(approaching)

        assertThat(gateway.questions).hasSize(1)
        assertThat(gateway.questions[0].endpoint).isEqualTo(config.jevEndpoint)
        assertThat(gateway.questions[0].request.getString("model")).isEqualTo(config.jevModel)
    }

    @Test
    fun answer_steersThePaddleToTheLaneAndShowsTheLatency() {
        observe(approaching)

        gateway.questions[0].onOutcome(PongJevOutcome.Answer(answerBody(3, 0.82), latencyMs = 143))

        assertThat(brain.status).isEqualTo(PongJevBrain.Status.Answered(143, 0.82, 3))
        assertThat(paddle.targetCenterY).isEqualTo(laneCenter(3))
    }

    @Test
    fun observe_afterAnAnswer_waitsForTheInterval() {
        observe(approaching)
        gateway.questions[0].onOutcome(PongJevOutcome.Answer(answerBody(1), latencyMs = 100))

        now = 100
        observe(approaching)
        assertThat(gateway.questions).hasSize(1)

        now = 200
        observe(approaching)
        assertThat(gateway.questions).hasSize(2)
    }

    @Test
    fun observe_courseChanged_asksBeforeTheInterval() {
        observe(approaching)
        gateway.questions[0].onOutcome(PongJevOutcome.Answer(answerBody(1), latencyMs = 100))

        now = 50
        observe(approaching, courseChanged = true)

        assertThat(gateway.questions).hasSize(2)
    }

    @Test
    fun observe_courseChangedWhileInFlight_isRememberedForTheNextQuestion() {
        observe(approaching)
        now = 50
        observe(approaching, courseChanged = true)
        assertThat(gateway.questions).hasSize(1)

        gateway.questions[0].onOutcome(PongJevOutcome.Answer(answerBody(1), latencyMs = 100))
        now = 60
        observe(approaching)

        assertThat(gateway.questions).hasSize(2)
    }

    @Test
    fun watchdog_givesUpOnASilentQuestionAndAsksAgain() {
        observe(approaching)

        now = config.jevStaleAfterMs
        observe(approaching)

        assertThat(brain.status).isEqualTo(
            PongJevBrain.Status.Failed("no answer after ${config.jevStaleAfterMs}ms"))
        assertThat(gateway.questions).hasSize(2)
    }

    @Test
    fun watchdog_dropsTheLateAnswerOfAQuestionItGaveUpOn() {
        observe(approaching)
        now = config.jevStaleAfterMs
        observe(approaching)
        val givenUp = gateway.questions[0]
        val current = gateway.questions[1]

        givenUp.onOutcome(PongJevOutcome.Answer(answerBody(0), latencyMs = 9_000))

        assertThat(brain.status).isInstanceOf(PongJevBrain.Status.Failed::class.java)
        assertThat(paddle.targetCenterY).isNull()

        current.onOutcome(PongJevOutcome.Answer(answerBody(2), latencyMs = 120))

        assertThat(brain.status).isEqualTo(PongJevBrain.Status.Answered(120, 0.9, 2))
        assertThat(paddle.targetCenterY).isEqualTo(laneCenter(2))
    }

    @Test
    fun noKey_isShownAsSuch() {
        observe(approaching)

        gateway.questions[0].onOutcome(PongJevOutcome.NoKey)

        assertThat(brain.status).isEqualTo(PongJevBrain.Status.NoKey)
    }

    @Test
    fun failure_showsItsReason() {
        observe(approaching)

        gateway.questions[0].onOutcome(PongJevOutcome.Failure("HTTP 401"))

        assertThat(brain.status).isEqualTo(PongJevBrain.Status.Failed("HTTP 401"))
    }

    @Test
    fun malformedAnswer_isAFailureNotALane() {
        observe(approaching)

        gateway.questions[0].onOutcome(PongJevOutcome.Answer("{\"answers\":{}}", latencyMs = 80))

        val status = brain.status
        assertThat(status).isInstanceOf(PongJevBrain.Status.Failed::class.java)
        assertThat((status as PongJevBrain.Status.Failed).reason).startsWith("unexpected answer")
        assertThat(paddle.targetCenterY).isNull()
    }

    @Test
    fun answered_becomesIdleWhenTheBallLeaves_butAFailureStays() {
        observe(approaching)
        gateway.questions[0].onOutcome(PongJevOutcome.Answer(answerBody(1), latencyMs = 100))

        observe(leaving)
        assertThat(brain.status).isEqualTo(PongJevBrain.Status.Idle)

        now = 500
        observe(approaching)
        gateway.questions[1].onOutcome(PongJevOutcome.Failure("HTTP 500"))
        observe(leaving)
        assertThat(brain.status).isEqualTo(PongJevBrain.Status.Failed("HTTP 500"))
    }

    @Test
    fun afterAFailure_theNextQuestionGoesOutAfterTheInterval() {
        observe(approaching)
        gateway.questions[0].onOutcome(PongJevOutcome.Failure("HTTP 500"))

        now = config.jevIntervalMs
        observe(approaching)

        assertThat(gateway.questions).hasSize(2)
    }
}
