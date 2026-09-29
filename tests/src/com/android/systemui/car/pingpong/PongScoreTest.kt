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

@RunWith(AndroidJUnit4::class)
class PongScoreTest {
    private val score = PongScore()

    @Test
    fun starts_atZeroWithNoWinner() {
        assertThat(score.left).isEqualTo(0)
        assertThat(score.right).isEqualTo(0)
        assertThat(score.gameWinner).isNull()
        assertThat(score.winnerPending).isFalse()
    }

    @Test
    fun point_countsForTheScorer() {
        score.point(PongSide.LEFT, winScore = 11)
        score.point(PongSide.RIGHT, winScore = 11)
        score.point(PongSide.RIGHT, winScore = 11)

        assertThat(score.left).isEqualTo(1)
        assertThat(score.right).isEqualTo(2)
    }

    @Test
    fun point_belowTheWinScore_namesNoWinner() {
        assertThat(score.point(PongSide.LEFT, winScore = 2)).isNull()
        assertThat(score.gameWinner).isNull()
        assertThat(score.winnerPending).isFalse()
    }

    @Test
    fun point_reachingTheWinScore_namesTheWinnerOnce() {
        score.point(PongSide.RIGHT, winScore = 2)

        assertThat(score.point(PongSide.RIGHT, winScore = 2)).isEqualTo(PongSide.RIGHT)
        assertThat(score.gameWinner).isEqualTo(PongSide.RIGHT)
        assertThat(score.winnerPending).isTrue()
    }

    @Test
    fun point_afterTheGameIsWon_countsButNamesNoSecondWinner() {
        score.point(PongSide.LEFT, winScore = 1)

        assertThat(score.point(PongSide.LEFT, winScore = 1)).isNull()
        assertThat(score.point(PongSide.RIGHT, winScore = 1)).isNull()
        assertThat(score.left).isEqualTo(2)
        assertThat(score.gameWinner).isEqualTo(PongSide.LEFT)
    }

    @Test
    fun takeWinnerToAnnounce_handsTheWinnerOutOnce() {
        score.point(PongSide.LEFT, winScore = 1)

        assertThat(score.takeWinnerToAnnounce()).isEqualTo(PongSide.LEFT)
        assertThat(score.winnerPending).isFalse()
        // A match rebuilt between the point and its announcement asks again and gets nothing.
        assertThat(score.takeWinnerToAnnounce()).isNull()
        assertThat(score.gameWinner).isEqualTo(PongSide.LEFT)
    }

    @Test
    fun resetIfGameOver_withoutAWin_leavesTheScoreAlone() {
        score.point(PongSide.LEFT, winScore = 11)

        assertThat(score.resetIfGameOver()).isFalse()
        assertThat(score.left).isEqualTo(1)
    }

    @Test
    fun resetIfGameOver_afterAWin_startsANewGame() {
        score.point(PongSide.RIGHT, winScore = 1)
        score.takeWinnerToAnnounce()

        assertThat(score.resetIfGameOver()).isTrue()
        assertThat(score.left).isEqualTo(0)
        assertThat(score.right).isEqualTo(0)
        assertThat(score.gameWinner).isNull()
        assertThat(score.winnerPending).isFalse()
    }

    @Test
    fun reset_dropsAPendingAnnouncementToo() {
        score.point(PongSide.RIGHT, winScore = 1)

        score.reset()

        assertThat(score.winnerPending).isFalse()
        assertThat(score.takeWinnerToAnnounce()).isNull()
        assertThat(score.gameWinner).isNull()
    }
}
