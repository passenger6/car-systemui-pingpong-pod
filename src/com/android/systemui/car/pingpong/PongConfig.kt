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

import android.content.Context
import kotlin.math.max

/** The raw numbers an RRO can set, before [PongConfig.coerced] makes them playable. */
data class PongConfigValues(
    val ballSpeedPx: Float,
    val ballMaxSpeedPx: Float,
    val ballSpeedGain: Float,
    val hitAngleDegrees: Float,
    val paddleSpeedPx: Float,
    val serveDelayMs: Long,
    val jevIntervalMs: Long,
    val jevStaleAfterMs: Long,
    val lanes: Int,
    val winScore: Int,
    val jevPaddleId: String,
    val jevModel: String,
    val jevEndpoint: String,
)

/**
 * Everything tunable, read once per process from resources (an RRO on `com.android.systemui`
 * can override any of them; SystemUI restarts to pick an RRO up). Speeds are given in dp/s and
 * converted to px/s with the display density.
 *
 * Every value is coerced into a playable range, so an override cannot stop the ball, send it
 * back into the paddle, or set a watchdog shorter than the HTTP timeouts it backs up.
 */
class PongConfig private constructor(
    val ballSpeedPx: Float,
    val ballMaxSpeedPx: Float,
    val ballSpeedGain: Float,
    val hitAngleDegrees: Float,
    val paddleSpeedPx: Float,
    val serveDelayMs: Long,
    val jevIntervalMs: Long,
    val jevStaleAfterMs: Long,
    val lanes: Int,
    val winScore: Int,
    val jevPaddleId: String,
    val jevModel: String,
    val jevEndpoint: String,
) {
    val rules: PongRules = PongRules(ballSpeedGain, ballMaxSpeedPx, hitAngleDegrees)

    fun isJevPaddle(panelId: String): Boolean = panelId == jevPaddleId

    companion object {
        /** Slack the staleness watchdog keeps beyond the HTTP timeouts, so a slow but live
         *  answer is never declared stale a moment before it lands. */
        const val JEV_STALE_MARGIN_MS = 1_000L
        const val MIN_JEV_STALE_AFTER_MS =
            PongJevClient.CONNECT_TIMEOUT_MS + PongJevClient.READ_TIMEOUT_MS + JEV_STALE_MARGIN_MS
        private const val MAX_HIT_ANGLE_DEGREES = 89f
        private const val MIN_SPEED_GAIN = 0.01f
        /** A Choice question takes 2..255 options. */
        private const val MIN_LANES = 2
        private const val MAX_LANES = 255

        fun fromResources(context: Context): PongConfig {
            val resources = context.resources
            val density = resources.displayMetrics.density
            fun dpPerSecond(id: Int): Float = resources.getInteger(id) * density
            return coerced(
                PongConfigValues(
                    ballSpeedPx = dpPerSecond(R.integer.pong_ball_speed_dp),
                    ballMaxSpeedPx = dpPerSecond(R.integer.pong_ball_max_speed_dp),
                    ballSpeedGain = resources.getInteger(R.integer.pong_ball_speed_gain_percent) / 100f,
                    hitAngleDegrees = resources.getInteger(R.integer.pong_hit_angle_degrees).toFloat(),
                    paddleSpeedPx = dpPerSecond(R.integer.pong_paddle_speed_dp),
                    serveDelayMs = resources.getInteger(R.integer.pong_serve_delay_ms).toLong(),
                    jevIntervalMs = resources.getInteger(R.integer.pong_jev_interval_ms).toLong(),
                    jevStaleAfterMs = resources.getInteger(R.integer.pong_jev_stale_after_ms).toLong(),
                    lanes = resources.getInteger(R.integer.pong_lanes),
                    winScore = resources.getInteger(R.integer.pong_win_score),
                    jevPaddleId = resources.getString(R.string.pong_jev_paddle),
                    jevModel = resources.getString(R.string.pong_jev_model),
                    jevEndpoint = resources.getString(R.string.pong_jev_endpoint),
                ),
            )
        }

        /** The playable version of [values]; pure, so the ranges are testable without resources. */
        fun coerced(values: PongConfigValues): PongConfig {
            val ballSpeedPx = max(1f, values.ballSpeedPx)
            return PongConfig(
                ballSpeedPx = ballSpeedPx,
                ballMaxSpeedPx = max(ballSpeedPx, values.ballMaxSpeedPx),
                ballSpeedGain = max(MIN_SPEED_GAIN, values.ballSpeedGain),
                hitAngleDegrees = values.hitAngleDegrees.coerceIn(0f, MAX_HIT_ANGLE_DEGREES),
                paddleSpeedPx = max(1f, values.paddleSpeedPx),
                serveDelayMs = values.serveDelayMs.coerceAtLeast(0L),
                jevIntervalMs = values.jevIntervalMs.coerceAtLeast(0L),
                jevStaleAfterMs = values.jevStaleAfterMs.coerceAtLeast(MIN_JEV_STALE_AFTER_MS),
                lanes = values.lanes.coerceIn(MIN_LANES, MAX_LANES),
                winScore = values.winScore.coerceAtLeast(1),
                jevPaddleId = values.jevPaddleId,
                jevModel = values.jevModel,
                jevEndpoint = values.jevEndpoint,
            )
        }
    }
}
