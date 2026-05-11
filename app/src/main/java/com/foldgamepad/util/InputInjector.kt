package com.foldgamepad.util

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import com.foldgamepad.service.FoldAccessibilityService

object InputInjector {

    @Volatile var service: FoldAccessibilityService? = null
    val isReady get() = service != null

    private val handler = Handler(Looper.getMainLooper())

    // ── Pending taps ──────────────────────────────────────────────────────────
    // When a joystick gesture is active, taps are queued here and bundled into
    // the next joystick continuation as a simultaneous multi-touch stroke.
    // This avoids calling dispatchGesture() twice (which cancels the first call).

    private val pendingTaps = mutableListOf<Pair<Int, Int>>()

    // ── Tap ──────────────────────────────────────────────────────────────────

    fun tap(x: Int, y: Int) {
        if (joystickActive) {
            // Defer: will be bundled into the next joystick gesture dispatch
            synchronized(pendingTaps) { pendingTaps.add(x to y) }
        } else {
            dispatchTapNow(x, y)
        }
    }

    private fun dispatchTapNow(x: Int, y: Int) {
        val svc = service ?: return
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
        svc.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    // ── Continuous joystick ───────────────────────────────────────────────────
    // Maintains an unbroken ACTION_DOWN → ACTION_MOVE chain via continueStroke.
    // Each ~80 ms the callback fires; we continue from the current target position
    // and drain any pending button taps as simultaneous second-touch strokes.

    private var joystickActive = false
    private var targetX = 0f;  private var targetY = 0f
    private var lastX   = 0f;  private var lastY   = 0f
    private var currentStroke: GestureDescription.StrokeDescription? = null

    private val joystickCallback = object : AccessibilityService.GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription) {
            if (!joystickActive) return
            val svc    = service ?: return
            val stroke = currentStroke ?: return

            // Move (or stay) at current joystick target
            val path = Path().apply {
                moveTo(lastX, lastY)
                lineTo(targetX, targetY)
            }
            lastX = targetX;  lastY = targetY
            currentStroke = stroke.continueStroke(path, 0L, STROKE_MS, true)

            val builder = GestureDescription.Builder().addStroke(currentStroke!!)

            // Bundle any queued button taps as simultaneous multi-touch strokes
            val taps = synchronized(pendingTaps) { pendingTaps.toList().also { pendingTaps.clear() } }
            taps.forEach { (tx, ty) ->
                val tapPath = Path().apply { moveTo(tx.toFloat(), ty.toFloat()) }
                // Small startTime offset keeps Android happy when mixing stroke types
                builder.addStroke(GestureDescription.StrokeDescription(tapPath, 10L, 60L))
            }

            svc.dispatchGesture(builder.build(), this, handler)
        }

        override fun onCancelled(gestureDescription: GestureDescription) {
            if (joystickActive) restartJoystick()
        }
    }

    fun joystickDown(cx: Int, cy: Int) {
        val svc = service ?: return
        joystickActive = true
        lastX = cx.toFloat();   lastY = cy.toFloat()
        targetX = cx.toFloat(); targetY = cy.toFloat()

        val path = Path().apply {
            moveTo(lastX, lastY)
            lineTo(lastX + 0.1f, lastY)   // tiny extent — required for valid stroke
        }
        currentStroke = GestureDescription.StrokeDescription(path, 0L, STROKE_MS, true)
        svc.dispatchGesture(
            GestureDescription.Builder().addStroke(currentStroke!!).build(),
            joystickCallback, handler
        )
    }

    fun joystickUpdate(tx: Float, ty: Float) {
        targetX = tx;  targetY = ty
    }

    fun joystickUp() {
        joystickActive = false
        val svc    = service ?: return
        val stroke = currentStroke ?: return
        val path   = Path().apply { moveTo(lastX, lastY); lineTo(lastX + 0.1f, lastY) }
        val end    = stroke.continueStroke(path, 0L, 50L, false)
        svc.dispatchGesture(GestureDescription.Builder().addStroke(end).build(), null, null)
        currentStroke = null
    }

    private fun restartJoystick() {
        joystickDown(lastX.toInt(), lastY.toInt())
    }

    // ── Swipe pad ─────────────────────────────────────────────────────────────

    fun swipePad(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val svc = service ?: return
        val path = Path().apply { moveTo(fromX, fromY); lineTo(toX, toY) }
        svc.dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, 80L))
                .build(),
            null, null
        )
    }

    private const val STROKE_MS = 80L
}
