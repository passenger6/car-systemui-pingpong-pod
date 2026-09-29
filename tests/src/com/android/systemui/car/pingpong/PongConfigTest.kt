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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** [PongConfig.coerced]: the ranges, without resources. */
@RunWith(AndroidJUnit4::class)
class PongConfigTest {
    private val sane = PongTestValues.sane

    @Test
    fun coerced_leavesPlayableValuesAlone() {
        val config = PongConfig.coerced(sane)

        assertThat(config.ballSpeedPx).isEqualTo(600f)
        assertThat(config.ballMaxSpeedPx).isEqualTo(1600f)
        assertThat(config.ballSpeedGain).isEqualTo(1.04f)
        assertThat(config.hitAngleDegrees).isEqualTo(60f)
        assertThat(config.paddleSpeedPx).isEqualTo(900f)
        assertThat(config.serveDelayMs).isEqualTo(1_200L)
        assertThat(config.jevIntervalMs).isEqualTo(200L)
        assertThat(config.jevStaleAfterMs).isEqualTo(8_000L)
        assertThat(config.lanes).isEqualTo(12)
        assertThat(config.winScore).isEqualTo(11)
        assertThat(config.jevPaddleId).isEqualTo("pong_right_paddle")
        assertThat(config.jevModel).isEqualTo("jev-latest")
        assertThat(config.jevEndpoint).isEqualTo("https://api.typesafe.ai/v1/systemone")
    }

    @Test
    fun coerced_ballSpeed_isAtLeastOnePixelPerSecond() {
        assertThat(PongConfig.coerced(sane.copy(ballSpeedPx = 0f)).ballSpeedPx).isEqualTo(1f)
        assertThat(PongConfig.coerced(sane.copy(ballSpeedPx = -50f)).ballSpeedPx).isEqualTo(1f)
    }

    @Test
    fun coerced_maxSpeed_isAtLeastTheServeSpeed() {
        val config = PongConfig.coerced(sane.copy(ballSpeedPx = 600f, ballMaxSpeedPx = 100f))

        assertThat(config.ballMaxSpeedPx).isEqualTo(600f)
    }

    @Test
    fun coerced_speedGain_isAtLeastOnePercent_butMayStillSlowTheBall() {
        assertThat(PongConfig.coerced(sane.copy(ballSpeedGain = 0f)).ballSpeedGain).isEqualTo(0.01f)
        assertThat(PongConfig.coerced(sane.copy(ballSpeedGain = 0.5f)).ballSpeedGain).isEqualTo(0.5f)
    }

    @Test
    fun coerced_hitAngle_staysBetweenZeroAndEightyNine() {
        assertThat(PongConfig.coerced(sane.copy(hitAngleDegrees = 120f)).hitAngleDegrees).isEqualTo(89f)
        assertThat(PongConfig.coerced(sane.copy(hitAngleDegrees = -5f)).hitAngleDegrees).isEqualTo(0f)
    }

    @Test
    fun coerced_paddleSpeed_isAtLeastOnePixelPerSecond() {
        assertThat(PongConfig.coerced(sane.copy(paddleSpeedPx = 0f)).paddleSpeedPx).isEqualTo(1f)
    }

    @Test
    fun coerced_delays_areNeverNegative() {
        val config = PongConfig.coerced(sane.copy(serveDelayMs = -1L, jevIntervalMs = -1L))

        assertThat(config.serveDelayMs).isEqualTo(0L)
        assertThat(config.jevIntervalMs).isEqualTo(0L)
    }

    @Test
    fun coerced_staleAfter_coversTheClientsTimeoutsPlusTheMargin() {
        val minimum = PongJevClient.CONNECT_TIMEOUT_MS + PongJevClient.READ_TIMEOUT_MS +
            PongConfig.JEV_STALE_MARGIN_MS

        assertThat(PongConfig.MIN_JEV_STALE_AFTER_MS).isEqualTo(minimum)
        assertThat(PongConfig.coerced(sane.copy(jevStaleAfterMs = 0L)).jevStaleAfterMs).isEqualTo(minimum)
        assertThat(PongConfig.coerced(sane.copy(jevStaleAfterMs = 8_000L)).jevStaleAfterMs).isEqualTo(8_000L)
    }

    @Test
    fun coerced_lanes_fitAChoiceQuestion() {
        assertThat(PongConfig.coerced(sane.copy(lanes = 1)).lanes).isEqualTo(2)
        assertThat(PongConfig.coerced(sane.copy(lanes = 1_000)).lanes).isEqualTo(255)
    }

    @Test
    fun coerced_winScore_isAtLeastOne() {
        assertThat(PongConfig.coerced(sane.copy(winScore = 0)).winScore).isEqualTo(1)
    }

    @Test
    fun rules_carryTheCoercedValues() {
        val config = PongConfig.coerced(sane.copy(ballSpeedGain = 0f, hitAngleDegrees = 120f))

        assertThat(config.rules).isEqualTo(PongRules(0.01f, 1600f, 89f))
    }

    @Test
    fun isJevPaddle_matchesTheConfiguredIdOnly() {
        val config = PongConfig.coerced(sane)

        assertThat(config.isJevPaddle("pong_right_paddle")).isTrue()
        assertThat(config.isJevPaddle("pong_left_paddle")).isFalse()
    }
}
