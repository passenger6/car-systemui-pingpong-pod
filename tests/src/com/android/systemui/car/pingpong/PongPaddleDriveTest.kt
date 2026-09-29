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

/** [PongPaddleDrive] for a 220 px paddle whose playing variant is `open`, centred at y = 510. */
@RunWith(AndroidJUnit4::class)
class PongPaddleDriveTest {
    private val court = Rect(0, 0, 2000, 1000)
    private val atRest = Rect(100, 400, 124, 620)
    private val drive = PongPaddleDrive("pong_left_paddle", atRest, playingVariant = "open")

    @Test
    fun bounds_areACopyOfTheInitialRect() {
        atRest.offset(0, 100)

        assertThat(drive.bounds).isEqualTo(Rect(100, 400, 124, 620))
        assertThat(drive.isHeld).isFalse()
        assertThat(drive.isAway).isFalse()
    }

    @Test
    fun grab_keepsTheFingersOffsetToTheCentre() {
        drive.grab(rawY = 550f)

        assertThat(drive.isHeld).isTrue()
        assertThat(drive.follow(rawY = 600f, court)).isTrue()
        assertThat(drive.bounds.exactCenterY()).isWithin(0.5f).of(560f)
    }

    @Test
    fun holdAt_bringsTheCentreToTheFinger() {
        assertThat(drive.holdAt(rawY = 700f, court)).isTrue()

        assertThat(drive.isHeld).isTrue()
        assertThat(drive.bounds.exactCenterY()).isWithin(0.5f).of(700f)
        assertThat(drive.bounds.width()).isEqualTo(24)
        assertThat(drive.bounds.height()).isEqualTo(220)
    }

    @Test
    fun follow_withoutAFinger_movesNothing() {
        assertThat(drive.follow(rawY = 700f, court)).isFalse()
        assertThat(drive.bounds).isEqualTo(Rect(100, 400, 124, 620))
    }

    @Test
    fun release_endsTheHold() {
        drive.holdAt(rawY = 700f, court)

        drive.release()

        assertThat(drive.isHeld).isFalse()
        assertThat(drive.follow(rawY = 800f, court)).isFalse()
    }

    @Test
    fun advance_movesTowardTheTargetAtTheGivenSpeed() {
        drive.targetCenterY = 810f

        assertThat(drive.advance(deltaSeconds = 0.1f, speedPx = 1000f, court)).isTrue()
        assertThat(drive.bounds.exactCenterY()).isWithin(0.5f).of(610f)
        assertThat(drive.advance(deltaSeconds = 0.1f, speedPx = 1000f, court)).isTrue()
        assertThat(drive.bounds.exactCenterY()).isWithin(0.5f).of(710f)
        assertThat(drive.advance(deltaSeconds = 0.1f, speedPx = 1000f, court)).isTrue()
        assertThat(drive.bounds.exactCenterY()).isWithin(0.5f).of(810f)
        // Arrived: nothing moves any more.
        assertThat(drive.advance(deltaSeconds = 0.1f, speedPx = 1000f, court)).isFalse()
    }

    @Test
    fun advance_closerThanOneStep_landsOnTheTarget() {
        drive.targetCenterY = 520f

        assertThat(drive.advance(deltaSeconds = 0.1f, speedPx = 1000f, court)).isTrue()
        assertThat(drive.bounds.exactCenterY()).isWithin(0.5f).of(520f)
    }

    @Test
    fun advance_withoutATarget_movesNothing() {
        assertThat(drive.advance(deltaSeconds = 0.1f, speedPx = 1000f, court)).isFalse()
    }

    @Test
    fun advance_whileHeld_yieldsToTheFinger() {
        drive.holdAt(rawY = 700f, court)
        drive.targetCenterY = 100f

        assertThat(drive.advance(deltaSeconds = 0.1f, speedPx = 1000f, court)).isFalse()
        assertThat(drive.bounds.exactCenterY()).isWithin(0.5f).of(700f)
    }

    @Test
    fun place_neverLeavesTheCourt() {
        drive.holdAt(rawY = -500f, court)
        assertThat(drive.bounds.top).isEqualTo(court.top)

        drive.holdAt(rawY = 5000f, court)
        assertThat(drive.bounds.bottom).isEqualTo(court.bottom)
    }

    @Test
    fun place_inACourtShorterThanThePaddle_centresIt() {
        val lowCourt = Rect(0, 0, 2000, 100)

        drive.holdAt(rawY = 80f, lowCourt)

        assertThat(drive.bounds.exactCenterY()).isWithin(0.5f).of(50f)
    }

    @Test
    fun syncWithVariant_inAnotherVariant_handsThePaddleToTheXml() {
        val gone = Rect(-160, 400, -136, 620)
        drive.holdAt(rawY = 700f, court)

        drive.syncWithVariant("gone", gone, animating = false)

        assertThat(drive.isAway).isTrue()
        assertThat(drive.isHeld).isFalse()
        assertThat(drive.bounds).isEqualTo(gone)
        assertThat(drive.grabIgnored()).isTrue()
        assertThat(drive.holdAt(rawY = 500f, court)).isFalse()
        drive.targetCenterY = 500f
        assertThat(drive.advance(deltaSeconds = 0.1f, speedPx = 1000f, court)).isFalse()
        assertThat(drive.bounds).isEqualTo(gone)
    }

    @Test
    fun syncWithVariant_whileAnimatingInThePlayingVariant_isAwayToo() {
        val target = Rect(100, 300, 124, 520)

        drive.syncWithVariant("open", target, animating = true)

        assertThat(drive.isAway).isTrue()
        assertThat(drive.bounds).isEqualTo(target)
    }

    @Test
    fun syncWithVariant_backInThePlayingVariant_resumesWhereTheFrameworkLeftIt() {
        val gone = Rect(-160, 400, -136, 620)
        val back = Rect(100, 300, 124, 520)
        drive.syncWithVariant("gone", gone, animating = false)
        drive.syncWithVariant("open", back, animating = true)

        drive.syncWithVariant("open", drive.bounds, animating = false)

        assertThat(drive.isAway).isFalse()
        assertThat(drive.bounds).isEqualTo(back)
        drive.targetCenterY = 510f
        assertThat(drive.advance(deltaSeconds = 0.1f, speedPx = 1000f, court)).isTrue()
        assertThat(drive.bounds.exactCenterY()).isWithin(0.5f).of(510f)
    }

    @Test
    fun syncWithVariant_inThePlayingVariant_keepsThePodsBounds() {
        drive.holdAt(rawY = 700f, court)
        val stamped = Rect(drive.bounds)

        drive.syncWithVariant("open", stamped, animating = false)

        assertThat(drive.isAway).isFalse()
        assertThat(drive.isHeld).isTrue()
        assertThat(drive.bounds).isEqualTo(stamped)
    }

    @Test
    fun syncWithVariant_withNoPlayingVariant_isNeverAway() {
        val stateless = PongPaddleDrive("pong_right_paddle", atRest, playingVariant = null)

        stateless.syncWithVariant(null, atRest, animating = false)

        assertThat(stateless.isAway).isFalse()
    }

    /** True when a grab is refused: the finger is not holding the paddle afterwards. */
    private fun PongPaddleDrive.grabIgnored(): Boolean {
        grab(rawY = 500f)
        return !isHeld
    }
}
