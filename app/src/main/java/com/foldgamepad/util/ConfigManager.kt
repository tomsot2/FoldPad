package com.foldgamepad.util

import android.content.Context
import com.foldgamepad.model.ButtonConfig
import com.foldgamepad.model.ButtonType
import com.foldgamepad.model.LayoutConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Per-package layout storage.
 *
 *   layout_default                  → fallback when no game selected
 *   layout_pkg_<packageName>        → saved layout for that specific game
 *   active_package                  → which package's layout to load by default
 *
 * Each game automatically gets its own button positions, targets, and joystick
 * setup. Switching games via the picker loads that game's layout; if it has
 * none, the default layout is duplicated for it.
 */
object ConfigManager {

    private const val PREFS_NAME    = "foldgamepad_prefs"
    private const val KEY_DEFAULT   = "layout_default"
    private const val KEY_PKG_PREFIX = "layout_pkg_"
    private const val KEY_ACTIVE    = "active_package"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ── Save ───────────────────────────────────────────────────────────────────

    /**
     * Save [config] under the slot determined by its [LayoutConfig.gamePackage].
     * Blank package → default slot. Also marks this package as active.
     */
    fun save(context: Context, config: LayoutConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val slot  = slotKey(config.gamePackage)
        prefs.edit()
            .putString(slot, json.encodeToString(config))
            .putString(KEY_ACTIVE, config.gamePackage)
            .apply()
    }

    // ── Load ───────────────────────────────────────────────────────────────────

    /**
     * Load the currently active layout. If no active package is set, returns
     * the default layout (or built-in default if neither exists yet).
     */
    fun load(context: Context): LayoutConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val active = prefs.getString(KEY_ACTIVE, "") ?: ""
        return loadForPackage(context, active)
    }

    /**
     * Load the layout saved for [packageName]. If none exists, falls back to
     * the default layout, then to the built-in default. Always stamps the
     * returned config with [packageName] so subsequent saves go to the right slot.
     */
    fun loadForPackage(context: Context, packageName: String): LayoutConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val pkgRaw = if (packageName.isNotBlank())
            prefs.getString(slotKey(packageName), null) else null

        val raw = pkgRaw ?: prefs.getString(KEY_DEFAULT, null)

        val parsed = if (raw != null) {
            try { json.decodeFromString<LayoutConfig>(raw) }
            catch (e: Exception) { buildDefault() }
        } else buildDefault()

        return if (packageName.isNotBlank())
            parsed.copy(gamePackage = packageName)
        else parsed
    }

    // ── List & delete ──────────────────────────────────────────────────────────

    /** Returns list of game package names that have a saved layout. */
    fun listSavedPackages(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.all.keys
            .filter { it.startsWith(KEY_PKG_PREFIX) }
            .map { it.removePrefix(KEY_PKG_PREFIX) }
            .sorted()
    }

    fun deleteLayout(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(slotKey(packageName)).apply()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun slotKey(packageName: String): String =
        if (packageName.isBlank()) KEY_DEFAULT else "$KEY_PKG_PREFIX$packageName"

    /**
     * Built-in default layout — Game Booster style.
     */
    private fun buildDefault(): LayoutConfig {
        val btns = mutableListOf(
            ButtonConfig("joy_l", "JOY", ButtonType.JOYSTICK, panelX = 0.15f, panelY = 0.50f, size = 0.30f),
            ButtonConfig("btn_1", "1",   ButtonType.TAP,      panelX = 0.72f, panelY = 0.25f, size = 0.16f),
            ButtonConfig("btn_2", "2",   ButtonType.TAP,      panelX = 0.87f, panelY = 0.25f, size = 0.16f),
            ButtonConfig("btn_3", "3",   ButtonType.TAP,      panelX = 0.72f, panelY = 0.72f, size = 0.16f),
            ButtonConfig("btn_4", "4",   ButtonType.TAP,      panelX = 0.87f, panelY = 0.72f, size = 0.16f),
            ButtonConfig("btn_l", "L",   ButtonType.TAP,      panelX = 0.06f, panelY = 0.12f, size = 0.12f),
            ButtonConfig("btn_r", "R",   ButtonType.TAP,      panelX = 0.94f, panelY = 0.12f, size = 0.12f),
        )
        return LayoutConfig(buttons = btns)
    }
}
