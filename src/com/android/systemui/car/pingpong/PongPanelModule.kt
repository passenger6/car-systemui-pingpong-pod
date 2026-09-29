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
import android.view.View
import com.android.car.scalableui.panel.DecorPanelController
import com.android.systemui.car.wm.scalableui.panel.controller.DecorPanelViewMap
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

/**
 * Dagger module for the ping-pong pod: the court and decor-paddle controller factories, their
 * views, and (through [PongTaskPanelModule], Java for the raw map type) the ball's and the
 * app-paddle's. Include it in `PanelControllerModule` (see README).
 */
@Module(includes = [PongTaskPanelModule::class])
abstract class PongPanelModule {

    @Binds
    @IntoMap
    @ClassKey(PongCourtController::class)
    abstract fun bindPongCourtControllerFactory(
        factory: PongCourtController.Factory
    ): DecorPanelController.Factory<*>

    @Binds
    @IntoMap
    @ClassKey(PongPaddleController::class)
    abstract fun bindPongPaddleControllerFactory(
        factory: PongPaddleController.Factory
    ): DecorPanelController.Factory<*>

    companion object {
        @Provides
        @IntoMap
        @ClassKey(PongCourtView::class)
        @DecorPanelViewMap
        fun providePongCourtView(context: Context): View = PongCourtView(context)

        @Provides
        @IntoMap
        @ClassKey(PongPaddleView::class)
        @DecorPanelViewMap
        fun providePongPaddleView(context: Context): View = PongPaddleView(context)
    }
}
