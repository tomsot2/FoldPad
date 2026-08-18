package com.foldgamepad.util

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.foldgamepad.service.FoldAccessibilityService
import java.lang.reflect.Method

/**
 * Dispatches taps to a SPECIFIC display — needed for cover-screen triggers,
 * since the button press happens on the cover display but must land on the
 * inner display where the game is running.
 *
 * IMPORTANT: There is no PUBLIC compile-time API on AccessibilityService for
 * cross-display gesture dispatch in the standard SDK — the 4-arg
 * dispatchGesture(displayId, GestureDescription, callback, Handler) is not
 * exposed as a stable public method we can call directly. We look it up via
 * reflection at runtime instead: if the OS build actually has it (some OEM/AOSP
 * versions expose it for multi-display accessibility), we use it; if not, we
 * fall back to standard single-display dispatch, which will target whichever
 * display currently holds accessibility focus (not necessarily the inner one).
 *
 * isCrossDisplaySupported tells the rest of the app which situation we're in.
 */
object InputInjector {

    @Volatile var service: FoldAccessibilityService? = null
    val isReady get() = service != null

    private val handler = Handler(Looper.getMainLooper())

    private var crossDisplayMethod: Method? = null
    private var lookedUp = false
    val isCrossDisplaySupported: Boolean get() { ensureLookup(); return crossDisplayMethod != null }

    private fun ensureLookup() {
        if (lookedUp) return
        lookedUp = true
        try {
            crossDisplayMethod = AccessibilityService::class.java.getMethod(
                "dispatchGesture",
                Int::class.javaPrimitiveType,
                GestureDescription::class.java,
                AccessibilityService.GestureResultCallback::class.java,
                Handler::class.java
            )
            android.util.Log.i("InputInjector", "Cross-display dispatchGesture found ✓")
        } catch (e: NoSuchMethodException) {
            android.util.Log.w("InputInjector", "Cross-display dispatchGesture NOT available on this OS build")
            crossDisplayMethod = null
        }
    }

    private fun pointPath(x: Float, y: Float): Path = Path().apply {
        moveTo(x, y); lineTo(x + 0.5f, y + 0.5f)
    }

    /** Tap at (x, y) on the display identified by [displayId], if supported. */
    fun tapOnDisplay(displayId: Int, x: Int, y: Int) {
        val svc = service ?: return
        ensureLookup()

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(
                pointPath(x.toFloat(), y.toFloat()), 0L, 60L
            ))
            .build()

        val method = crossDisplayMethod
        if (method != null) {
            try {
                method.invoke(svc, displayId, gesture, null, handler)
                return
            } catch (e: Exception) {
                android.util.Log.w("InputInjector", "Reflective cross-display dispatch failed: ${e.message}")
            }
        }

        // Fallback: standard dispatch, targets whatever display is currently focused.
        try {
            svc.dispatchGesture(gesture, null, handler)
        } catch (e: Exception) {
            android.util.Log.w("InputInjector", "dispatchGesture failed: ${e.message}")
        }
    }
}
