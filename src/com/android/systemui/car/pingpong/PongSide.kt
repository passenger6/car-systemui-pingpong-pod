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

/** The two halves of the court. Event ids carry the lower-case name: `_Pong_Point_left`. */
enum class PongSide {
    LEFT,
    RIGHT;

    val opposite: PongSide
        get() = if (this == LEFT) RIGHT else LEFT

    val eventSuffix: String
        get() = name.lowercase()

    companion object {
        /** Which half of [court] holds the centre of [bounds]. */
        fun of(bounds: Rect, court: Rect): PongSide =
            if (bounds.exactCenterX() < court.exactCenterX()) LEFT else RIGHT
    }
}
