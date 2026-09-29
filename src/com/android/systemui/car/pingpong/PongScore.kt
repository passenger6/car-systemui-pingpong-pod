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

/**
 * The score, owned by [PongGame] so it survives a match rebuild (a theme refresh recreates the
 * controllers and with them the match).
 *
 * The end of a game lives here for the same reason. A won game is announced once
 * ([takeWinnerToAnnounce]) and reset at the next serve ([resetIfGameOver]); both survive a match
 * rebuild, so a win is neither announced twice nor played past.
 */
class PongScore {
    var left: Int = 0
        private set
    var right: Int = 0
        private set

    /** The winner of a finished game, until the score is reset. */
    var gameWinner: PongSide? = null
        private set

    private var winnerToAnnounce: PongSide? = null

    /** True between the winning point and its `_Pong_Win_<side>`. */
    val winnerPending: Boolean
        get() = winnerToAnnounce != null

    /** Counts a point; returns the winner when this point ends the game, once per game. */
    fun point(side: PongSide, winScore: Int): PongSide? {
        if (side == PongSide.LEFT) left++ else right++
        if (gameWinner != null) return null
        val winner = when {
            left >= winScore -> PongSide.LEFT
            right >= winScore -> PongSide.RIGHT
            else -> null
        } ?: return null
        gameWinner = winner
        winnerToAnnounce = winner
        return winner
    }

    /** The winner whose `_Pong_Win_<side>` is still to be fired, or null; hands it out once. */
    fun takeWinnerToAnnounce(): PongSide? {
        val winner = winnerToAnnounce
        winnerToAnnounce = null
        return winner
    }

    /** Back to 0:0 when a game has been won; true when it was. */
    fun resetIfGameOver(): Boolean {
        if (gameWinner == null) return false
        reset()
        return true
    }

    fun reset() {
        left = 0
        right = 0
        gameWinner = null
        winnerToAnnounce = null
    }
}
