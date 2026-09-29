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
import com.android.car.scalableui.manager.StateManager
import com.android.car.scalableui.model.Event
import com.android.car.scalableui.model.PongVariantGeometry
import com.android.systemui.car.wm.scalableui.panel.DecorPanel
import com.android.systemui.car.wm.scalableui.panel.PanelUtils
import com.android.systemui.car.wm.scalableui.panel.SysUIPanel
import com.android.systemui.car.wm.scalableui.panel.TaskPanel
import com.android.wm.shell.automotive.AutoSurfaceTransaction
import com.android.wm.shell.automotive.AutoSurfaceTransactionFactory
import javax.inject.Inject

/**
 * Moves a panel per frame: same size, new origin. These writes always happen together:
 *
 *  1. the panel's current variant (the stamp; [PongVariantGeometry] says why),
 *  2. the surface, through one [AutoSurfaceTransaction] the caller applies once per frame,
 *  3. for a decor only, the panel's own bounds (the model the decor's view host relayouts to).
 *
 * A task panel's own bounds are not written per frame.
 * `PanelTransitionCoordinator.hasConflictingState` compares them with WindowManager's task
 * bounds, which stay put because only the surface moves. If they differed, the coordinator would
 * fire `_System_TaskOpenEvent` for the ball on every frame, and every XML rule on that event
 * with it. The panel's bounds follow at rest, when a transition applies the variant.
 *
 * No WindowContainerTransaction is used: a task never changes size, so its app is never asked to
 * relayout. A decor's move relayouts its view host at the new position (`setBounds(AutoDecor)`),
 * still without WindowManager. Shell main thread only.
 */
class PongSurfaces @Inject constructor(
    private val transactionFactory: AutoSurfaceTransactionFactory,
    private val panelUtils: PanelUtils,
) {
    fun begin(label: String): AutoSurfaceTransaction = transactionFactory.createTransaction(label)

    /**
     * Moves a task panel: its surface and its current variant, never its own bounds (see above).
     * False when the task has nothing on screen to move ([hasSurface]).
     */
    fun moveTask(transaction: AutoSurfaceTransaction, panel: TaskPanel, bounds: Rect): Boolean {
        if (!hasSurface(panel)) return false
        stampCurrent(panel.panelId, bounds)
        transaction.setTaskSurfacePosition(
            panel.rootTaskId, bounds.left.toFloat(), bounds.top.toFloat())
        return true
    }

    /** Moves a decor panel. False when its AutoDecor is not created yet. */
    fun moveDecor(transaction: AutoSurfaceTransaction, panel: DecorPanel, bounds: Rect): Boolean {
        val decor = panel.autoDecor ?: return false
        panel.setBounds(Rect(bounds))
        stampCurrent(panel.panelId, bounds)
        transaction.setBounds(decor, Rect(bounds))
        return true
    }

    /** Moves either kind of panel; a paddle may be a drawn bar or an app. */
    fun movePanel(transaction: AutoSurfaceTransaction, panel: SysUIPanel, bounds: Rect): Boolean =
        when (panel) {
            is TaskPanel -> moveTask(transaction, panel, bounds)
            is DecorPanel -> moveDecor(transaction, panel, bounds)
            else -> false
        }

    /**
     * Whether the panel has something on screen to move: a task's leash and its root task
     * stack (the stack can go while the leash reference stays, and a move addressed to root
     * task -1 has no surface behind it), a decor's AutoDecor.
     */
    fun hasSurface(panel: SysUIPanel): Boolean = when (panel) {
        is TaskPanel -> panel.leash != null && panel.rootTaskId >= 0
        is DecorPanel -> panel.autoDecor != null
        else -> false
    }

    /** The bounds a panel's current variant says, or null when the panel has no state. */
    fun currentBounds(panelId: String): Rect? = panelUtils.getCurrentVariant(panelId)?.bounds

    /** The name of the variant a panel is in, or null when it has no state. */
    fun currentVariantName(panelId: String): String? =
        panelUtils.getCurrentVariant(panelId)?.idName

    /** Whether a transition is still animating the panel; the framework owns its surface then. */
    fun isAnimating(panelId: String): Boolean =
        StateManager.getPanelState(panelId)?.isAnimating == true

    /** Whether the panel's XML has a transition on [eventId] from the variant it is in now. */
    fun hasTransitionFor(panelId: String, eventId: String): Boolean {
        val state = StateManager.getPanelState(panelId) ?: return false
        return state.getTransition(Event.Builder(eventId).setPanelId(panelId).build()) != null
    }

    /**
     * Rewrites a named variant's bounds, whether or not the panel is in it. Used right before a
     * serve: the flight variant still holds the bounds of the last frame of the previous rally,
     * and the serve transition would otherwise animate the ball back out there.
     */
    fun assignVariantBounds(panelId: String, variantName: String, bounds: Rect): Boolean {
        val variant = StateManager.getPanelState(panelId)?.getVariant(variantName) ?: return false
        PongVariantGeometry.assignBounds(variant, bounds)
        return true
    }

    private fun stampCurrent(panelId: String, bounds: Rect) {
        val variant = panelUtils.getCurrentVariant(panelId) ?: return
        PongVariantGeometry.assignBounds(variant, bounds)
    }
}
