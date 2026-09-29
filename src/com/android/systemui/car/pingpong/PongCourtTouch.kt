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

import android.view.MotionEvent

/**
 * A touch on the court, copied out of the [MotionEvent] on the view's thread. The event is
 * recycled once the view returns, so it must not cross to the shell thread. Each finger is its
 * own pointer: a second one steers the other paddle, and a paddle is released by the finger
 * that grabbed it, whichever half it is over by then.
 */
sealed interface PongCourtTouch {
    data class Pointer(val pointerId: Int, val rawX: Float, val rawY: Float)

    data class Down(val pointer: Pointer) : PongCourtTouch
    data class Move(val pointers: List<Pointer>) : PongCourtTouch
    data class Up(val pointerId: Int) : PongCourtTouch
    object Cancel : PongCourtTouch

    companion object {
        /** The touch this event is, or null for the actions the court does not react to. */
        fun from(event: MotionEvent): PongCourtTouch? = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                Down(pointerAt(event, event.actionIndex))
            MotionEvent.ACTION_MOVE ->
                Move(List(event.pointerCount) { index -> pointerAt(event, index) })
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
                Up(event.getPointerId(event.actionIndex))
            MotionEvent.ACTION_CANCEL -> Cancel
            else -> null
        }

        private fun pointerAt(event: MotionEvent, index: Int): Pointer =
            Pointer(event.getPointerId(index), event.getRawX(index), event.getRawY(index))
    }
}
