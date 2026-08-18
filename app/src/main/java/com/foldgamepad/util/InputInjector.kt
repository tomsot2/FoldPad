package com.foldgamepad.util

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import com.foldgamepad.service.FoldAccessibilityService

/**
 * Dispatches a tap via the accessibility service's standard dispatchGesture().
 *
 * IMPORTANT: dispatchGesture() with no displayId always targets the DEFAULT
 * display (id 0), regardless of which display the service's own overlay UI is
 * currently rendered on. This means a button shown on the cover screen
 * (a separate Presentation on a non-default display) can still inject a touch
 * onto the inner screen's game — no special multi-display API needed.
 *
 * This is also why a same-display overlay (button UI and game both on display 0)
 * doesn't work: the real finger touch and the injected gesture collide on the
 * same input channel and Android cancels both. Putting the button UI on a
 * different display (the cover screen) sidesteps that collision entirely.
 */
object InputInjector {

    @Volatile var service: FoldAccessibilityService? = null
    val isReady get() = service != null

    private val handler = Handler(Looper.getMainLooper())

    private fun pointPath(x: Float, y: Float): Path = Path().apply {
        moveTo(x, y); lineTo(x + 0.5f, y + 0.5f)
    }

    /**
     * Tap at (x, y) — always lands on the default display (the inner screen),
     * regardless of which display this call originates from.
     */
    fun tap(x: Int, y: Int) {
        val svc = service ?: return
        try {
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(
                    pointPath(x.toFloat(), y.toFloat()), 0L, 60L
                ))
                .build()
            svc.dispatchGesture(gesture, null, handler)
        } catch (e: Exception) {
            android.util.Log.w("InputInjector", "tap failed: ${e.message}")
        }
    }

    // Kept for source compatibility with existing call sites — displayId is
    // now ignored since plain dispatchGesture() already targets the default
    // display regardless of caller's display.
    fun tapOnDisplay(displayId: Int, x: Int, y: Int) = tap(x, y)
}
