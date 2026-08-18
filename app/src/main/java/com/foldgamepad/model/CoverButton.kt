package com.foldgamepad.model

import kotlinx.serialization.Serializable

/**
 * A resizable tap zone shown on the cover (outer) screen. Pressing it fires a
 * tap at (targetX, targetY) on the INNER screen, letting you use the cover
 * screen edges as blind left/right trigger buttons while holding the phone folded.
 */
@Serializable
data class CoverButton(
    val id: String,
    val label: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val targetX: Int = -1,
    val targetY: Int = -1
)

@Serializable
data class CoverLayout(
    val buttons: MutableList<CoverButton> = mutableListOf(
        CoverButton("trig_l", "L", x = 0.0f,  y = 0.30f, w = 0.18f, h = 0.40f),
        CoverButton("trig_r", "R", x = 0.82f, y = 0.30f, w = 0.18f, h = 0.40f)
    )
)
