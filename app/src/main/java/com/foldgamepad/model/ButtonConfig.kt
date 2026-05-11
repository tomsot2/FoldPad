package com.foldgamepad.model

import kotlinx.serialization.Serializable

enum class ButtonType { TAP, JOYSTICK }

@Serializable
data class ButtonConfig(
    val id: String,
    val label: String,
    val type: ButtonType,
    // Position in the panel (0.0–1.0, relative to panel width/height)
    val panelX: Float = 0.5f,
    val panelY: Float = 0.5f,
    // Size as fraction of panel height
    val size: Float = 0.18f,
    // Where taps/gestures are injected on the game screen (absolute pixels, -1 = not set)
    val targetX: Int = -1,
    val targetY: Int = -1,
    // How wide (in px) the joystick movement maps to on the game screen
    val joystickGameRadius: Int = 200,
    val isVisible: Boolean = true
)

@Serializable
data class LayoutConfig(
    val buttons: MutableList<ButtonConfig> = mutableListOf(),
    val gamePackage: String = "",
    val gameName: String = "",
    // Outer-screen / cover-mode L2 and R2 targets
    val l2TargetX: Int = -1,
    val l2TargetY: Int = -1,
    val r2TargetX: Int = -1,
    val r2TargetY: Int = -1
)
