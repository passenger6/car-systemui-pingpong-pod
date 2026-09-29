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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The ball in flight: centre and velocity in px (per second), half extents of the task it is.
 * Immutable; every step makes a new one. The pod only moves the task, never resizes it, so the
 * extents are whatever the ball variant says, square or not.
 */
data class PongBall(
    val centerX: Float,
    val centerY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val halfWidth: Float,
    val halfHeight: Float,
) {
    val speed: Float
        get() = hypot(velocityX, velocityY)

    fun bounds(): Rect = Rect(
        (centerX - halfWidth).roundToInt(),
        (centerY - halfHeight).roundToInt(),
        (centerX + halfWidth).roundToInt(),
        (centerY + halfHeight).roundToInt(),
    )

    fun approaches(side: PongSide): Boolean =
        if (side == PongSide.LEFT) velocityX < 0f else velocityX > 0f
}

/** The tunables a step needs; the pod reads them from resources ([PongConfig]). */
data class PongRules(val speedGain: Float, val maxSpeedPx: Float, val hitAngleDegrees: Float)

sealed interface PongStep {
    data class Flight(val ball: PongBall, val hitPaddle: PongSide?, val hitWall: Boolean) : PongStep
    data class Goal(val scorer: PongSide) : PongStep
}

private data class Velocity(val x: Float, val y: Float)

/**
 * The rules of flight, pure (rects and numbers only), so a test can run a whole rally. Walls
 * reflect. A paddle reflects at an angle set by where the ball lands on it
 * (its edge sends the ball off at [PongRules.hitAngleDegrees], its middle straight back) and
 * speeds it up by [PongRules.speedGain] up to [PongRules.maxSpeedPx]. A ball whose whole body
 * has left the court sideways is a goal for the other side.
 *
 * A paddle hit is swept: it counts only when the ball's leading edge crosses the paddle's face
 * during this step and the ball's height overlaps the paddle there. A plain overlap test would
 * let a late paddle catch a ball that already passed, and let a small, fast ball skip the face
 * between two frames.
 */
object PongPhysics {

    fun serve(
        centerX: Float,
        centerY: Float,
        halfWidth: Float,
        halfHeight: Float,
        speedPx: Float,
        toward: PongSide,
        angleDegrees: Float,
    ): PongBall {
        val angle = Math.toRadians(angleDegrees.toDouble())
        val direction = if (toward == PongSide.RIGHT) 1f else -1f
        return PongBall(
            centerX = centerX,
            centerY = centerY,
            velocityX = (cos(angle) * speedPx).toFloat() * direction,
            velocityY = (sin(angle) * speedPx).toFloat(),
            halfWidth = halfWidth,
            halfHeight = halfHeight,
        )
    }

    fun step(
        ball: PongBall,
        court: Rect,
        leftPaddle: Rect,
        rightPaddle: Rect,
        deltaSeconds: Float,
        rules: PongRules,
    ): PongStep {
        var centerX = ball.centerX + ball.velocityX * deltaSeconds
        var centerY = ball.centerY + ball.velocityY * deltaSeconds
        var velocityX = ball.velocityX
        var velocityY = ball.velocityY
        var hitWall = false

        val topLimit = court.top + ball.halfHeight
        val bottomLimit = court.bottom - ball.halfHeight
        if (centerY < topLimit) {
            centerY = 2 * topLimit - centerY
            velocityY = -velocityY
            hitWall = true
        } else if (centerY > bottomLimit) {
            centerY = 2 * bottomLimit - centerY
            velocityY = -velocityY
            hitWall = true
        }

        var hitPaddle: PongSide? = null
        if (velocityX < 0f) {
            val crossing = crossing(
                previousEdge = ball.centerX - ball.halfWidth,
                newEdge = centerX - ball.halfWidth,
                face = leftPaddle.right.toFloat(),
                previousCenterY = ball.centerY,
                newCenterY = centerY,
                ball = ball,
                paddle = leftPaddle,
            )
            if (crossing != null) {
                val bounced = bounce(crossing, leftPaddle, ball, rules, direction = 1f)
                velocityX = bounced.x
                velocityY = bounced.y
                // The overshoot past the face comes back out in front of it: the ball ends up
                // where it would be had it bounced at the moment of crossing.
                centerX = leftPaddle.right + ball.halfWidth + (leftPaddle.right - (centerX - ball.halfWidth))
                hitPaddle = PongSide.LEFT
            }
        } else if (velocityX > 0f) {
            val crossing = crossing(
                previousEdge = ball.centerX + ball.halfWidth,
                newEdge = centerX + ball.halfWidth,
                face = rightPaddle.left.toFloat(),
                previousCenterY = ball.centerY,
                newCenterY = centerY,
                ball = ball,
                paddle = rightPaddle,
            )
            if (crossing != null) {
                val bounced = bounce(crossing, rightPaddle, ball, rules, direction = -1f)
                velocityX = bounced.x
                velocityY = bounced.y
                centerX = rightPaddle.left - ball.halfWidth - ((centerX + ball.halfWidth) - rightPaddle.left)
                hitPaddle = PongSide.RIGHT
            }
        }

        if (centerX + ball.halfWidth < court.left) return PongStep.Goal(PongSide.RIGHT)
        if (centerX - ball.halfWidth > court.right) return PongStep.Goal(PongSide.LEFT)
        return PongStep.Flight(
            ball.copy(centerX = centerX, centerY = centerY, velocityX = velocityX, velocityY = velocityY),
            hitPaddle,
            hitWall,
        )
    }

    /**
     * The ball's centre y at the moment its leading edge reaches [face], or null when the edge
     * does not cross the face in this step or the ball misses the paddle's height there. The
     * direction of travel is implied by the caller: the previous edge is in front of the face,
     * the new edge at or behind it.
     */
    private fun crossing(
        previousEdge: Float,
        newEdge: Float,
        face: Float,
        previousCenterY: Float,
        newCenterY: Float,
        ball: PongBall,
        paddle: Rect,
    ): Float? {
        val travel = previousEdge - newEdge
        if (travel == 0f) return null
        val fraction = (previousEdge - face) / travel
        // The face must lie on this step's path; otherwise the ball was already behind the paddle
        // or is still ahead of it.
        if (fraction < 0f || fraction > 1f) return null
        val centerYAtFace = previousCenterY + (newCenterY - previousCenterY) * fraction
        val overlapsPaddle = centerYAtFace + ball.halfHeight >= paddle.top &&
            centerYAtFace - ball.halfHeight <= paddle.bottom
        return if (overlapsPaddle) centerYAtFace else null
    }

    /** The velocity after a paddle hit; [direction] is +1 leaving a left paddle, -1 a right one. */
    private fun bounce(
        centerYAtFace: Float,
        paddle: Rect,
        ball: PongBall,
        rules: PongRules,
        direction: Float,
    ): Velocity {
        // How far from the paddle's middle the ball landed, -1 (top edge) .. +1 (bottom edge).
        // A paddle with no height reflects flat rather than dividing by zero.
        val reach = paddle.height() / 2f + ball.halfHeight
        val offset = if (reach <= 0f) 0f else ((centerYAtFace - paddle.exactCenterY()) / reach).coerceIn(-1f, 1f)
        val angle = Math.toRadians((offset * rules.hitAngleDegrees).toDouble())
        val speed = min(ball.speed * rules.speedGain, rules.maxSpeedPx)
        return Velocity((cos(angle) * speed).toFloat() * direction, (sin(angle) * speed).toFloat())
    }
}
