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

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * The field: a felt background, a dashed net, the two scores with their labels, and one status
 * line along the bottom. A touch on the court's empty area steers the paddle of that half; this
 * is how a paddle that is an app gets moved, since its own touches belong to the app.
 */
class PongCourtView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defaultStyleAttribute: Int = 0,
) : View(context, attributeSet, defaultStyleAttribute) {

    var listener: ((MotionEvent) -> Unit)? = null

    private val backgroundColor = context.getColor(R.color.pong_court_background)
    private val courtPadding = context.resources.getDimension(R.dimen.pong_court_padding)

    private val netPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.pong_net)
        style = Paint.Style.STROKE
        strokeWidth = context.resources.getDimension(R.dimen.pong_net_width)
        val dash = context.resources.getDimension(R.dimen.pong_net_dash)
        pathEffect = DashPathEffect(floatArrayOf(dash, dash), 0f)
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.pong_score)
        textSize = context.resources.getDimension(R.dimen.pong_score_text_size)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.pong_label)
        textSize = context.resources.getDimension(R.dimen.pong_label_text_size)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.2f
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.pong_status)
        textSize = context.resources.getDimension(R.dimen.pong_status_text_size)
        textAlign = Paint.Align.CENTER
    }

    private var leftScore = 0
    private var rightScore = 0
    private var leftLabel = ""
    private var rightLabel = ""
    private var statusText = ""

    fun setScore(left: Int, right: Int) {
        leftScore = left
        rightScore = right
        invalidate()
    }

    fun setLabels(left: String, right: String) {
        leftLabel = left
        rightLabel = right
        invalidate()
    }

    fun setStatus(text: String) {
        statusText = text
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        listener?.invoke(event)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(backgroundColor)
        val centerX = width / 2f
        canvas.drawLine(centerX, courtPadding, centerX, height - courtPadding, netPaint)

        val leftColumn = width / 4f
        val rightColumn = width * 3 / 4f
        val scoreBaseline = courtPadding + scorePaint.textSize
        canvas.drawText(leftScore.toString(), leftColumn, scoreBaseline, scorePaint)
        canvas.drawText(rightScore.toString(), rightColumn, scoreBaseline, scorePaint)

        val labelBaseline = scoreBaseline + labelPaint.textSize * 1.5f
        canvas.drawText(leftLabel, leftColumn, labelBaseline, labelPaint)
        canvas.drawText(rightLabel, rightColumn, labelBaseline, labelPaint)

        canvas.drawText(statusText, centerX, height - courtPadding, statusPaint)
    }
}
