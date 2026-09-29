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
import com.android.car.scalableui.model.PanelControllerMetadata
import com.android.car.scalableui.panel.TaskPanelController
import com.android.systemui.car.wm.scalableui.panel.PanelUtils
import com.android.systemui.car.wm.scalableui.panel.controller.BaseTaskPanelController
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/**
 * The ball is a TaskPanel with a real app inside (`<DefaultComponent>` in its controller
 * XML). This controller adds one thing to [BaseTaskPanelController]: it tells [PongGame]
 * which panel the ball is. Launching, persistence and the rest stay the base's.
 */
class PongBallController @AssistedInject constructor(
    context: Context,
    @Assisted panelId: String,
    @Assisted metadata: PanelControllerMetadata,
    panelUtils: PanelUtils,
    private val game: PongGame,
) : BaseTaskPanelController(context, panelId, metadata, panelUtils) {

    @AssistedFactory
    interface Factory : TaskPanelController.Factory<PongBallController> {
        override fun create(panelId: String, metadata: PanelControllerMetadata): PongBallController
    }

    private val ballPanelId = panelId

    override fun init() {
        super.init()
        game.attachBall(ballPanelId, token = this)
    }

    override fun destroy() {
        game.detachBall(ballPanelId, token = this)
        super.destroy()
    }
}
