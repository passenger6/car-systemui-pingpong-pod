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
import android.util.Log
import android.view.MotionEvent
import com.android.car.scalableui.model.Event
import com.android.systemui.car.wm.scalableui.EventDispatcher
import com.android.systemui.car.wm.scalableui.panel.SysUIPanel
import com.android.wm.shell.common.ShellExecutor

/**
 * One match: the rules that turn frames into a rally, points and serves. Shell main thread only.
 *
 * The XML owns the ball's states; the pod only fires events and reads the variant back:
 *
 *  - the ball flies only while its current variant is [FLIGHT_VARIANT]. Whether this class
 *    put it there with [EVENT_SERVE] or another event did, the next frame starts the flight
 *    from the variant's bounds;
 *  - a goal fires `_Pong_Point_<scorer>`; the XML takes the ball out of [FLIGHT_VARIANT] (and,
 *    with a duration on that transition, the framework glides it back to rest);
 *  - a game's last point fires `_Pong_Win_<winner>` once the ball has come to rest. Fired
 *    together with the point, the win's animation would start from the goal line;
 *  - a serve is due whenever the ball rests and none is scheduled: after a point, at the
 *    start, and after any outside transition that ended a rally. It is fired only when the
 *    ball's XML has a `_Pong_Serve` transition from its current variant; otherwise the court
 *    says so and the serve is retried later.
 *
 * Before each serve the flight variant is re-stamped with the ball's resting bounds: it still
 * holds the last frame of the previous rally, and the serve transition would otherwise carry
 * the ball back out there.
 */
