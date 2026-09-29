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

/**
 * The court's status line: one sentence per state the pod can detect (a missing panel, a court
 * without bounds, a ball without an app, a hidden ball, an XML that cannot serve from where the
 * ball is, Jev without a key).
 */
class PongStatus(private val context: Context) {

    fun waitingParts(missing: List<String>): String =
        context.getString(R.string.pong_status_waiting_parts, missing.joinToString(", "))

    fun panelMissing(panelId: String): String =
        context.getString(R.string.pong_status_panel_missing, panelId)

    fun sameSide(): String = context.getString(R.string.pong_status_paddles_same_side)

    fun courtNoBounds(): String = context.getString(R.string.pong_status_court_no_bounds)

    fun waitingBallApp(): String = context.getString(R.string.pong_status_waiting_ball_app)

    fun waitingPaddleApp(panelId: String): String =
        context.getString(R.string.pong_status_waiting_paddle_app, panelId)

    fun ballHidden(): String = context.getString(R.string.pong_status_ball_hidden)

    fun serveNotReachable(variantName: String): String =
        context.getString(R.string.pong_status_serve_not_reachable, variantName)

    fun noFlightVariant(): String = context.getString(R.string.pong_status_no_flight_variant)

    fun serving(): String = context.getString(R.string.pong_status_serving)

    fun win(sideLabel: String): String = context.getString(R.string.pong_status_win, sideLabel)

    fun label(isJev: Boolean): String =
        context.getString(if (isJev) R.string.pong_label_jev else R.string.pong_label_you)

    /** In play, with Jev's last word when Jev plays. */
    fun inPlay(jev: PongJevBrain.Status?): String {
        val play = context.getString(R.string.pong_status_in_play)
        val jevText = when (jev) {
            null -> return play
            PongJevBrain.Status.Idle -> context.getString(R.string.pong_status_jev_idle)
            PongJevBrain.Status.NoKey -> context.getString(R.string.pong_status_jev_no_key)
            is PongJevBrain.Status.Failed ->
                context.getString(R.string.pong_status_jev_failed, jev.reason)
            is PongJevBrain.Status.Answered ->
                context.getString(R.string.pong_status_jev_answer, jev.latencyMs, jev.confidence)
        }
        return "$play · $jevText"
    }
}
