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
import android.util.Log
import com.android.systemui.car.wm.scalableui.EventDispatcher
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.shared.annotations.ShellMainThread
import javax.inject.Inject

/**
 * The registry the four controllers report to, and the owner of the match and the frame loop.
 *
 * Controllers are created from XML in any order and on other threads; every entry point hops
 * to the shell main thread, where all state lives. A match exists exactly while a court, a
 * ball and two paddles are registered; any change to that set ends the match and, if the set
 * is complete again, starts a new one. The score outlives matches ([PongScore]).
 *
 * A registration belongs to a controller instance, not to a panel id. `DecorPanel.reset()`
 * builds a new controller without destroying the previous one, and it runs more than once
 * (from `init()`, `refreshTheme()` and `PanelTransitionCoordinator.resetUnpreparedDecorPanel()`).
 * The old view can detach after the new controller has registered, so every detach carries the
 * [token] (the controller) its attach used and removes only a registration that is still its
 * own.
 */
@WMSingleton
class PongGame @Inject constructor(
    context: Context,
    @ShellMainThread private val shellMainExecutor: ShellExecutor,
    private val surfaces: PongSurfaces,
    private val eventDispatcher: EventDispatcher,
    private val jevClient: PongJevClient,
) {
    /** What the court shows. Called on the shell main thread. */
    interface Listener {
        fun onScore(left: Int, right: Int)
        fun onStatus(text: String)
        fun onLabels(left: String, right: String)
    }

    private class Registration(val panelId: String, val token: Any)

    private val config = PongConfig.fromResources(context)
    private val status = PongStatus(context)
    private val score = PongScore()
    private val loop = PongLoop()
    private var court: Registration? = null
    private var courtListener: Listener? = null
    private var ball: Registration? = null
    /** Paddle panel id → the controller that registered it, in registration order. */
    private val paddles = LinkedHashMap<String, Any>()
    private var match: PongMatch? = null

    fun isJevPaddle(panelId: String): Boolean = config.isJevPaddle(panelId)

    fun attachCourt(panelId: String, listener: Listener, token: Any) = onShellThread {
        Log.d(TAG, "attach court $panelId by ${name(token)}")
        court = Registration(panelId, token)
        courtListener = listener
        rebuild()
    }

    fun detachCourt(panelId: String, token: Any) = onShellThread {
        if (!owns(court, panelId, token)) return@onShellThread
        Log.d(TAG, "detach court $panelId by ${name(token)}")
        court = null
        courtListener = null
        rebuild()
    }

    fun attachPaddle(panelId: String, token: Any) = onShellThread {
        Log.d(TAG, "attach paddle $panelId by ${name(token)}")
        paddles[panelId] = token
        rebuild()
    }

    fun detachPaddle(panelId: String, token: Any) = onShellThread {
        if (paddles[panelId] !== token) return@onShellThread
        Log.d(TAG, "detach paddle $panelId by ${name(token)}")
        paddles.remove(panelId)
        rebuild()
    }

    fun attachBall(panelId: String, token: Any) = onShellThread {
        Log.d(TAG, "attach ball $panelId by ${name(token)}")
        ball = Registration(panelId, token)
        rebuild()
    }

    fun detachBall(panelId: String, token: Any) = onShellThread {
        if (!owns(ball, panelId, token)) return@onShellThread
        Log.d(TAG, "detach ball $panelId by ${name(token)}")
        ball = null
        rebuild()
    }

    fun paddleTouch(panelId: String, action: Int, rawY: Float) = onShellThread {
        match?.touch(panelId, action, rawY)
    }

    fun courtTouch(touch: PongCourtTouch) = onShellThread {
        match?.courtTouch(touch)
    }

    private fun rebuild() {
        match?.stop()
        match = null
        loop.stop()

        val listener = courtListener
        val courtId = court?.panelId
        val ballId = ball?.panelId
        val missing = ArrayList<String>()
        if (courtId == null) missing.add(PART_COURT)
        if (ballId == null) missing.add(PART_BALL)
        if (paddles.size != 2) missing.add("$PART_PADDLES (${paddles.size} of 2)")
        if (courtId == null || ballId == null || listener == null || missing.isNotEmpty()) {
            Log.d(TAG, "no match: waiting for ${missing.joinToString()}")
            listener?.onStatus(status.waitingParts(missing))
            return
        }

        Log.d(TAG, "match: court=$courtId ball=$ballId paddles=${paddles.keys}")
        val started = PongMatch(
            config, PongParts(courtId, ballId, paddles.keys.toList()), score, surfaces,
            eventDispatcher, shellMainExecutor, status, jevClient, listener)
        match = started
        started.start()
        loop.start { deltaSeconds -> started.frame(deltaSeconds) }
    }

    private fun owns(registration: Registration?, panelId: String, token: Any): Boolean =
        registration != null && registration.panelId == panelId && registration.token === token

    private fun name(token: Any): String =
        "${token.javaClass.simpleName}@${Integer.toHexString(System.identityHashCode(token))}"

    private fun onShellThread(block: () -> Unit) {
        shellMainExecutor.execute(block)
    }

    companion object {
        const val TAG = "PongGame"
        private const val PART_COURT = "court"
        private const val PART_BALL = "ball"
        private const val PART_PADDLES = "paddles"
    }
}
