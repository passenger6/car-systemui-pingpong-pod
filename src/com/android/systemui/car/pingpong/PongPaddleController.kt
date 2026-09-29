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

import android.view.View
import com.android.car.scalableui.model.PanelControllerMetadata
import com.android.car.scalableui.panel.DecorPanelController
import com.android.systemui.car.wm.scalableui.panel.controller.DecorPanelViewMap
import com.android.systemui.car.wm.scalableui.view.DecorPanelControllerBase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import javax.inject.Provider

/**
 * A paddle's controller: registers the paddle with [PongGame] while its view exists and
 * forwards the view's touches there. The game reads the paddle's side off its position on the
 * court; this controller only tells the view whether Jev plays it (`pong_jev_paddle`), so the
 * view can take Jev's colour.
 */
class PongPaddleController @AssistedInject constructor(
    @Assisted panelId: String,
    @Assisted metadata: PanelControllerMetadata,
    @DecorPanelViewMap decorPanelViewMap: Map<Class<*>, @JvmSuppressWildcards Provider<View>>,
    private val game: PongGame,
) : DecorPanelControllerBase(panelId, metadata, decorPanelViewMap) {

    @AssistedFactory
    interface Factory : DecorPanelController.Factory<PongPaddleController> {
        override fun create(panelId: String, metadata: PanelControllerMetadata): PongPaddleController
    }

    private var paddleView: PongPaddleView? = null

    private val detachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) = release()
    }

    override fun getView(): View? {
        val view = super.getView()
        if (view is PongPaddleView && view !== paddleView) {
            paddleView?.let {
                it.listener = null
                it.removeOnAttachStateChangeListener(detachListener)
            }
            paddleView = view
            view.playedByJev = game.isJevPaddle(mPanelId)
            view.listener = { event -> game.paddleTouch(mPanelId, event.actionMasked, event.rawY) }
            view.addOnAttachStateChangeListener(detachListener)
            game.attachPaddle(mPanelId, token = this)
        }
        return view
    }

    override fun refreshTheme() {
        release()
        super.refreshTheme()
    }

    override fun destroy() {
        release()
        super.destroy()
    }

    private fun release() {
        game.detachPaddle(mPanelId, token = this)
        paddleView?.let {
            it.listener = null
            it.removeOnAttachStateChangeListener(detachListener)
        }
        paddleView = null
    }
}
