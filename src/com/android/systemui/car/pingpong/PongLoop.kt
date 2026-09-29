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

import android.view.Choreographer

/**
 * One Choreographer frame callback, re-posted while running. Lives on the shell main thread:
 * `wmshell.main` is a HandlerThread with a Looper, so Choreographer works there and the frame
 * lands on the same thread that owns the panels and the surface transactions.
 *
 * The delta handed to each frame is capped at [MAX_FRAME_SECONDS]: after a stall the next
 * frame advances the ball by at most that much flight, so it cannot tunnel through a paddle.
 * Motion is driven by time, so a 30 Hz display gets the same speed as a 60 Hz one.
 */
class PongLoop {
    private var active: Choreographer.FrameCallback? = null
    private var lastFrameNanos = 0L

    val isRunning: Boolean
        get() = active != null

    fun start(onFrame: (deltaSeconds: Float) -> Unit) {
        stop()
        lastFrameNanos = 0L
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (active !== this) return
                val delta = if (lastFrameNanos == 0L) {
                    0f
                } else {
                    ((frameTimeNanos - lastFrameNanos) / NANOS_PER_SECOND).coerceAtMost(MAX_FRAME_SECONDS)
                }
                lastFrameNanos = frameTimeNanos
                onFrame(delta)
                // The frame may have stopped the loop; only a still-active callback re-posts.
                if (active === this) Choreographer.getInstance().postFrameCallback(this)
            }
        }
        active = callback
        Choreographer.getInstance().postFrameCallback(callback)
    }

    fun stop() {
        active?.let { Choreographer.getInstance().removeFrameCallback(it) }
        active = null
    }

    companion object {
        const val MAX_FRAME_SECONDS = 0.05f
        private const val NANOS_PER_SECOND = 1_000_000_000f
    }
}
