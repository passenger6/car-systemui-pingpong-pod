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
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.roundToInt

/** What Jev answered: the band it expects the ball in, and how sure it is (0..1). */
data class PongJevAnswer(val lane: Int, val confidence: Double)

/**
 * The question the pod asks Jev, in the System One request shape
 * (https://docs.typesafe.ai/primitives/choice): a JSON `state` (court, ball, both paddles) and
 * a `choice` question whose options are horizontal bands of the court. Jev returns the band it
 * expects the ball to cross the paddle's line in; the paddle drives there.
 *
 * Pure JSON building and parsing. [parse] throws on any other shape, so an API change shows up
 * as a failure on the court instead of a wrong lane.
 */
object PongJevQuestion {
    const val QUESTION_ID = "landing_lane"
    private const val LANE_PREFIX = "lane_"

    fun request(
        model: String,
        court: Rect,
        ball: PongBall,
        myPaddle: Rect,
        mySide: PongSide,
        opponentPaddle: Rect,
        lanes: Int,
    ): JSONObject {
        val goalLineX = if (mySide == PongSide.LEFT) myPaddle.right else myPaddle.left
        val state = JSONObject()
            .put("court", rectJson(court))
            .put("ball", JSONObject()
                .put("x", ball.centerX.roundToInt())
                .put("y", ball.centerY.roundToInt())
                .put("vx", ball.velocityX.roundToInt())
                .put("vy", ball.velocityY.roundToInt())
                .put("width", (ball.halfWidth * 2).roundToInt())
                .put("height", (ball.halfHeight * 2).roundToInt()))
            .put("my_paddle", JSONObject()
                .put("side", mySide.eventSuffix)
                .put("goal_line_x", goalLineX)
                .put("center_y", myPaddle.exactCenterY().roundToInt())
                .put("height", myPaddle.height()))
            .put("opponent_paddle", JSONObject()
                .put("side", mySide.opposite.eventSuffix)
                .put("center_y", opponentPaddle.exactCenterY().roundToInt())
                .put("height", opponentPaddle.height()))
            .put("rules", "Pixels; y grows downward. The ball moves in a straight line at " +
                "(vx, vy) px per second and reflects off the top and bottom edges of the court.")

        val criteria = JSONObject()
        for (lane in 0 until lanes) {
            criteria.put(laneKey(lane),
                "centre y from ${laneTop(court, lanes, lane)} to ${laneTop(court, lanes, lane + 1)} px")
        }
        val question = JSONObject()
            .put("type", "choice")
            .put("instructions", "Where will the centre of the ball cross my goal line " +
                "(x = $goalLineX) next? Pick the band of y it crosses in.")
            .put("criteria", criteria)

        return JSONObject()
            .put("state", state)
            .put("model", model)
            .put("questions", JSONObject().put(QUESTION_ID, question))
    }

    fun laneCenterY(court: Rect, lanes: Int, lane: Int): Float =
        court.top + court.height() * (lane + 0.5f) / lanes

    @Throws(JSONException::class)
    fun parse(body: String, lanes: Int): PongJevAnswer {
        val answer = JSONObject(body).getJSONObject("answers").getJSONObject(QUESTION_ID)
        val choice = answer.getString("choice")
        val lane = if (choice.startsWith(LANE_PREFIX)) choice.removePrefix(LANE_PREFIX).toIntOrNull() else null
        if (lane == null || lane !in 0 until lanes) throw JSONException("choice out of range: $choice")
        val confidence = answer.getDouble("confidence")
        // Shown on the court as a probability; anything outside 0..1 is not one.
        if (confidence.isNaN() || confidence < 0.0 || confidence > 1.0) {
            throw JSONException("confidence out of range: $confidence")
        }
        return PongJevAnswer(lane, confidence)
    }

    private fun laneKey(lane: Int): String = LANE_PREFIX + lane

    private fun laneTop(court: Rect, lanes: Int, lane: Int): Int =
        (court.top + court.height() * lane / lanes.toFloat()).roundToInt()

    private fun rectJson(rect: Rect): JSONObject = JSONObject()
        .put("left", rect.left)
        .put("top", rect.top)
        .put("right", rect.right)
        .put("bottom", rect.bottom)
}
