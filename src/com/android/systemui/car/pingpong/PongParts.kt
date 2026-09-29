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

import com.android.car.scalableui.panel.PanelPool
import com.android.systemui.car.wm.scalableui.panel.DecorPanel
import com.android.systemui.car.wm.scalableui.panel.SysUIPanel
import com.android.systemui.car.wm.scalableui.panel.TaskPanel

/**
 * The four panels of a match, looked up in the pool on every frame rather than cached: a
 * controller registers before its panel is necessarily in the pool, and a panel can leave
 * (theme refresh, overlay change) while the match runs. A paddle is a DecorPanel (a drawn bar)
 * or a TaskPanel (an app). Left and right are read off their position on the court.
 */
class PongParts(val courtId: String, val ballId: String, private val paddleIds: List<String>) {

    class Resolved(
        val court: DecorPanel,
        val ball: TaskPanel,
        val leftPaddle: SysUIPanel,
        val rightPaddle: SysUIPanel,
    )

    sealed interface Lookup {
        data class Found(val parts: Resolved) : Lookup
        data class Missing(val panelId: String) : Lookup
        object SameSide : Lookup
    }

    fun resolve(): Lookup {
        val pool = PanelPool.getInstance()
        val court = pool.getPanel(courtId) as? DecorPanel ?: return Lookup.Missing(courtId)
        val ball = pool.getPanel(ballId) as? TaskPanel ?: return Lookup.Missing(ballId)
        val paddles = ArrayList<SysUIPanel>(paddleIds.size)
        for (paddleId in paddleIds) {
            paddles.add(pool.getPanel(paddleId) as? SysUIPanel ?: return Lookup.Missing(paddleId))
        }
        val courtBounds = court.bounds
        val leftPaddle = paddles.firstOrNull { PongSide.of(it.bounds, courtBounds) == PongSide.LEFT }
            ?: return Lookup.SameSide
        val rightPaddle = paddles.firstOrNull { PongSide.of(it.bounds, courtBounds) == PongSide.RIGHT }
            ?: return Lookup.SameSide
        return Lookup.Found(Resolved(court, ball, leftPaddle, rightPaddle))
    }
}
