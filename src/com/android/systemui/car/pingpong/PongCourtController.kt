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
 * The court: a DecorPanel whose view is the field (net, score, status). Its controller is the
 * game's only display; it registers the court with [PongGame] when its view exists and hands
 * every update to the view on the view's own thread.
 *
 * Registration follows the view, as in the Win98 pod: `DecorPanel.reset()` asks for the view
 * whenever it (re)creates the AutoDecor, and `destroy()` / `refreshTheme()` / a detached view
 * end this controller's turn.
 */
class PongCourtController @AssistedInject constructor(
    @Assisted panelId: String,
    @Assisted metadata: PanelControllerMetadata,
    @DecorPanelViewMap decorPanelViewMap: Map<Class<*>, @JvmSuppressWildcards Provider<View>>,
    private val game: PongGame,
) : DecorPanelControllerBase(panelId, metadata, decorPanelViewMap), PongGame.Listener {

    @AssistedFactory
    interface Factory : DecorPanelController.Factory<PongCourtController> {
        override fun create(panelId: String, metadata: PanelControllerMetadata): PongCourtController
    }

    private var courtView: PongCourtView? = null

    private val detachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) = release()
    }

    override fun getView(): View? {
        val view = super.getView()
        if (view is PongCourtView && view !== courtView) {
            courtView?.let {
                it.listener = null
                it.removeOnAttachStateChangeListener(detachListener)
            }
            courtView = view
            // Reduced on the view's thread: the MotionEvent is recycled after the callback.
            view.listener = { event -> PongCourtTouch.from(event)?.let { game.courtTouch(it) } }
            view.addOnAttachStateChangeListener(detachListener)
            game.attachCourt(mPanelId, this, token = this)
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

    override fun onScore(left: Int, right: Int) = onView { it.setScore(left, right) }

    override fun onStatus(text: String) = onView { it.setStatus(text) }

    override fun onLabels(left: String, right: String) = onView { it.setLabels(left, right) }

    /** The listener runs on the shell thread; the view draws on its host's thread. */
    private fun onView(update: (PongCourtView) -> Unit) {
        val view = courtView ?: return
        view.post { update(view) }
    }

    private fun release() {
        game.detachCourt(mPanelId, token = this)
        courtView?.let {
            it.listener = null
            it.removeOnAttachStateChangeListener(detachListener)
        }
        courtView = null
    }
}
