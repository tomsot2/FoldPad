package com.foldgamepad.view

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.foldgamepad.model.ButtonConfig
import com.foldgamepad.model.ButtonType
import com.foldgamepad.model.JoystickMode
import com.foldgamepad.model.LayoutConfig
import kotlin.math.*

/**
 * Full-screen edit overlay.
 *
 * Each button/joystick in the panel shows three interactive badges:
 *  ×  (top-right, red)    → delete
 *  ⇲  (bottom-right, yellow) → drag to resize
 *  ↔  (top-left, cyan, joystick only) → tap to toggle STICK / SWIPE_PAD mode
 *
 * Tap the label area of any button → cycles through preset labels.
 * Drag the button body upward into the GAME AREA → sets tap/gesture target.
 * Drag the button body within the PANEL AREA → repositions it.
 *
 * "+ TAP" / "+ JOY" buttons sit at the bottom of the panel.
 * "✓ DONE" button at top centre saves and exits.
 */
class EditModeView(
    context: Context,
    config: LayoutConfig,
    private val screenW: Int,
    private val screenH: Int,
    private val gameH: Int,
    private val panelH: Int,
    private val onSave: (LayoutConfig) -> Unit,
    private val onDone: () -> Unit
) : View(context) {

    private val cfg = config.copy(buttons = config.buttons.map { it }.toMutableList())

    // ── Preset labels (tap button label to cycle) ─────────────────────────────
    private val presetLabels = listOf(
        "A","B","X","Y","L1","L2","R1","R2",
        "↑","↓","←","→","▶","⏸","✕","○","△","□","ZL","ZR"
    )

    // ── Drag state ────────────────────────────────────────────────────────────
    private enum class DragType { NONE, MOVE, RESIZE }
    private var dragType  = DragType.NONE
    private var dragIdx   = -1
    private var dragX     = 0f
    private var dragY     = 0f
    // Starting size for resize
    private var resizeStartSize = 0f
    private var resizeStartDist = 0f

    // ── Paints ────────────────────────────────────────────────────────────────
    private val dimP         = Paint().apply { style = Paint.Style.FILL; color = Color.argb(160, 0, 0, 0) }
    private val divP         = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(180, 0, 210, 255)
        pathEffect = DashPathEffect(floatArrayOf(20f, 12f), 0f)
    }
    private val btnFill      = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(210, 0, 150, 200) }
    private val btnFillDrag  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(240, 255, 160, 0) }
    private val btnStroke    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.argb(255, 0, 220, 255) }
    private val joyRing      = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.argb(200, 0, 220, 255) }
    private val textP        = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val labelP       = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 255, 255, 255); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val tgtP         = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(200, 255, 80, 80) }
    private val tgtFill      = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(120, 255, 80, 80) }
    private val lineP        = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(160, 255, 200, 0)
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val doneP        = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(230, 0, 180, 80) }
    private val doneStroke   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.WHITE }
    private val deletePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(220, 210, 40, 40) }
    private val resizePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(220, 210, 180, 0) }
    private val modeBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(220, 0, 140, 210) }
    private val addBtnPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(200, 30, 120, 60) }
    private val addBtnStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(200, 80, 220, 120) }
    private val badgeTextP   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }

    // ── Draw ──────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        // Background dim
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimP)
        // Divider
        canvas.drawLine(0f, gameH.toFloat(), width.toFloat(), gameH.toFloat(), divP)

        // Zone labels
        labelP.textSize = 26f
        canvas.drawText("GAME AREA — drag button here to set tap target", width / 2f, (gameH / 2f), labelP)
        canvas.drawText("BUTTON PANEL — drag to move  |  ⇲ to resize  |  × to delete", width / 2f, (gameH + panelH / 2f), labelP)

        // Target crosshairs
        cfg.buttons.forEachIndexed { i, btn ->
            if (i != dragIdx && btn.targetX >= 0 && btn.targetY >= 0)
                drawTarget(canvas, btn.targetX.toFloat(), btn.targetY.toFloat(), btn.label)
        }

        // Buttons (skip dragging one)
        cfg.buttons.forEachIndexed { i, btn ->
            if (i != dragIdx) {
                val (bx, by) = panelCoords(btn)
                drawButton(canvas, bx, by, btnRadius(btn), btn, dragging = false)
            }
        }

        // Dragging button on top
        if (dragIdx >= 0 && dragType == DragType.MOVE) {
            val btn = cfg.buttons[dragIdx]
            val (ox, oy) = panelCoords(btn)
            canvas.drawLine(ox, oy, dragX, dragY, lineP)
            drawButton(canvas, dragX, dragY, btnRadius(btn), btn, dragging = true)
            if (dragY < gameH) drawTarget(canvas, dragX, dragY, btn.label, live = true)
        }

        // Add buttons
        drawAddButton(canvas, "＋ TAP",  width * 0.18f, gameH + panelH - 38f)
        drawAddButton(canvas, "＋ JOY",  width * 0.50f, gameH + panelH - 38f)

        // Done button
        drawDoneBtn(canvas)
    }

    private fun drawButton(canvas: Canvas, cx: Float, cy: Float, r: Float, btn: ButtonConfig, dragging: Boolean) {
        val fill = if (dragging) btnFillDrag else btnFill
        when (btn.type) {
            ButtonType.TAP -> {
                canvas.drawCircle(cx, cy, r, fill)
                canvas.drawCircle(cx, cy, r, btnStroke)
            }
            ButtonType.JOYSTICK -> {
                canvas.drawCircle(cx, cy, r, fill)
                canvas.drawCircle(cx, cy, r, joyRing)
                canvas.drawCircle(cx, cy, r * 0.35f, btnFill)
            }
        }
        textP.textSize = r * 0.46f
        canvas.drawText(btn.label, cx, cy + textP.textSize * 0.36f, textP)

        if (!dragging) {
            val badgeR = (r * 0.28f).coerceAtLeast(14f)
            val bOff   = r * 0.72f

            // × delete badge (top-right)
            canvas.drawCircle(cx + bOff, cy - bOff, badgeR, deletePaint)
            badgeTextP.textSize = badgeR * 1.0f
            canvas.drawText("×", cx + bOff, cy - bOff + badgeTextP.textSize * 0.36f, badgeTextP)

            // ⇲ resize handle (bottom-right)
            canvas.drawCircle(cx + bOff, cy + bOff, badgeR, resizePaint)
            badgeTextP.textSize = badgeR * 0.8f
            canvas.drawText("⇲", cx + bOff, cy + bOff + badgeTextP.textSize * 0.36f, badgeTextP)

            // Mode badge (top-left, joystick only)
            if (btn.type == ButtonType.JOYSTICK) {
                canvas.drawCircle(cx - bOff, cy - bOff, badgeR, modeBadgePaint)
                badgeTextP.textSize = badgeR * 0.65f
                val modeLabel = if (btn.joystickMode == JoystickMode.STICK) "STK" else "SWP"
                canvas.drawText(modeLabel, cx - bOff, cy - bOff + badgeTextP.textSize * 0.36f, badgeTextP)
            }
        }
    }

    private fun drawTarget(canvas: Canvas, tx: Float, ty: Float, label: String, live: Boolean = false) {
        val r = 22f
        canvas.drawCircle(tx, ty, r, tgtFill); canvas.drawCircle(tx, ty, r, tgtP)
        canvas.drawLine(tx - r * 1.8f, ty, tx + r * 1.8f, ty, tgtP)
        canvas.drawLine(tx, ty - r * 1.8f, tx, ty + r * 1.8f, tgtP)
        textP.textSize = 18f
        textP.color = if (live) Color.YELLOW else Color.argb(200, 255, 100, 100)
        canvas.drawText(label, tx, ty - r * 2.2f, textP)
        textP.color = Color.WHITE
    }

    private fun drawAddButton(canvas: Canvas, label: String, cx: Float, cy: Float) {
        val rw = 130f; val rh = 28f
        val rect = RectF(cx - rw, cy - rh, cx + rw, cy + rh)
        canvas.drawRoundRect(rect, rh, rh, addBtnPaint)
        canvas.drawRoundRect(rect, rh, rh, addBtnStroke)
        textP.textSize = 22f; textP.color = Color.WHITE
        canvas.drawText(label, cx, cy + 8f, textP)
    }

    private fun drawDoneBtn(canvas: Canvas) {
        val cx = width / 2f; val cy = 50f; val rw = 170f; val rh = 36f
        val rect = RectF(cx - rw, cy - rh, cx + rw, cy + rh)
        canvas.drawRoundRect(rect, rh, rh, doneP)
        canvas.drawRoundRect(rect, rh, rh, doneStroke)
        textP.textSize = 26f; textP.color = Color.WHITE
        canvas.drawText("✓  DONE – tap to save", cx, cy + 9f, textP)
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> onDown(x, y)
            MotionEvent.ACTION_MOVE -> onMove(x, y)
            MotionEvent.ACTION_UP   -> onUp(x, y)
            else -> false
        }
    }

    private fun onDown(x: Float, y: Float): Boolean {
        // Done button
        val dcx = width / 2f
        if (y in 14f..86f && x in (dcx - 170f)..(dcx + 170f)) {
            onSave(cfg); onDone(); return true
        }

        // "+ TAP" button
        val addTapCx = width * 0.18f; val addCy = gameH + panelH - 38f
        if (abs(x - addTapCx) < 130f && abs(y - addCy) < 28f) {
            addButton(ButtonType.TAP); return true
        }

        // "+ JOY" button
        val addJoyCx = width * 0.50f
        if (abs(x - addJoyCx) < 130f && abs(y - addCy) < 28f) {
            addButton(ButtonType.JOYSTICK); return true
        }

        // Badge / button body hits
        cfg.buttons.forEachIndexed { i, btn ->
            val (bx, by) = panelCoords(btn)
            val r     = btnRadius(btn)
            val bOff  = r * 0.72f
            val badgeR = (r * 0.28f).coerceAtLeast(14f)

            // × delete badge
            if (dist(x, y, bx + bOff, by - bOff) < badgeR * 1.4f) {
                cfg.buttons.removeAt(i); onSave(cfg); invalidate(); return true
            }
            // ⇲ resize handle
            if (dist(x, y, bx + bOff, by + bOff) < badgeR * 1.4f) {
                dragIdx = i; dragType = DragType.RESIZE
                resizeStartSize = btn.size
                resizeStartDist = dist(x, y, bx, by)
                dragX = x; dragY = y; invalidate(); return true
            }
            // Mode badge (joystick only)
            if (btn.type == ButtonType.JOYSTICK &&
                dist(x, y, bx - bOff, by - bOff) < badgeR * 1.4f) {
                val newMode = if (btn.joystickMode == JoystickMode.STICK) JoystickMode.SWIPE_PAD else JoystickMode.STICK
                cfg.buttons[i] = btn.copy(joystickMode = newMode)
                onSave(cfg); invalidate(); return true
            }
            // Button label tap (cycle label) — only if tap, not drag; decided in onUp
            // Button body drag
            if (dist(x, y, bx, by) < r * 1.4f) {
                dragIdx = i; dragType = DragType.MOVE
                dragX = x; dragY = y; invalidate(); return true
            }
        }
        return false
    }

    private fun onMove(x: Float, y: Float): Boolean {
        if (dragIdx < 0) return false
        dragX = x; dragY = y
        if (dragType == DragType.RESIZE) {
            val btn = cfg.buttons[dragIdx]
            val (bx, by) = panelCoords(btn)
            val currentDist = dist(x, y, bx, by)
            val scale = if (resizeStartDist > 0f) currentDist / resizeStartDist else 1f
            val newSize = (resizeStartSize * scale).coerceIn(0.05f, 0.45f)
            cfg.buttons[dragIdx] = btn.copy(size = newSize)
            onSave(cfg)
        }
        invalidate(); return true
    }

    private fun onUp(x: Float, y: Float): Boolean {
        if (dragIdx >= 0 && dragType == DragType.MOVE) {
            val btn = cfg.buttons[dragIdx]
            val (origX, origY) = panelCoords(btn)
            val travelled = dist(x, y, origX, origY)

            cfg.buttons[dragIdx] = when {
                // Dropped in game area → set as target
                y < gameH -> btn.copy(targetX = x.toInt(), targetY = y.toInt())
                // Tiny movement → cycle label
                travelled < 20f -> {
                    val cur = presetLabels.indexOf(btn.label)
                    val next = presetLabels[(cur + 1).rem(presetLabels.size)]
                    btn.copy(label = next)
                }
                // Moved within panel → reposition
                else -> btn.copy(
                    panelX = (x / width).coerceIn(0.02f, 0.98f),
                    panelY = ((y - gameH) / panelH).coerceIn(0.02f, 0.98f)
                )
            }
            onSave(cfg)
        }
        dragIdx  = -1
        dragType = DragType.NONE
        invalidate(); return true
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun addButton(type: ButtonType) {
        val id    = "btn_${System.currentTimeMillis()}"
        val label = if (type == ButtonType.TAP) nextTapLabel() else "JOY"
        val btn   = ButtonConfig(
            id = id, label = label, type = type,
            panelX = 0.5f, panelY = 0.5f, size = 0.15f
        )
        cfg.buttons.add(btn)
        onSave(cfg); invalidate()
    }

    private fun nextTapLabel(): String {
        val used = cfg.buttons.map { it.label }.toSet()
        return presetLabels.firstOrNull { it !in used } ?: "BTN"
    }

    private fun panelCoords(btn: ButtonConfig): Pair<Float, Float> =
        (btn.panelX * width) to (gameH + btn.panelY * panelH)

    private fun btnRadius(btn: ButtonConfig): Float =
        (btn.size * panelH / 2f).coerceAtLeast(28f)

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float) =
        sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))
}
