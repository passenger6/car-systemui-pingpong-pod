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
import kotlin.math.abs
import kotlin.math.atan2

/** [PongPhysics] on a 2000 × 1000 court with 24 px paddles at x = 100..124 and 1876..1900. */
@RunWith(AndroidJUnit4::class)
class PongPhysicsTest {
    private val court = Rect(0, 0, 2000, 1000)
    private val leftPaddle = Rect(100, 400, 124, 620)
    private val rightPaddle = Rect(1876, 400, 1900, 620)
    private val rules = PongRules(speedGain = 1.04f, maxSpeedPx = 1600f, hitAngleDegrees = 60f)

    private fun ball(centerX: Float, centerY: Float, velocityX: Float, velocityY: Float) =
        PongBall(centerX, centerY, velocityX, velocityY, halfWidth = 20f, halfHeight = 20f)

    private fun step(ball: PongBall, deltaSeconds: Float): PongStep =
        PongPhysics.step(ball, court, leftPaddle, rightPaddle, deltaSeconds, rules)

    private fun flight(step: PongStep): PongStep.Flight {
        assertThat(step).isInstanceOf(PongStep.Flight::class.java)
        return step as PongStep.Flight
    }

    @Test
    fun serve_towardLeftAtZeroDegrees_fliesStraightLeft() {
        val served = PongPhysics.serve(500f, 400f, 30f, 30f, 600f, PongSide.LEFT, 0f)

        assertThat(served.centerX).isEqualTo(500f)
        assertThat(served.centerY).isEqualTo(400f)
        assertThat(served.halfWidth).isEqualTo(30f)
        assertThat(served.halfHeight).isEqualTo(30f)
        assertThat(served.velocityX).isWithin(0.01f).of(-600f)
        assertThat(served.velocityY).isWithin(0.01f).of(0f)
        assertThat(served.approaches(PongSide.LEFT)).isTrue()
    }

    @Test
    fun serve_towardRightAtAnAngle_keepsTheSpeedAndHeadsDown() {
        val served = PongPhysics.serve(500f, 400f, 30f, 30f, 600f, PongSide.RIGHT, 20f)

        assertThat(served.velocityX).isGreaterThan(0f)
        assertThat(served.velocityY).isGreaterThan(0f)
        assertThat(served.speed).isWithin(0.01f).of(600f)
        assertThat(served.approaches(PongSide.RIGHT)).isTrue()
    }

    @Test
    fun bounds_roundsTheCentreAndExtents() {
        val bounds = ball(100.4f, 200.6f, 0f, 0f).bounds()

        assertThat(bounds).isEqualTo(Rect(80, 181, 120, 221))
    }

    @Test
    fun step_inOpenCourt_movesByVelocityTimesDelta() {
        val result = flight(step(ball(1000f, 500f, 400f, -100f), 0.5f))

        assertThat(result.ball.centerX).isWithin(0.01f).of(1200f)
        assertThat(result.ball.centerY).isWithin(0.01f).of(450f)
        assertThat(result.ball.velocityX).isEqualTo(400f)
        assertThat(result.ball.velocityY).isEqualTo(-100f)
        assertThat(result.hitPaddle).isNull()
        assertThat(result.hitWall).isFalse()
    }

    @Test
    fun step_atTheTopWall_reflectsTheOvershoot() {
        val result = flight(step(ball(1000f, 30f, 0f, -400f), 0.1f))

        assertThat(result.ball.centerY).isWithin(0.01f).of(50f)
        assertThat(result.ball.velocityY).isEqualTo(400f)
        assertThat(result.hitWall).isTrue()
    }

    @Test
    fun step_atTheBottomWall_reflectsTheOvershoot() {
        val result = flight(step(ball(1000f, 970f, 0f, 400f), 0.1f))

        assertThat(result.ball.centerY).isWithin(0.01f).of(950f)
        assertThat(result.ball.velocityY).isEqualTo(-400f)
        assertThat(result.hitWall).isTrue()
    }

    @Test
    fun step_leadingEdgeCrossesTheRightFace_bouncesInFrontOfIt() {
        // Leading edge travels 1860 → 1900 across the face at 1876, level with the paddle's middle.
        val result = flight(step(ball(1840f, 510f, 400f, 0f), 0.1f))

        assertThat(result.hitPaddle).isEqualTo(PongSide.RIGHT)
        assertThat(result.ball.velocityX).isLessThan(0f)
        assertThat(result.ball.velocityY).isWithin(0.01f).of(0f)
        assertThat(result.ball.speed).isWithin(0.01f).of(416f)
        assertThat(result.ball.centerX + result.ball.halfWidth).isAtMost(rightPaddle.left.toFloat())
        // 24 px of overshoot past the face come back out in front of it.
        assertThat(result.ball.centerX).isWithin(0.01f).of(1832f)
    }

