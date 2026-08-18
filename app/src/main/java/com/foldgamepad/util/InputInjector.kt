package com.foldgamepad.util

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.foldgamepad.service.FoldAccessibilityService

/**
 * Dispatches a tap via the accessibility service's standard dispatchGesture().
 *
 * dispatchGesture() with no displayId always targets the DEFAULT display
 * (id 0, the inner screen), regardless of which display the service's own
 * overlay UI is currently rendered on.
 */
object InputInjector {

    private const val TAG = "InputInjector"

    @Volatile var service: FoldAccessibilityService? = null
    val isReady get() = service != null

    private val handler = Handler(Looper.getMainLooper())

    private fun pointPath(x: Float, y: Float): Path = Path().apply {
        moveTo(x, y); lineTo(x + 0.5f, y + 0.5f)
    }

    private val resultCallback = object : AccessibilityService.GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription) {
            Log.i(TAG, "Gesture COMPLETED ✓")
        }
        override fun onCancelled(gestureDescription: GestureDescription) {
            Log.w(TAG, "Gesture CANCELLED ✗ — likely blocked by a real touch or focus issue")
        }
    }

    /**
     * Tap at (x, y) on the default display. Returns a status string describing
     * exactly what happened, so callers can surface it (Toast/log) rather than
     * failing silently.
     */
    fun tap(x: Int, y: Int): String {
        val svc = service ?: run {
            Log.w(TAG, "tap($x,$y) skipped — accessibility service not connected")
            return "NOT_READY: accessibility service not connected"
        }
        return try {
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(
                    pointPath(x.toFloat(), y.toFloat()), 0L, 60L
                ))
                .build()
            val accepted = svc.dispatchGesture(gesture, resultCallback, handler)
            Log.i(TAG, "tap($x,$y) dispatchGesture accepted=$accepted")
            if (accepted) "DISPATCHED to ($x,$y)" else "REJECTED by dispatchGesture (accepted=false)"
        } catch (e: Exception) {
            Log.e(TAG, "tap($x,$y) threw: ${e.message}", e)
            "EXCEPTION: ${e.message}"
        }
    }

    fun tapOnDisplay(displayId: Int, x: Int, y: Int): String = tap(x, y)
}
