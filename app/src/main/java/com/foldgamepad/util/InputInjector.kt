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

    // ── Tap ──────────────────────────────────────────────────────────────────

    fun tap(x: Int, y: Int) {
        val svc = service ?: return
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
        svc.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    // ── Continuous joystick (hold-and-drag) ───────────────────────────────────
    // Uses continueStroke so the game sees one unbroken ACTION_DOWN → ACTION_MOVE
    // sequence rather than repeated taps.

    private var joystickActive   = false
    private var currentTX        = 0f
    private var currentTY        = 0f
    private var lastTX           = 0f
    private var lastTY           = 0f
    private var currentStroke: GestureDescription.StrokeDescription? = null

    private val joystickCallback = object : AccessibilityService.GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription) {
            if (!joystickActive) return
            val svc = service ?: return
            val stroke = currentStroke ?: return
            // Continue from last position to wherever the thumb is now
            val path = Path().apply {
                moveTo(lastTX, lastTY)
                lineTo(currentTX, currentTY)
            }
            lastTX = currentTX
            lastTY = currentTY
            currentStroke = stroke.continueStroke(path, 0L, STROKE_MS, true)
            svc.dispatchGesture(
                GestureDescription.Builder().addStroke(currentStroke!!).build(),
                this, handler
            )
        }

        override fun onCancelled(gestureDescription: GestureDescription) {
            // Restart gesture if we're still holding
            if (joystickActive) restartJoystick()
        }
    }

    /** Call when the virtual joystick thumb first moves off centre. */
    fun joystickDown(cx: Int, cy: Int) {
        val svc = service ?: return
        joystickActive = true
        lastTX   = cx.toFloat();  lastTY   = cy.toFloat()
        currentTX = cx.toFloat(); currentTY = cy.toFloat()
        val path = Path().apply {
            moveTo(lastTX, lastTY)
            lineTo(lastTX + 0.1f, lastTY) // tiny extent required
        }
        currentStroke = GestureDescription.StrokeDescription(path, 0L, STROKE_MS, true)
        svc.dispatchGesture(
            GestureDescription.Builder().addStroke(currentStroke!!).build(),
            joystickCallback, handler
        )
    }

    /** Call every time the normalised joystick position changes. */
    fun joystickUpdate(tx: Float, ty: Float) {
        currentTX = tx
        currentTY = ty
    }

    /** Call when the thumb is released. */
    fun joystickUp() {
        joystickActive = false
        val svc    = service ?: return
        val stroke = currentStroke ?: return
        // Final stroke: return to centre (or stay) then lift
        val path = Path().apply { moveTo(lastTX, lastTY); lineTo(lastTX + 0.1f, lastTY) }
        val end  = stroke.continueStroke(path, 0L, 50L, false)
        svc.dispatchGesture(GestureDescription.Builder().addStroke(end).build(), null, null)
        currentStroke = null
    }

    private fun restartJoystick() {
        joystickDown(lastTX.toInt(), lastTY.toInt())
    }

    // ── Swipe pad (camera drag) ────────────────────────────────────────────────
    // Dispatches a quick one-shot swipe; good for camera control where each
    // movement is a small swipe from the target centre.

    fun swipePad(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val svc = service ?: return
        val path = Path().apply { moveTo(fromX, fromY); lineTo(toX, toY) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 80L)
        svc.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private const val STROKE_MS = 120L   // How long each continued segment lasts
}
