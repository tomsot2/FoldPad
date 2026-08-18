package com.foldgamepad.util

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import com.foldgamepad.service.FoldAccessibilityService

/**
 * Dispatches taps to a SPECIFIC display — critical for cover-screen triggers,
 * since the button press happens on the cover display but must land on the
 * inner display where the game is actually running.
 *
 * Cross-display gesture dispatch (GestureDescription.Builder(displayId)) requires
 * API 33+. The Fold 7 ships well above that, so no fallback path is needed.
 */
object InputInjector {

    @Volatile var service: FoldAccessibilityService? = null
    val isReady get() = service != null

    private val handler = Handler(Looper.getMainLooper())

    private fun pointPath(x: Float, y: Float): Path = Path().apply {
        moveTo(x, y); lineTo(x + 0.5f, y + 0.5f)
    }

    /** Tap at (x, y) on the display identified by [displayId]. */
    fun tapOnDisplay(displayId: Int, x: Int, y: Int) {
        val svc = service ?: return
        try {
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                GestureDescription.Builder(displayId)
            } else {
                GestureDescription.Builder()
            }
            builder.addStroke(GestureDescription.StrokeDescription(
                pointPath(x.toFloat(), y.toFloat()), 0L, 60L
            ))
            svc.dispatchGesture(builder.build(), null, handler)
        } catch (e: Exception) {
            android.util.Log.w("InputInjector", "tapOnDisplay failed: ${e.message}")
        }
    }
}
