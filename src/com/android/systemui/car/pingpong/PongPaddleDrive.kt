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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Where one paddle is and where it is going. A finger that holds the paddle wins over Jev's
 * target until it lets go. The paddle moves vertically only and never leaves the court.
 *
 * The pod drives a paddle only in its playing variant, the one it was in when the match found
 * it. While the XML holds it in another variant, or a transition is animating it, the paddle is
 * [isAway]: the pod neither moves nor stamps it, and [bounds] follow the variant, so the physics
 * uses the paddle's real position and the other variant's bounds are not overwritten.
 */
class PongPaddleDrive(val panelId: String, initialBounds: Rect, val playingVariant: String?) {
    val bounds: Rect = Rect(initialBounds)

    /** Distance from the finger to the paddle's centre while held, so a grab does not jump. */
    private var grabOffsetY: Float? = null

    /** The centre y Jev asked for; null means nowhere to go. */
    var targetCenterY: Float? = null

    /** True while the XML owns the paddle (another variant, or a running transition). */
    var isAway: Boolean = false
        private set

    val isHeld: Boolean
        get() = grabOffsetY != null

    /**
     * Once per frame: who owns the paddle right now. Away, the finger is released and the bounds
     * track the variant; back, the last tracked bounds are where the framework left the surface.
     */
    fun syncWithVariant(currentVariant: String?, variantBounds: Rect, animating: Boolean) {
        isAway = currentVariant != playingVariant || animating
        if (!isAway) return
        grabOffsetY = null
        bounds.set(variantBounds)
    }

    /** A finger on the paddle itself: it keeps its offset to the finger, so nothing jumps. */
    fun grab(rawY: Float) {
        if (isAway) return
        grabOffsetY = bounds.exactCenterY() - rawY
    }

    /** A finger on the paddle's half of the court: the paddle's centre comes to the finger. */
    fun holdAt(rawY: Float, court: Rect): Boolean {
        if (isAway) return false
        grabOffsetY = 0f
        return follow(rawY, court)
    }

    /** True when the paddle moved. */
    fun follow(rawY: Float, court: Rect): Boolean {
        val offset = grabOffsetY ?: return false
        return place(rawY + offset, court)
    }

    fun release() {
        grabOffsetY = null
    }

    /** One frame toward [targetCenterY] at [speedPx] per second; true when the paddle moved. */
    fun advance(deltaSeconds: Float, speedPx: Float, court: Rect): Boolean {
        if (isHeld || isAway) return false
        val target = targetCenterY ?: return false
        val current = bounds.exactCenterY()
        val distance = target - current
        val step = speedPx * deltaSeconds
        val next = if (abs(distance) <= step) target else current + sign(distance) * step
        return place(next, court)
    }

    private fun place(centerY: Float, court: Rect): Boolean {
        val half = bounds.height() / 2f
        // A court shorter than the paddle has no room to clamp in; centre the paddle instead.
        val clamped = if (court.height() < bounds.height()) {
            court.exactCenterY()
        } else {
            centerY.coerceIn(court.top + half, court.bottom - half)
        }
        val top = (clamped - half).roundToInt()
        if (top == bounds.top) return false
        bounds.offsetTo(bounds.left, top)
        return true
    }
}
