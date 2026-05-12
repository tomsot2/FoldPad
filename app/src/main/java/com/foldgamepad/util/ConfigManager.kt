package com.foldgamepad.util

import android.content.Context
import com.foldgamepad.model.ButtonConfig
import com.foldgamepad.model.ButtonType
import com.foldgamepad.model.LayoutConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ConfigManager {

    private const val PREFS_NAME = "foldgamepad_prefs"
    private const val KEY_LAYOUT  = "layout_v1"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun save(context: Context, config: LayoutConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAYOUT, json.encodeToString(config))
            .apply()
    }

    fun load(context: Context): LayoutConfig {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAYOUT, null) ?: return buildDefault()
        return try { json.decodeFromString(raw) } catch (e: Exception) { buildDefault() }
    }

    /**
     * Default layout mirrors Game Booster's default virtual keypad:
     *  - One joystick on the left (like Game Booster's "Center L")
     *  - Four numbered action buttons on the right (userKey_1 through userKey_4)
     * Labels are numbers rather than controller letters — simpler and game-agnostic.
     */
    private fun buildDefault(): LayoutConfig {
        val btns = mutableListOf(
            // Joystick — left side, Game Booster "Center L" equivalent
            ButtonConfig("joy_l", "JOY", ButtonType.JOYSTICK, panelX = 0.15f, panelY = 0.50f, size = 0.30f),

            // Numbered action buttons — right cluster, Game Booster userKey_1..4
            ButtonConfig("btn_1", "1",   ButtonType.TAP, panelX = 0.72f, panelY = 0.25f, size = 0.16f),
            ButtonConfig("btn_2", "2",   ButtonType.TAP, panelX = 0.87f, panelY = 0.25f, size = 0.16f),
            ButtonConfig("btn_3", "3",   ButtonType.TAP, panelX = 0.72f, panelY = 0.72f, size = 0.16f),
            ButtonConfig("btn_4", "4",   ButtonType.TAP, panelX = 0.87f, panelY = 0.72f, size = 0.16f),

            // Shoulder buttons — top row
            ButtonConfig("btn_l", "L",   ButtonType.TAP, panelX = 0.06f, panelY = 0.12f, size = 0.12f),
            ButtonConfig("btn_r", "R",   ButtonType.TAP, panelX = 0.94f, panelY = 0.12f, size = 0.12f),
        )
        return LayoutConfig(buttons = btns)
    }
}
