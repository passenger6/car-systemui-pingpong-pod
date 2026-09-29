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

/** A config every range accepts as it is; a test copies it and breaks one value. */
object PongTestValues {
    val sane = PongConfigValues(
        ballSpeedPx = 600f,
        ballMaxSpeedPx = 1600f,
        ballSpeedGain = 1.04f,
        hitAngleDegrees = 60f,
        paddleSpeedPx = 900f,
        serveDelayMs = 1_200L,
        jevIntervalMs = 200L,
        jevStaleAfterMs = 8_000L,
        lanes = 12,
        winScore = 11,
        jevPaddleId = "pong_right_paddle",
        jevModel = "jev-latest",
        jevEndpoint = "https://api.typesafe.ai/v1/systemone",
    )
}
