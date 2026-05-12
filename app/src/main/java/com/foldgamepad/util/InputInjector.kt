package com.foldgamepad.util

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import com.foldgamepad.service.FoldAccessibilityService
import kotlin.math.abs

object InputInjector {

    @Volatile var service: FoldAccessibilityService? = null
    val isReady get() = service != null

    private val handler = Handler(Looper.getMainLooper())

    // When a joystick gesture is active, taps are queued here and bundled into
    // the next joystick continuation as a simultaneous multi-touch stroke.
    private val pendingTaps = mutableListOf<Pair<Int, Int>>()

    /**
     * Build a path with guaranteed non-zero length. Android rejects degenerate
     * (zero-length) strokes and silently cancels the entire gesture if one is
     * included, which was the root cause of the "stutter when held still" bug:
     * holding the joystick stationary made target == last, producing a
     * zero-length continuation path that Android then cancelled, looping forever.
     */
    private fun segment(fromX: Float, fromY: Float, toX: Float, toY: Float): Path {
        val dx = toX - fromX
        val dy = toY - fromY
        return Path().apply {
            moveTo(fromX, fromY)
            if (abs(dx) < 0.5f && abs(dy) < 0.5f) {
                // Imperceptible offset keeps the path valid while holding still.
                lineTo(fromX + 0.5f, fromY + 0.5f)
            } else {
                lineTo(toX, toY)
            }
        }
    }

    private fun pointPath(x: Float, y: Float): Path = Path().apply {
        moveTo(x, y)
        lineTo(x + 0.5f, y + 0.5f)
    }

    // ── Tap ──────────────────────────────────────────────────────────────────

    fun tap(x: Int, y: Int) {
        if (joystickActive) {
            synchronized(pendingTaps) { pendingTaps.add(x to y) }
        } else {
            dispatchTapNow(x, y)
        }
    }

    private fun dispatchTapNow(x: Int, y: Int) {
        val svc = service ?: return
        val stroke = GestureDescription.StrokeDescription(pointPath(x.toFloat(), y.toFloat()), 0L, 60L)
        svc.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    // ── Continuous joystick ───────────────────────────────────────────────────

    private const val STROKE_MS = 80L
    private const val RESTART_DELAY_MS = 60L

    private var joystickActive = false
    private var targetX = 0f;  private var targetY = 0f
    private var lastX   = 0f;  private var lastY   = 0f
    private var currentStroke: GestureDescription.StrokeDescription? = null
    private var restartPending = false

    private val joystickCallback = object : AccessibilityService.GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription) {
            if (!joystickActive) return
            dispatchNextContinuation()
        }

        override fun onCancelled(gestureDescription: GestureDescription) {
            if (!joystickActive) return
            // Cancellation usually means the user touched the screen elsewhere
            // (touches preempt accessibility gestures). Wait briefly so the
            // conflicting finger has time to lift, then resume from the current
            // target. Without the delay, the restart is immediately cancelled
            // again and the joystick appears to freeze.
            if (restartPending) return
            restartPending = true
            handler.postDelayed({
                restartPending = false
                if (joystickActive) restartFromCurrentTarget()
            }, RESTART_DELAY_MS)
        }
    }

    private fun dispatchNextContinuation() {
        val svc    = service ?: return
        val stroke = currentStroke ?: return restartFromCurrentTarget()

        val path = segment(lastX, lastY, targetX, targetY)
        lastX = targetX;  lastY = targetY

        val next = stroke.continueStroke(path, 0L, STROKE_MS, true)
        currentStroke = next

        val builder = GestureDescription.Builder().addStroke(next)
        val taps = synchronized(pendingTaps) { pendingTaps.toList().also { pendingTaps.clear() } }
        taps.forEach { (tx, ty) ->
            builder.addStroke(GestureDescription.StrokeDescription(
                pointPath(tx.toFloat(), ty.toFloat()), 10L, 60L
            ))
        }

        try {
            svc.dispatchGesture(builder.build(), joystickCallback, handler)
        } catch (e: Exception) {
            joystickActive = false
            currentStroke  = null
        }
    }

    fun joystickDown(cx: Int, cy: Int) {
        val svc = service ?: return
        joystickActive = true
        restartPending = false
        lastX = cx.toFloat();   lastY = cy.toFloat()
        targetX = cx.toFloat(); targetY = cy.toFloat()

        currentStroke = GestureDescription.StrokeDescription(
            pointPath(lastX, lastY), 0L, STROKE_MS, true
        )
        try {
            svc.dispatchGesture(
                GestureDescription.Builder().addStroke(currentStroke!!).build(),
                joystickCallback, handler
            )
        } catch (e: Exception) {
            joystickActive = false
            currentStroke  = null
        }
    }

    fun joystickUpdate(tx: Float, ty: Float) {
        targetX = tx;  targetY = ty
    }

    fun joystickUp() {
        joystickActive = false
        restartPending = false
        val svc    = service ?: return
        val stroke = currentStroke ?: return
        try {
            val end = stroke.continueStroke(
                segment(lastX, lastY, lastX, lastY), 0L, 50L, false
            )
            svc.dispatchGesture(
                GestureDescription.Builder().addStroke(end).build(), null, null
            )
        } catch (e: Exception) { /* swallow */ }
        currentStroke = null
    }

    /**
     * Start a fresh stroke at the current target position. The game will see
     * a brief release-and-repress, but the position stays correct.
     */
    private fun restartFromCurrentTarget() {
        val svc = service ?: return
        if (!joystickActive) return
        lastX = targetX;  lastY = targetY
        currentStroke = GestureDescription.StrokeDescription(
            pointPath(targetX, targetY), 0L, STROKE_MS, true
        )
        try {
            svc.dispatchGesture(
                GestureDescription.Builder().addStroke(currentStroke!!).build(),
                joystickCallback, handler
            )
        } catch (e: Exception) {
            joystickActive = false
            currentStroke  = null
        }
    }

    // ── Swipe pad ─────────────────────────────────────────────────────────────

    fun swipePad(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val svc = service ?: return
        svc.dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(
                    segment(fromX, fromY, toX, toY), 0L, 80L
                ))
                .build(),
            null, null
        )
    }
}
