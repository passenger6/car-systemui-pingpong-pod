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
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PongSideTest {
    private val court = Rect(0, 0, 2000, 1000)

    @Test
    fun opposite_swapsTheHalves() {
        assertThat(PongSide.LEFT.opposite).isEqualTo(PongSide.RIGHT)
        assertThat(PongSide.RIGHT.opposite).isEqualTo(PongSide.LEFT)
    }

    @Test
    fun eventSuffix_isTheLowerCaseName() {
        assertThat(PongSide.LEFT.eventSuffix).isEqualTo("left")
        assertThat(PongSide.RIGHT.eventSuffix).isEqualTo("right")
    }

    @Test
    fun of_readsTheHalfOffTheCentre() {
        assertThat(PongSide.of(Rect(100, 400, 124, 620), court)).isEqualTo(PongSide.LEFT)
        assertThat(PongSide.of(Rect(1876, 400, 1900, 620), court)).isEqualTo(PongSide.RIGHT)
        // A panel straddling the net counts by its centre, not its edges.
        assertThat(PongSide.of(Rect(900, 0, 1010, 100), court)).isEqualTo(PongSide.LEFT)
        assertThat(PongSide.of(Rect(990, 0, 1100, 100), court)).isEqualTo(PongSide.RIGHT)
    }
}
