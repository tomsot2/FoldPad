package com.foldgamepad.util

import android.content.Context
import com.foldgamepad.model.ButtonConfig
import com.foldgamepad.model.ButtonType
import com.foldgamepad.model.LayoutConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ConfigManager {

    private const val PREFS_NAME     = "foldgamepad_prefs"
    private const val KEY_DEFAULT    = "layout_default"
    private const val KEY_PKG_PREFIX = "layout_pkg_"
    private const val KEY_ACTIVE     = "active_package"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun save(context: Context, config: LayoutConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(slotKey(config.gamePackage), json.encodeToString(config))
            .putString(KEY_ACTIVE, config.gamePackage)
            .apply()
    }

    fun load(context: Context): LayoutConfig {
        val prefs  = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val active = prefs.getString(KEY_ACTIVE, "") ?: ""
        return loadForPackage(context, active)
    }

    fun loadForPackage(context: Context, packageName: String): LayoutConfig {
        val prefs  = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pkgRaw = if (packageName.isNotBlank()) prefs.getString(slotKey(packageName), null) else null
        val raw    = pkgRaw ?: prefs.getString(KEY_DEFAULT, null)
        val parsed = if (raw != null) {
            try { json.decodeFromString<LayoutConfig>(raw) } catch (e: Exception) { buildDefault() }
        } else buildDefault()
        return if (packageName.isNotBlank()) parsed.copy(gamePackage = packageName) else parsed
    }

    fun listSavedPackages(context: Context): List<String> =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).all.keys
            .filter { it.startsWith(KEY_PKG_PREFIX) }
            .map { it.removePrefix(KEY_PKG_PREFIX) }
            .sorted()

    fun deleteLayout(context: Context, packageName: String) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(slotKey(packageName)).apply()

    private fun slotKey(pkg: String) = if (pkg.isBlank()) KEY_DEFAULT else "$KEY_PKG_PREFIX$pkg"

    private fun buildDefault() = LayoutConfig(buttons = mutableListOf(
        ButtonConfig("joy_l", "JOY", ButtonType.JOYSTICK, panelX=0.15f, panelY=0.50f, size=0.30f),
        ButtonConfig("btn_1", "1",   ButtonType.TAP,      panelX=0.72f, panelY=0.25f, size=0.16f),
        ButtonConfig("btn_2", "2",   ButtonType.TAP,      panelX=0.87f, panelY=0.25f, size=0.16f),
        ButtonConfig("btn_3", "3",   ButtonType.TAP,      panelX=0.72f, panelY=0.72f, size=0.16f),
        ButtonConfig("btn_4", "4",   ButtonType.TAP,      panelX=0.87f, panelY=0.72f, size=0.16f),
        ButtonConfig("btn_l", "L",   ButtonType.TAP,      panelX=0.06f, panelY=0.12f, size=0.12f),
        ButtonConfig("btn_r", "R",   ButtonType.TAP,      panelX=0.94f, panelY=0.12f, size=0.12f),
    ))
}
