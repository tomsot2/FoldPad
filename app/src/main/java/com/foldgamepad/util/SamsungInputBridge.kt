package com.foldgamepad.util

import android.util.Log
import com.foldgamepad.model.ButtonConfig
import com.foldgamepad.model.ButtonType

/**
 * Attempts to use Samsung's SemGameManager.requestWithJson("set_input_redirection") API
 * to register a native touch-remap table with the system.
 *
 * When active, the system intercepts taps on the panel area and remaps them to game
 * coordinates directly — no AccessibilityService round-trip, lower latency, harder for
 * games to block.
 *
 * Discovered by analysing Game Booster APK (B7/e class, GMSFilterData, set_input_redirection).
 *
 * Falls back silently to AccessibilityService path if unavailable or permission-denied.
 */
object SamsungInputBridge {

    private const val TAG = "SamsungInputBridge"
    private const val METHOD_REQUEST = "requestWithJson"
    private const val CMD_SET        = "set_input_redirection"
    private const val CMD_CLEAR      = "set_input_redirection"

    // Tri-state: null = not yet tested, true = works, false = unavailable
    @Volatile private var available: Boolean? = null

    val isAvailable: Boolean
        get() {
            available?.let { return it }
            available = probe()
            return available!!
        }

    /** True if the bridge successfully registered the last mapping. */
    @Volatile var isActive: Boolean = false
        private set

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Register a native input-remap table from [buttons].
     * Call whenever the overlay layout changes.
     * Returns true if Samsung accepted the mapping.
     */
    fun applyMapping(
        buttons: List<ButtonConfig>,
        screenW: Int,
        gameH: Int,
        panelH: Int
    ): Boolean {
        if (!isAvailable) return false
        val json = buildJson(buttons, screenW, gameH, panelH)
        return invoke(CMD_SET, json).also { ok ->
            isActive = ok
            if (ok) Log.d(TAG, "Input redirection applied (${buttons.count { it.targetX >= 0 }} buttons)")
            else    Log.d(TAG, "Input redirection rejected — using AccessibilityService fallback")
        }
    }

    /**
     * Clear the remap table (e.g. when overlay is destroyed).
     */
    fun clearMapping(): Boolean {
        if (!isAvailable) return false
        val json = """{"type":0,"items":[]}"""
        return invoke(CMD_CLEAR, json).also { isActive = false }
    }

    // ── JSON builder ────────────────────────────────────────────────────────

    /**
     * Builds the GMSFilterData JSON that Game Booster's B7/e.a() sends.
     * Only includes tap buttons that have a target coordinate set.
     *
     * Format (per item):
     *   name        – button id
     *   inDisplayId – display the button lives on (0 = same display as game)
     *   srcMaintain – always 1 (observed in Game Booster)
     *   inputType   – 4 = simple tap
     *   x / y       – centre of button in panel (absolute screen pixels)
     *   l / t / w / h – button bounding box in panel
     *   mappingX/Y  – where to inject the touch in game area
     */
    private fun buildJson(
        buttons: List<ButtonConfig>,
        screenW: Int,
        gameH: Int,
        panelH: Int
    ): String {
        val items = buttons
            .filter { it.isVisible && it.type == ButtonType.TAP && it.targetX >= 0 && it.targetY >= 0 }
            .joinToString(",") { btn ->
                val cx     = (btn.panelX * screenW).toInt()
                val cy     = gameH + (btn.panelY * panelH).toInt()
                val sizePx = (btn.size * panelH).toInt().coerceAtLeast(60)
                val r      = sizePx / 2
                """{"name":"${btn.id}","inDisplayId":0,"srcMaintain":1,"inputType":4,""" +
                """"x":$cx,"y":$cy,"l":${cx - r},"t":${cy - r},"w":$sizePx,"h":$sizePx,""" +
                """"mappingX":${btn.targetX},"mappingY":${btn.targetY}}"""
            }
        return """{"type":1,"items":[$items]}"""
    }

    // ── Reflection helpers ──────────────────────────────────────────────────

    private fun probe(): Boolean = try {
        val cls = Class.forName("com.samsung.android.game.SemGameManager")
        cls.getMethod(METHOD_REQUEST, String::class.java, String::class.java)
        Log.d(TAG, "SemGameManager.requestWithJson found — Samsung native bridge available")
        true
    } catch (e: Exception) {
        Log.d(TAG, "SemGameManager not available: ${e.message}")
        false
    }

    private fun invoke(command: String, json: String): Boolean = try {
        val cls    = Class.forName("com.samsung.android.game.SemGameManager")
        val mgr    = cls.newInstance()
        val method = cls.getMethod(METHOD_REQUEST, String::class.java, String::class.java)
        method.invoke(mgr, command, json)
        true
    } catch (e: Exception) {
        Log.w(TAG, "requestWithJson failed: ${e.message}")
        available = false
        false
    }
}
