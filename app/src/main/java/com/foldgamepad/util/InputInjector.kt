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
 * Each active joystick gets its own state and stroke chain; every tick (~80ms)
 * we pack all live joystick continuations and queued taps into one GestureDescription.
 * This lets two joysticks run simultaneously and lets taps fire during a joystick hold.
 *
 * On gesture cancellation (real screen touch preempts accessibility input), all stroke
 * refs are cleared and the next tick spawns fresh strokes — a brief release/re-press
 * that keeps joysticks responsive.
 */
object InputInjector {

    @Volatile var service: FoldAccessibilityService? = null
    val isReady get() = service != null

    private val handler = Handler(Looper.getMainLooper())
    private const val STROKE_MS = 80L

    private data class JoyState(
        var targetX: Float, var targetY: Float,
        var lastX: Float,   var lastY: Float,
        var stroke: GestureDescription.StrokeDescription? = null,
        var ending: Boolean = false
    )

    private val joysticks   = HashMap<String, JoyState>()
    private val pendingTaps = mutableListOf<Pair<Int, Int>>()
    private var tickInFlight = false

    // ── Path helpers ──────────────────────────────────────────────────────────

    private fun segment(fromX: Float, fromY: Float, toX: Float, toY: Float): Path =
        Path().apply {
            moveTo(fromX, fromY)
            if (abs(toX - fromX) < 0.5f && abs(toY - fromY) < 0.5f)
                lineTo(fromX + 0.5f, fromY + 0.5f)
            else lineTo(toX, toY)
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
                        pointPath(x.toFloat(), y.toFloat()), 0L, 60L))
                    .build(), null, null)
        } catch (e: Exception) { /* swallow */ }
    }

    // ── Joystick API ──────────────────────────────────────────────────────────

    fun joystickDown(id: String, cx: Int, cy: Int) {
        synchronized(joysticks) {
            joysticks[id] = JoyState(cx.toFloat(), cy.toFloat(), cx.toFloat(), cy.toFloat())
            if (!tickInFlight) scheduleTick()
        }
    }

    fun joystickUpdate(id: String, tx: Float, ty: Float) {
        synchronized(joysticks) { joysticks[id]?.let { it.targetX = tx; it.targetY = ty } }
    }

    fun joystickUp(id: String) {
        synchronized(joysticks) {
            joysticks[id]?.let { it.ending = true }
            if (!tickInFlight) scheduleTick()
        }
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    private val tickCallback = object : AccessibilityService.GestureResultCallback() {
        override fun onCompleted(g: GestureDescription) = synchronized(joysticks) {
            tickInFlight = false; scheduleTick()
        }
        override fun onCancelled(g: GestureDescription) = synchronized(joysticks) {
            for (joy in joysticks.values) joy.stroke = null
            tickInFlight = false; scheduleTick()
        }
    }

    private fun scheduleTick() {
        if (tickInFlight) return
        if (joysticks.isEmpty() && pendingTaps.isEmpty()) return
        dispatchTick()
    }

    private fun dispatchTick() {
        val svc = service ?: return
        val builder  = GestureDescription.Builder()
        var anyStroke = false
        val toRemove  = mutableListOf<String>()

        for ((_, joy) in joysticks) {
            if (joy.ending) {
                joy.stroke?.let { live ->
                    builder.addStroke(live.continueStroke(
                        segment(joy.lastX, joy.lastY, joy.lastX, joy.lastY), 0L, 50L, false))
                    anyStroke = true
                }
                toRemove.add(joy.hashCode().toString()) // placeholder — real key below
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

        // Remove ended joysticks by key
        for ((key, joy) in joysticks.entries.toList()) {
            if (joy.ending) joysticks.remove(key)
        }

        val taps = synchronized(pendingTaps) { pendingTaps.toList().also { pendingTaps.clear() } }
        taps.forEach { (tx, ty) ->
            builder.addStroke(GestureDescription.StrokeDescription(
                pointPath(tx.toFloat(), ty.toFloat()), 10L, 60L))
            anyStroke = true
        }

        if (!anyStroke) return
        tickInFlight = true
        try { svc.dispatchGesture(builder.build(), tickCallback, handler) }
        catch (e: Exception) { tickInFlight = false }
    }

    // ── Swipe pad ─────────────────────────────────────────────────────────────

    fun swipePad(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val svc = service ?: return
        try {
            svc.dispatchGesture(
                GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(
                        segment(fromX, fromY, toX, toY), 0L, 80L))
                    .build(), null, null)
        } catch (e: Exception) { /* swallow */ }
    }
}
