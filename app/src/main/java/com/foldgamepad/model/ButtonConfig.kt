package com.foldgamepad.model

import kotlinx.serialization.Serializable

enum class ButtonType   { TAP, JOYSTICK }
enum class JoystickMode { STICK, SWIPE_PAD }

@Serializable
data class ButtonConfig(
    val id: String,
    val label: String,
    val type: ButtonType,
    val panelX: Float = 0.5f,
    val panelY: Float = 0.5f,
    val size: Float = 0.18f,
    val targetX: Int = -1,
    val targetY: Int = -1,
    val joystickGameRadius: Int = 200,
    val joystickMode: JoystickMode = JoystickMode.STICK,
    val isVisible: Boolean = true
)

@Serializable
data class LayoutConfig(
    val buttons: MutableList<ButtonConfig> = mutableListOf(),
    val gamePackage: String = "",
    val gameName: String = "",
    val l2TargetX: Int = -1,
    val l2TargetY: Int = -1,
    val r2TargetX: Int = -1,
    val r2TargetY: Int = -1
)
