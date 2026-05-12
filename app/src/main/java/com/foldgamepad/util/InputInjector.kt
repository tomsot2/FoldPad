package com.foldgamepad.util

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import com.foldgamepad.service.FoldAccessibilityService
import kotlin.math.abs

/**
 * Accessibility-service input injector.
 *
 * Each active joystick gets its own state and stroke chain; every tick (~ STROKE_MS)
 * we pack all live joystick continuations *and* any queued taps into one
 * GestureDescription and dispatch it. This lets two joysticks run simultaneously
 * and lets a button tap fire during a joystick hold without breaking the hold.
 *
 * Cancellation handling: if Android cancels the gesture (real screen touch
 * preempts accessibility input), all stroke refs are cleared and the next tick
 * just creates fresh strokes at each joystick's current target. The game sees
 * a brief release/re-press, but each joystick stays responsive.
 */
object InputInjector {

    @Volatile var service: FoldAccessibilityService? = null
    val isReady get() = service != null

    private val handler = Handler(Looper.getMainLooper())

    private const val STROKE_MS = 80L

    private data class JoyState(
        var targetX: Float, var targetY: Float,
        var lastX:   Float, var lastY:   Float,
        var stroke:  GestureDescription.StrokeDescription? = null,
        var ending:  Boolean = false
    )

    // Per-joystick state, keyed by button id (e.g. "joy_l", "joy_r").
    // Synchronised because joystick events fire on the UI thread but onCompleted
    // callbacks may arrive on a worker thread.
    private val joysticks = HashMap<String, JoyState>()
    private val pendingTaps = mutableListOf<Pair<Int, Int>>()

    // True while a dispatch is in flight — we tick again only after onCompleted/onCancelled.
    private var tickInFlight = false

    // ── Path helpers ──────────────────────────────────────────────────────────

    private fun segment(fromX: Float, fromY: Float, toX: Float, toY: Float): Path {
        val dx = toX - fromX
        val dy = toY - fromY
        return Path().apply {
            moveTo(fromX, fromY)
            if (abs(dx) < 0.5f && abs(dy) < 0.5f) {
                lineTo(fromX + 0.5f, fromY + 0.5f)
            } else {
                lineTo(toX, toY)
            }
        }
    }

    private fun pointPath(x: Float, y: Float): Path = Path().apply {
        moveTo(x, y); lineTo(x + 0.5f, y + 0.5f)
    }

    // ── Tap ───────────────────────────────────────────────────────────────────

    fun tap(x: Int, y: Int) {
        synchronized(joysticks) {
            if (joysticks.isNotEmpty()) {
                synchronized(pendingTaps) { pendingTaps.add(x to y) }
                if (!tickInFlight) scheduleTick()
            } else {
                dispatchTapNow(x, y)
            }
        }
    }

    private fun dispatchTapNow(x: Int, y: Int) {
        val svc = service ?: return
        try {
            svc.dispatchGesture(
                GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(
                        pointPath(x.toFloat(), y.toFloat()), 0L, 60L
                    )).build(),
                null, null
            )
        } catch (e: Exception) { /* swallow */ }
    }

    // ── Joystick API ──────────────────────────────────────────────────────────

    fun joystickDown(id: String, cx: Int, cy: Int) {
        synchronized(joysticks) {
            joysticks[id] = JoyState(
                targetX = cx.toFloat(), targetY = cy.toFloat(),
                lastX   = cx.toFloat(), lastY   = cy.toFloat()
            )
            if (!tickInFlight) scheduleTick()
        }
    }

    fun joystickUpdate(id: String, tx: Float, ty: Float) {
        synchronized(joysticks) {
            joysticks[id]?.let { it.targetX = tx; it.targetY = ty }
        }
    }

    fun joystickUp(id: String) {
        synchronized(joysticks) {
            joysticks[id]?.let { it.ending = true }
            if (!tickInFlight) scheduleTick()
        }
    }

    // Legacy single-joystick API (kept for compatibility with older callers).
    private const val LEGACY_ID = "_legacy"
    fun joystickDown(cx: Int, cy: Int) = joystickDown(LEGACY_ID, cx, cy)
    fun joystickUpdate(tx: Float, ty: Float) = joystickUpdate(LEGACY_ID, tx, ty)
    fun joystickUp() = joystickUp(LEGACY_ID)

    // ── Tick / dispatch ───────────────────────────────────────────────────────

    private val tickCallback = object : AccessibilityService.GestureResultCallback() {
        override fun onCompleted(g: GestureDescription) {
            synchronized(joysticks) {
                tickInFlight = false
                scheduleTick()
            }
        }
        override fun onCancelled(g: GestureDescription) {
            synchronized(joysticks) {
                for (joy in joysticks.values) joy.stroke = null
                tickInFlight = false
                scheduleTick()
            }
        }
    }

    /** Caller must hold the joysticks lock. */
    private fun scheduleTick() {
        if (tickInFlight) return
        if (joysticks.isEmpty() && pendingTaps.isEmpty()) return
        dispatchTick()
    }

    /** Caller must hold the joysticks lock. */
    private fun dispatchTick() {
        val svc = service ?: return
        val builder = GestureDescription.Builder()
        var anyStroke = false
        val toRemove = mutableListOf<String>()

        for ((id, joy) in joysticks) {
            if (joy.ending) {
                joy.stroke?.let { live ->
                    builder.addStroke(live.continueStroke(
                        segment(joy.lastX, joy.lastY, joy.lastX, joy.lastY),
                        0L, 50L, false
                    ))
                    anyStroke = true
                }
                toRemove.add(id)
            } else {
                val path = segment(joy.lastX, joy.lastY, joy.targetX, joy.targetY)
                joy.lastX = joy.targetX; joy.lastY = joy.targetY

                val s = joy.stroke?.continueStroke(path, 0L, STROKE_MS, true)
                    ?: GestureDescription.StrokeDescription(path, 0L, STROKE_MS, true)
                joy.stroke = s
                builder.addStroke(s)
                anyStroke = true
            }
        }
        toRemove.forEach { joysticks.remove(it) }

        val taps = synchronized(pendingTaps) { pendingTaps.toList().also { pendingTaps.clear() } }
        taps.forEach { (tx, ty) ->
            builder.addStroke(GestureDescription.StrokeDescription(
                pointPath(tx.toFloat(), ty.toFloat()), 10L, 60L
            ))
            anyStroke = true
        }

        if (!anyStroke) return

        tickInFlight = true
        try {
            svc.dispatchGesture(builder.build(), tickCallback, handler)
        } catch (e: Exception) {
            tickInFlight = false
        }
    }

    // ── Swipe pad ─────────────────────────────────────────────────────────────

    fun swipePad(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val svc = service ?: return
        try {
            svc.dispatchGesture(
                GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(
                        segment(fromX, fromY, toX, toY), 0L, 80L
                    )).build(),
                null, null
            )
        } catch (e: Exception) { /* swallow */ }
    }
}