class PongMatch(
    private val config: PongConfig,
    private val parts: PongParts,
    private val score: PongScore,
    private val surfaces: PongSurfaces,
    private val eventDispatcher: EventDispatcher,
    private val shellMainExecutor: ShellExecutor,
    private val status: PongStatus,
    jevGateway: PongJevGateway,
    private val listener: PongGame.Listener,
) {
    private val brain = PongJevBrain(config, jevGateway)
    private val drives = HashMap<String, PongPaddleDrive>()
    /** Court pointer id → the paddle it took on DOWN; MOVE and UP follow the binding. */
    private val courtPointers = HashMap<Int, String>()
    private var flight: PongBall? = null
    private var serveToward: PongSide? = null
    private var serveCount = 0
    private var serveScheduled = false
    private var jevSide: PongSide? = null
    private var labelsShown = false
    private var lastStatus: String? = null
    private val serveRunnable = Runnable { serve() }

    fun start() {
        listener.onScore(score.left, score.right)
    }

    fun stop() {
        shellMainExecutor.removeCallbacks(serveRunnable)
        serveScheduled = false
        flight = null
        courtPointers.clear()
    }

    fun frame(deltaSeconds: Float) {
        val resolved = when (val lookup = parts.resolve()) {
            is PongParts.Lookup.Missing -> {
                report(status.panelMissing(lookup.panelId))
                return
            }
            PongParts.Lookup.SameSide -> {
                report(status.sameSide())
                return
            }
            is PongParts.Lookup.Found -> lookup.parts
        }
        showLabels(resolved)
        val court = courtBounds(resolved)
        // A court whose state was never applied has an empty rect.
        if (court.isEmpty) {
            flight = null
            report(status.courtNoBounds())
            return
        }
        // Drives exist from the first resolved frame, so a finger can move a paddle between
        // points and before the first serve.
        val leftDrive = drive(resolved.leftPaddle)
        val rightDrive = drive(resolved.rightPaddle)
        syncWithVariant(resolved.leftPaddle, leftDrive)
        syncWithVariant(resolved.rightPaddle, rightDrive)
        // A paddle that is an app has nothing on screen until the app is up.
        for (paddle in listOf(resolved.leftPaddle, resolved.rightPaddle)) {
            if (!surfaces.hasSurface(paddle)) {
                flight = null
                report(status.waitingPaddleApp(paddle.panelId))
                return
            }
        }
        val ballPanel = resolved.ball
        if (!surfaces.hasSurface(ballPanel)) {
            flight = null
            report(status.waitingBallApp())
            return
        }
        if (!ballPanel.isVisible) {
            flight = null
            report(status.ballHidden())
            return
        }
        if (surfaces.currentVariantName(parts.ballId) != FLIGHT_VARIANT) {
            flight = null
            announceWinOnceAtRest()
            if (!serveScheduled) {
                report(status.serving())
                scheduleServe()
            }
            return
        }

        val ball = flight ?: launch(resolved).also { flight = it }
        val step = PongPhysics.step(
            ball, court, leftDrive.bounds, rightDrive.bounds, deltaSeconds, config.rules)
        when (step) {
            is PongStep.Goal -> {
                flight = null
                point(step.scorer)
            }
            is PongStep.Flight -> {
                flight = step.ball
                val jevStatus = steerJev(step, court, leftDrive, rightDrive)
                val transaction = surfaces.begin(TRANSACTION_FRAME)
                surfaces.moveTask(transaction, ballPanel, step.ball.bounds())
                if (leftDrive.advance(deltaSeconds, config.paddleSpeedPx, court)) {
                    surfaces.movePanel(transaction, resolved.leftPaddle, leftDrive.bounds)
                }
                if (rightDrive.advance(deltaSeconds, config.paddleSpeedPx, court)) {
                    surfaces.movePanel(transaction, resolved.rightPaddle, rightDrive.bounds)
                }
                transaction.apply()
                report(status.inPlay(jevStatus))
            }
        }
    }

    /** A finger on a drawn paddle. The touch thread has already hopped to the shell thread. */
    fun touch(panelId: String, action: Int, rawY: Float) {
        val drive = drives[panelId] ?: return
        when (action) {
            MotionEvent.ACTION_DOWN -> drive.grab(rawY)
            MotionEvent.ACTION_MOVE -> {
                val lookup = parts.resolve() as? PongParts.Lookup.Found ?: return
                val panel = paddlePanel(lookup.parts, panelId) ?: return
                if (drive.follow(rawY, courtBounds(lookup.parts))) placePaddle(panel, drive)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drive.release()
        }
    }

    /**
     * A finger on the court's empty area steers the paddle of the half it landed on until that
     * finger lifts, wherever it is by then. This is how a paddle that is an app gets moved.
     * Jev's paddle obeys the finger while it is down. Two fingers steer two paddles; a paddle
     * already held ignores a second finger.
     */
    fun courtTouch(touch: PongCourtTouch) {
        val lookup = parts.resolve() as? PongParts.Lookup.Found ?: return
        val court = courtBounds(lookup.parts)
        when (touch) {
            is PongCourtTouch.Down -> {
                val pointer = touch.pointer
                val panel = if (pointer.rawX < court.exactCenterX()) {
                    lookup.parts.leftPaddle
                } else {
                    lookup.parts.rightPaddle
                }
                val drive = drive(panel)
                if (drive.isHeld) return
                courtPointers[pointer.pointerId] = panel.panelId
                if (drive.holdAt(pointer.rawY, court)) placePaddle(panel, drive)
            }
            is PongCourtTouch.Move -> for (pointer in touch.pointers) {
                val panelId = courtPointers[pointer.pointerId] ?: continue
                val panel = paddlePanel(lookup.parts, panelId) ?: continue
                val drive = drives[panelId] ?: continue
                if (drive.follow(pointer.rawY, court)) placePaddle(panel, drive)
            }
            is PongCourtTouch.Up -> {
                val panelId = courtPointers.remove(touch.pointerId) ?: return
                drives[panelId]?.release()
            }
            PongCourtTouch.Cancel -> {
                for (panelId in courtPointers.values) drives[panelId]?.release()
                courtPointers.clear()
            }
        }
    }

    private fun placePaddle(panel: SysUIPanel, drive: PongPaddleDrive) {
        val transaction = surfaces.begin(TRANSACTION_TOUCH)
        surfaces.movePanel(transaction, panel, drive.bounds)
        transaction.apply()
    }

    /** Who owns the paddle this frame: the pod in its playing variant, the XML anywhere else. */
    private fun syncWithVariant(panel: SysUIPanel, drive: PongPaddleDrive) {
        drive.syncWithVariant(
            surfaces.currentVariantName(panel.panelId),
            surfaces.currentBounds(panel.panelId) ?: panel.bounds,
            surfaces.isAnimating(panel.panelId),
        )
    }

    private fun courtBounds(resolved: PongParts.Resolved): Rect =
        surfaces.currentBounds(parts.courtId) ?: resolved.court.bounds

    private fun paddlePanel(resolved: PongParts.Resolved, panelId: String): SysUIPanel? = when (panelId) {
        resolved.leftPaddle.panelId -> resolved.leftPaddle
        resolved.rightPaddle.panelId -> resolved.rightPaddle
        else -> null
    }

    private fun drive(panel: SysUIPanel): PongPaddleDrive = drives.getOrPut(panel.panelId) {
        PongPaddleDrive(
            panel.panelId,
            surfaces.currentBounds(panel.panelId) ?: panel.bounds,
            surfaces.currentVariantName(panel.panelId),
        )
    }

    private fun launch(resolved: PongParts.Resolved): PongBall {
        val bounds = surfaces.currentBounds(parts.ballId) ?: resolved.ball.bounds
        val toward = serveToward ?: jevSide?.opposite ?: PongSide.LEFT
        val angle = SERVE_ANGLES[serveCount % SERVE_ANGLES.size]
        serveCount++
        Log.d(TAG, "flight from $bounds toward $toward at $angle°")
        return PongPhysics.serve(
            bounds.exactCenterX(), bounds.exactCenterY(), bounds.width() / 2f, bounds.height() / 2f,
            config.ballSpeedPx, toward, angle)
    }

    private fun steerJev(
        step: PongStep.Flight,
        court: Rect,
        leftDrive: PongPaddleDrive,
        rightDrive: PongPaddleDrive,
    ): PongJevBrain.Status? {
        val side = jevSide ?: return null
        val jevDrive = if (side == PongSide.LEFT) leftDrive else rightDrive
        val opponentDrive = if (side == PongSide.LEFT) rightDrive else leftDrive
        brain.observe(step.ball, court, jevDrive, opponentDrive.bounds, side,
            courseChanged = step.hitPaddle != null || step.hitWall)
        return brain.status
    }

    private fun point(scorer: PongSide) {
        val winner = score.point(scorer, config.winScore)
        Log.d(TAG, "point $scorer: ${score.left}:${score.right}")
        listener.onScore(score.left, score.right)
        fire(EVENT_POINT + scorer.eventSuffix)
        // The win is announced from frame() once the ball rests.
        if (winner == null) report(status.serving())
        serveToward = scorer.opposite
        scheduleServe()
    }

    /** Fires `_Pong_Win_<side>` once, after the point's glide has ended. */
    private fun announceWinOnceAtRest() {
        if (!score.winnerPending || surfaces.isAnimating(parts.ballId)) return
        val winner = score.takeWinnerToAnnounce() ?: return
        fire(EVENT_WIN + winner.eventSuffix)
        report(status.win(status.label(winner == jevSide)))
    }

    private fun scheduleServe() {
        shellMainExecutor.removeCallbacks(serveRunnable)
        shellMainExecutor.executeDelayed(serveRunnable, config.serveDelayMs)
        serveScheduled = true
    }

    private fun serve() {
        serveScheduled = false
        // An outside event already served.
        val variant = surfaces.currentVariantName(parts.ballId)
        if (variant == FLIGHT_VARIANT) return
        // A won game is announced, once the ball rests, before it is reset.
        if (score.winnerPending) {
            scheduleServe()
            return
        }
        // No `_Pong_Serve` from the ball's current variant (a celebration still running, or no
        // flight variant at all): retry later, and keep a `Game to …` line up meanwhile.
        if (!surfaces.hasTransitionFor(parts.ballId, EVENT_SERVE)) {
            if (score.gameWinner == null) report(status.serveNotReachable(variant ?: NO_VARIANT))
            scheduleServe()
            return
        }
        if (score.resetIfGameOver()) listener.onScore(score.left, score.right)
        surfaces.currentBounds(parts.ballId)?.let { resting ->
            surfaces.assignVariantBounds(parts.ballId, FLIGHT_VARIANT, resting)
        }
        fire(EVENT_SERVE)
        // StateManager switches the variant synchronously inside executeEvent.
        val variantAfter = surfaces.currentVariantName(parts.ballId)
        Log.d(TAG, "serve: ball variant now $variantAfter")
        if (variantAfter != FLIGHT_VARIANT) report(status.noFlightVariant())
    }

    private fun fire(eventId: String) {
        eventDispatcher.executeEvent(Event.Builder(eventId).setPanelId(parts.ballId).build())
    }

    private fun showLabels(resolved: PongParts.Resolved) {
        if (labelsShown) return
        labelsShown = true
        Log.d(TAG, "first frame: court=${resolved.court.bounds} ball=${resolved.ball.bounds}" +
            " left=${resolved.leftPaddle.panelId} right=${resolved.rightPaddle.panelId}")
        jevSide = when {
            config.isJevPaddle(resolved.leftPaddle.panelId) -> PongSide.LEFT
            config.isJevPaddle(resolved.rightPaddle.panelId) -> PongSide.RIGHT
            else -> null
        }
        listener.onLabels(
            status.label(jevSide == PongSide.LEFT), status.label(jevSide == PongSide.RIGHT))
    }

    private fun report(text: String) {
        if (text == lastStatus) return
        lastStatus = text
        listener.onStatus(text)
    }

    companion object {
        const val TAG = "PongMatch"
        /** The ball variant that means "in flight"; the pod stamps its bounds every frame. */
        const val FLIGHT_VARIANT = "in_play"
        const val EVENT_SERVE = "_Pong_Serve"
        const val EVENT_POINT = "_Pong_Point_"
        const val EVENT_WIN = "_Pong_Win_"
        private const val TRANSACTION_FRAME = "PongFrame"
        private const val TRANSACTION_TOUCH = "PongTouch"
        private const val NO_VARIANT = "(no variant)"
        /** Serve angles cycle, so a rally never starts the same way twice in a row. */
        private val SERVE_ANGLES = floatArrayOf(-20f, 0f, 20f)
    }
}
