package com.foldgamepad.util

import android.util.Log
import com.foldgamepad.model.ButtonConfig
import com.foldgamepad.model.ButtonType
import java.lang.reflect.Method

/**
 * Tries Samsung's native Game Optimizing Service input redirection via reflection.
 *
 * When active, GOS intercepts touches on the panel and redirects them to the game
 * at the mapped coordinates — fully bypassing AccessibilityService and its
 * gesture-cancellation limitations.
 *
 * JSON format reverse-engineered from Game Booster v8 B7/e.smali:
 *   { "status":1, "param":[{"typeParam":5,"pointParam":"[gameX, gameY, L, T, R, B]"}] }
 *
 * typeParam = (inputType | 4) | (inDisplayId << 3)
 *   inputType 1=tap, 2=joystick/drag   inDisplayId always 0 on single display
 */
object SamsungInputBridge {

    private const val TAG          = "SamsungBridge"
    private const val ACTION_SET   = "set_input_redirection"
    private const val ACTION_CLEAR = "set_input_redirection"

    private var instance: Any?    = null
    private var rwMethod: Method? = null

    var isActive   = false; private set
    val isAvailable get() = rwMethod != null

    fun init() {
        try {
            val cls  = Class.forName("com.samsung.android.game.SemGameManager")
            instance = cls.getDeclaredConstructor().newInstance()
            rwMethod = cls.getMethod("requestWithJson", String::class.java, String::class.java)
            Log.i(TAG, "SemGameManager available ✓")
        } catch (e: Exception) {
            Log.w(TAG, "SemGameManager not available: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun apply(buttons: List<ButtonConfig>, screenW: Int, gameH: Int, panelH: Int): Boolean {
        val method = rwMethod ?: return false
        val mgr    = instance  ?: return false
        val json   = buildJson(1, buttons, screenW, gameH, panelH)
        Log.d(TAG, "Sending: $json")
        return try {
            val result = method.invoke(mgr, ACTION_SET, json) as? String
            Log.i(TAG, "requestWithJson result: $result")
            isActive = true
            true
        } catch (e: Exception) {
            Log.w(TAG, "requestWithJson failed: ${e.message}")
            isActive = false
            false
        }
    }

    fun clear() {
        val method = rwMethod ?: run { isActive = false; return }
        val mgr    = instance  ?: run { isActive = false; return }
        try { method.invoke(mgr, ACTION_CLEAR, buildJson(0, emptyList(), 0, 0, 0)) }
        catch (e: Exception) { /* swallow */ }
        isActive = false
    }

    private fun buildJson(status: Int, buttons: List<ButtonConfig>,
                          screenW: Int, gameH: Int, panelH: Int): String {
        val params = StringBuilder()
        var first = true
        for (btn in buttons.filter { it.isVisible }) {
            val (l, t, w, h) = panelBounds(btn, screenW, gameH, panelH)
            val r = l + w; val b = t + h
            val inputType = if (btn.type == ButtonType.TAP) 1 else 2
            val typeParam = inputType or 4   // inDisplayId=0

            val pointParam = if (btn.type == ButtonType.TAP) {
                if (btn.targetX < 0 || btn.targetY < 0) continue
                "[${btn.targetX}, ${btn.targetY}, $l, $t, $r, $b]"
            } else {
                val cx = (l + r) / 2; val cy = (t + b) / 2
                val gx = btn.targetX.takeIf { it >= 0 } ?: (screenW / 2)
                val gy = btn.targetY.takeIf { it >= 0 } ?: (gameH / 2)
                "[${gx - cx}, ${gy - cy}, $l, $t, $r, $b]"
            }

            if (!first) params.append(',')
            params.append("""{"typeParam":$typeParam,"pointParam":"$pointParam"}""")
            first = false
        }
        return """{"status":$status,"param":[$params]}"""
    }

    private fun panelBounds(btn: ButtonConfig, screenW: Int, gameH: Int, panelH: Int): IntArray {
        val sz = (btn.size * panelH).toInt().coerceAtLeast(40)
        val cx = (btn.panelX * screenW).toInt()
        val cy = (gameH + btn.panelY * panelH).toInt()
        return intArrayOf((cx - sz / 2).coerceAtLeast(0), (cy - sz / 2).coerceAtLeast(0), sz, sz)
    }
}
