package com.foldgamepad.util

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import com.foldgamepad.service.FoldAccessibilityService

object InputInjector {

    /** Set by FoldAccessibilityService when it connects/disconnects. */
    @Volatile var service: FoldAccessibilityService? = null

    val isReady get() = service != null

    // ── Tap ──────────────────────────────────────────────────────────────────

    fun tap(x: Int, y: Int) {
        val svc = service ?: return
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
        svc.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    // ── Joystick ─────────────────────────────────────────────────────────────
    // While a joystick is held we repeatedly dispatch short swipe gestures from
    // the mapped centre point toward the mapped direction. 80 ms interval feels
    // responsive without flooding the input pipeline.

    private val joystickHandler = Handler(Looper.getMainLooper())
    private var joystickRunnable: Runnable? = null

    /** Call once when the joystick thumb first moves off-centre. */
    fun startJoystick(
        centreX: Int,
        centreY: Int,
        gameRadius: Int,
        getDelta: () -> Pair<Float, Float>   // live callback: (dx, dy) in -1..1
    ) {
        stopJoystick()
        joystickRunnable = object : Runnable {
            override fun run() {
                val (dx, dy) = getDelta()
                if (dx != 0f || dy != 0f) {
                    dispatchJoystickSwipe(centreX, centreY, dx, dy, gameRadius)
                }
                joystickHandler.postDelayed(this, 80L)
            }
        }
        joystickHandler.post(joystickRunnable!!)
    }

    /** Call when the joystick thumb is released. */
    fun stopJoystick() {
        joystickRunnable?.let { joystickHandler.removeCallbacks(it) }
        joystickRunnable = null
    }

    private fun dispatchJoystickSwipe(
        cx: Int, cy: Int, dx: Float, dy: Float, radius: Int
    ) {
        val svc = service ?: return
        val tx = cx + dx * radius
        val ty = cy + dy * radius
        val path = Path().apply {
            moveTo(cx.toFloat(), cy.toFloat())
            lineTo(tx, ty)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 80L)
        svc.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }
}