    @Test
    fun step_leadingEdgeCrossesTheLeftFace_bouncesInFrontOfIt() {
        val result = flight(step(ball(160f, 510f, -400f, 0f), 0.1f))

        assertThat(result.hitPaddle).isEqualTo(PongSide.LEFT)
        assertThat(result.ball.velocityX).isGreaterThan(0f)
        assertThat(result.ball.centerX - result.ball.halfWidth).isAtLeast(leftPaddle.right.toFloat())
        assertThat(result.ball.centerX).isWithin(0.01f).of(168f)
    }

    @Test
    fun step_ballAlreadyBehindTheFace_isNotCaughtAndNotSnappedBack() {
        // The body overlaps the paddle column (110..150 against 100..124) but the leading edge
        // was already behind the face before this step: a late paddle catches nothing.
        val result = flight(step(ball(140f, 510f, -100f, 0f), 0.1f))

        assertThat(result.hitPaddle).isNull()
        assertThat(result.ball.velocityX).isEqualTo(-100f)
        assertThat(result.ball.centerX).isWithin(0.01f).of(130f)
    }

    @Test
    fun step_crossingAboveThePaddle_missesIt() {
        val result = flight(step(ball(1840f, 100f, 400f, 0f), 0.1f))

        assertThat(result.hitPaddle).isNull()
        assertThat(result.ball.velocityX).isEqualTo(400f)
        assertThat(result.ball.centerX).isWithin(0.01f).of(1880f)
    }

    @Test
    fun step_atMaxSpeedInOneCappedFrame_cannotTunnelThroughThePaddle() {
        // 1600 px/s over the loop's 50 ms cap is 80 px: the leading edge goes 1870 → 1950, past
        // the whole 24 px paddle. An overlap test on the end position would see nothing.
        val result = flight(step(ball(1850f, 510f, 1600f, 0f), PongLoop.MAX_FRAME_SECONDS))

        assertThat(result.hitPaddle).isEqualTo(PongSide.RIGHT)
        assertThat(result.ball.velocityX).isLessThan(0f)
        assertThat(result.ball.centerX + result.ball.halfWidth).isAtMost(rightPaddle.left.toFloat())
    }

    @Test
    fun step_hit_gainsSpeedUpToTheCap() {
        val result = flight(step(ball(1840f, 510f, 1580f, 0f), 0.02f))

        assertThat(result.hitPaddle).isEqualTo(PongSide.RIGHT)
        assertThat(result.ball.speed).isWithin(0.5f).of(rules.maxSpeedPx)
    }

    @Test
    fun step_hitOnThePaddleEdge_leavesAtTheHitAngle() {
        // Centre y 640 at the face: the ball's top edge (620) just touches the paddle's bottom.
        val result = flight(step(ball(1840f, 640f, 400f, 0f), 0.1f))

        assertThat(result.hitPaddle).isEqualTo(PongSide.RIGHT)
        assertThat(result.ball.velocityY).isGreaterThan(0f)
        val degrees = Math.toDegrees(
            atan2(result.ball.velocityY, abs(result.ball.velocityX)).toDouble())
        assertThat(degrees).isWithin(0.5).of(rules.hitAngleDegrees.toDouble())
    }

    @Test
    fun step_hitOnTheUpperHalf_sendsTheBallUp() {
        val result = flight(step(ball(1840f, 450f, 400f, 0f), 0.1f))

        assertThat(result.hitPaddle).isEqualTo(PongSide.RIGHT)
        assertThat(result.ball.velocityY).isLessThan(0f)
    }

    @Test
    fun step_wholeBodyPastTheLeftEdge_isAGoalForTheRight() {
        assertThat(step(ball(10f, 500f, -400f, 0f), 0.1f)).isEqualTo(PongStep.Goal(PongSide.RIGHT))
    }

    @Test
    fun step_wholeBodyPastTheRightEdge_isAGoalForTheLeft() {
        assertThat(step(ball(1990f, 500f, 400f, 0f), 0.1f)).isEqualTo(PongStep.Goal(PongSide.LEFT))
    }

    @Test
    fun step_partlyOverTheEdge_isStillInFlight() {
        val result = flight(step(ball(15f, 500f, -100f, 0f), 0.1f))

        assertThat(result.ball.centerX).isWithin(0.01f).of(5f)
    }
}
