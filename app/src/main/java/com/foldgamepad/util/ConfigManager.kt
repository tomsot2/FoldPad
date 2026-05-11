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
        return try {
            json.decodeFromString(raw)
        } catch (e: Exception) {
            buildDefault()
        }
    }

    // Default layout: ABXY face cluster, L1/R1, two joysticks
    private fun buildDefault(): LayoutConfig {
        val btns = mutableListOf(
            // Left joystick
            ButtonConfig("joy_l",  "L",  ButtonType.JOYSTICK, panelX = 0.13f, panelY = 0.52f, size = 0.26f),
            // Right joystick
            ButtonConfig("joy_r",  "R",  ButtonType.JOYSTICK, panelX = 0.65f, panelY = 0.52f, size = 0.26f),
            // Face buttons (ABXY cluster, right side)
            ButtonConfig("btn_a",  "A",  ButtonType.TAP,      panelX = 0.87f, panelY = 0.62f, size = 0.14f),
            ButtonConfig("btn_b",  "B",  ButtonType.TAP,      panelX = 0.93f, panelY = 0.35f, size = 0.14f),
            ButtonConfig("btn_x",  "X",  ButtonType.TAP,      panelX = 0.80f, panelY = 0.35f, size = 0.14f),
            ButtonConfig("btn_y",  "Y",  ButtonType.TAP,      panelX = 0.87f, panelY = 0.10f, size = 0.14f),
            // Shoulder buttons
            ButtonConfig("btn_l1", "L1", ButtonType.TAP,      panelX = 0.06f, panelY = 0.10f, size = 0.13f),
            ButtonConfig("btn_r1", "R1", ButtonType.TAP,      panelX = 0.94f, panelY = 0.10f, size = 0.13f),
            // D-pad
            ButtonConfig("btn_du", "↑",  ButtonType.TAP,      panelX = 0.34f, panelY = 0.18f, size = 0.11f),
            ButtonConfig("btn_dd", "↓",  ButtonType.TAP,      panelX = 0.34f, panelY = 0.60f, size = 0.11f),
            ButtonConfig("btn_dl", "←",  ButtonType.TAP,      panelX = 0.27f, panelY = 0.38f, size = 0.11f),
            ButtonConfig("btn_dr", "→",  ButtonType.TAP,      panelX = 0.41f, panelY = 0.38f, size = 0.11f),
            // Start / Select
            ButtonConfig("btn_start",  "▶", ButtonType.TAP,   panelX = 0.55f, panelY = 0.25f, size = 0.10f),
            ButtonConfig("btn_select", "⏸", ButtonType.TAP,   panelX = 0.46f, panelY = 0.25f, size = 0.10f),
        )
        return LayoutConfig(buttons = btns)
    }
}
