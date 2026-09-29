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
 * A paddle that is a TaskPanel with a real app inside (`<DefaultComponent>` in its controller
 * XML): the dialer on the left, the radio on the right, both usable while they play. Like the
 * ball, it is moved surface-only, same size, so the app never relayouts. Its touches belong to
 * the app; the paddle is steered from the court's empty half instead (`PongCourtView`).
 */
class PongPaddleTaskController @AssistedInject constructor(
    context: Context,
    @Assisted panelId: String,
    @Assisted metadata: PanelControllerMetadata,
    panelUtils: PanelUtils,
    private val game: PongGame,
) : BaseTaskPanelController(context, panelId, metadata, panelUtils) {

    @AssistedFactory
    interface Factory : TaskPanelController.Factory<PongPaddleTaskController> {
        override fun create(panelId: String, metadata: PanelControllerMetadata): PongPaddleTaskController
    }

    private val paddlePanelId = panelId

    override fun init() {
        super.init()
        game.attachPaddle(paddlePanelId, token = this)
    }

    override fun destroy() {
        game.detachPaddle(paddlePanelId, token = this)
        super.destroy()
    }
}
