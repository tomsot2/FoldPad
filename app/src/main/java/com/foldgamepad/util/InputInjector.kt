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
 * Cross-display gesture dispatch (the displayId-aware dispatchGesture overload)
 * requires API 34+. The Fold 7 ships well above that.
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
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(
                    pointPath(x.toFloat(), y.toFloat()), 0L, 60L
                ))
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                svc.dispatchGesture(displayId, gesture, null, handler)
            } else {
                // Pre-API 34: no cross-display dispatch available. Falls back to
                // whichever display is currently receiving accessibility focus.
                svc.dispatchGesture(gesture, null, handler)
            }
        } catch (e: Exception) {
            android.util.Log.w("InputInjector", "tapOnDisplay failed: ${e.message}")
        }
    }
}
