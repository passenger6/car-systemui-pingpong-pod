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
import org.json.JSONException
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PongJevQuestionTest {
    private val court = Rect(0, 0, 2000, 1000)
    private val ball = PongBall(1000f, 500f, 400f, -100f, halfWidth = 20f, halfHeight = 20f)
    private val leftPaddle = Rect(100, 400, 124, 620)
    private val rightPaddle = Rect(1876, 400, 1900, 620)
    private val lanes = 4

    private fun answer(lane: String, confidence: Any?): String {
        val fields = ArrayList<String>()
        fields.add("\"choice\":\"$lane\"")
        if (confidence != null) fields.add("\"confidence\":$confidence")
        return "{\"answers\":{\"landing_lane\":{${fields.joinToString(",")}}}}"
    }

    @Test
    fun request_hasTheSystemOneShape() {
        val request = PongJevQuestion.request(
            "jev-latest", court, ball, rightPaddle, PongSide.RIGHT, leftPaddle, lanes)

        assertThat(request.getString("model")).isEqualTo("jev-latest")
        val state = request.getJSONObject("state")
        assertThat(state.getJSONObject("court").getInt("right")).isEqualTo(2000)
        val ballJson = state.getJSONObject("ball")
        assertThat(ballJson.getInt("x")).isEqualTo(1000)
        assertThat(ballJson.getInt("vy")).isEqualTo(-100)
        assertThat(ballJson.getInt("width")).isEqualTo(40)
        val myPaddle = state.getJSONObject("my_paddle")
        assertThat(myPaddle.getString("side")).isEqualTo("right")
        assertThat(myPaddle.getInt("goal_line_x")).isEqualTo(rightPaddle.left)
        assertThat(myPaddle.getInt("center_y")).isEqualTo(510)
        assertThat(myPaddle.getInt("height")).isEqualTo(220)
        assertThat(state.getJSONObject("opponent_paddle").getString("side")).isEqualTo("left")
        assertThat(state.getString("rules")).contains("y grows downward")

        val question = request.getJSONObject("questions").getJSONObject(PongJevQuestion.QUESTION_ID)
        assertThat(question.getString("type")).isEqualTo("choice")
        assertThat(question.getString("instructions")).contains("x = ${rightPaddle.left}")
        val criteria = question.getJSONObject("criteria")
        assertThat(criteria.length()).isEqualTo(lanes)
        assertThat(criteria.getString("lane_0")).isEqualTo("centre y from 0 to 250 px")
        assertThat(criteria.getString("lane_3")).isEqualTo("centre y from 750 to 1000 px")
    }

    @Test
    fun request_forTheLeftSide_usesThePaddlesInnerFaceAsTheGoalLine() {
        val request = PongJevQuestion.request(
            "jev-latest", court, ball, leftPaddle, PongSide.LEFT, rightPaddle, lanes)

        val myPaddle = request.getJSONObject("state").getJSONObject("my_paddle")
        assertThat(myPaddle.getString("side")).isEqualTo("left")
        assertThat(myPaddle.getInt("goal_line_x")).isEqualTo(leftPaddle.right)
    }

    @Test
    fun laneCenterY_isTheMiddleOfTheBand() {
        assertThat(PongJevQuestion.laneCenterY(court, lanes, 0)).isWithin(0.01f).of(125f)
        assertThat(PongJevQuestion.laneCenterY(court, lanes, 3)).isWithin(0.01f).of(875f)
    }

    @Test
    fun parse_readsLaneAndConfidence() {
        val parsed = PongJevQuestion.parse(answer("lane_2", 0.82), lanes)

        assertThat(parsed).isEqualTo(PongJevAnswer(lane = 2, confidence = 0.82))
    }

    @Test
    fun parse_acceptsTheEdgesOfTheConfidenceRange() {
        assertThat(PongJevQuestion.parse(answer("lane_0", 0), lanes).confidence).isEqualTo(0.0)
        assertThat(PongJevQuestion.parse(answer("lane_3", 1), lanes).confidence).isEqualTo(1.0)
    }

    @Test
    fun parse_withoutTheAnswersObject_throws() {
        assertThrows(JSONException::class.java) { PongJevQuestion.parse("{}", lanes) }
    }

    @Test
    fun parse_withoutAChoice_throws() {
        assertThrows(JSONException::class.java) {
            PongJevQuestion.parse("{\"answers\":{\"landing_lane\":{\"confidence\":0.5}}}", lanes)
        }
    }

    @Test
    fun parse_withoutAConfidence_throws() {
        assertThrows(JSONException::class.java) {
            PongJevQuestion.parse(answer("lane_1", confidence = null), lanes)
        }
    }

    @Test
    fun parse_laneOutOfRange_throws() {
        assertThrows(JSONException::class.java) { PongJevQuestion.parse(answer("lane_4", 0.5), lanes) }
        assertThrows(JSONException::class.java) { PongJevQuestion.parse(answer("lane_-1", 0.5), lanes) }
        assertThrows(JSONException::class.java) { PongJevQuestion.parse(answer("banana", 0.5), lanes) }
    }

    @Test
    fun parse_confidenceOutOfRange_throws() {
        assertThrows(JSONException::class.java) { PongJevQuestion.parse(answer("lane_1", 1.5), lanes) }
        assertThrows(JSONException::class.java) { PongJevQuestion.parse(answer("lane_1", -0.1), lanes) }
    }

    @Test
    fun parse_ofSomethingThatIsNotJson_throws() {
        assertThrows(JSONException::class.java) { PongJevQuestion.parse("<html>", lanes) }
    }
}
