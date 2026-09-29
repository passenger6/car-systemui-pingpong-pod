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
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * A paddle: a rounded bar filling its panel, in Jev's colour when Jev plays it. Every touch
 * goes to [listener]; the controller passes it on to the game.
 */
class PongPaddleView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defaultStyleAttribute: Int = 0,
) : View(context, attributeSet, defaultStyleAttribute) {

    var listener: ((MotionEvent) -> Unit)? = null

    var playedByJev: Boolean = false
        set(value) {
            field = value
            paint.color = context.getColor(
                if (value) R.color.pong_paddle_jev else R.color.pong_paddle_touch)
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.pong_paddle_touch)
    }
    private val corner = context.resources.getDimension(R.dimen.pong_paddle_corner)
    private val shape = RectF()

    override fun onDraw(canvas: Canvas) {
        shape.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(shape, corner, corner, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        listener?.invoke(event)
        return true
    }
}
