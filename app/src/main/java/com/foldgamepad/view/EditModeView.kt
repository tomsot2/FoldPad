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
 *  ×  (top-right, red)        → delete
 *  ⇲  (bottom-right, yellow)  → drag to resize
 *  STK/SWP (top-left, joystick only) → tap to toggle stick/swipe-pad mode
 *
 * Tap a button (no drag) → cycles through preset labels.
 * Drag a button up into the GAME AREA → sets its tap target.
 * Drag within the PANEL AREA → repositions it.
 *
 * BUTTON DRAWER (bottom-centre): two slots — TAP and JOY. Press a slot and
 * drag away to spawn a new button of that type at the drop position.
 *
 * "✓ DONE" at top centre saves and exits.
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

    private val presetLabels = listOf(
        "1","2","3","4","5","6","7","8",
        "L","R","↑","↓","←","→","A","B","X","Y","L1","R1","▶","⏸"
    )

    private enum class DragType { NONE, MOVE, RESIZE, SPAWN }
    private var dragType         = DragType.NONE
    private var dragIdx          = -1
    private var spawnType: ButtonType? = null
    private var dragX            = 0f
    private var dragY            = 0f
    private var resizeStartSize  = 0f
    private var resizeStartDist  = 0f

    private val dimP        = Paint().apply { style = Paint.Style.FILL; color = Color.argb(160, 0, 0, 0) }
    private val divP        = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(180, 0, 210, 255)
        pathEffect = DashPathEffect(floatArrayOf(20f, 12f), 0f)
    }
    private val btnFill     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(210, 0, 150, 200) }
    private val btnFillDrag = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(240, 255, 160, 0) }
    private val btnStroke   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.argb(255, 0, 220, 255) }
    private val joyRing     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.argb(200, 0, 220, 255) }
    private val textP       = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val labelP      = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 255, 255, 255); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val tgtP        = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(200, 255, 80, 80) }
    private val tgtFill     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(120, 255, 80, 80) }
    private val lineP       = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(160, 255, 200, 0)
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val doneP       = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(230, 0, 180, 80) }
    private val doneStroke  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.WHITE }
    private val deletePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(220, 210, 40, 40) }
    private val resizePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(220, 210, 180, 0) }
    private val modeBadgeP  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(220, 0, 140, 210) }
    private val drawerBg    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(220, 18, 22, 32) }
    private val drawerEdge  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(200, 0, 180, 230) }
    private val drawerHint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 180, 220, 255); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; textSize = 16f }
    private val badgeTextP  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }

    // ── Drawer geometry (computed from current size) ──────────────────────────

    private val drawerW = 280f
    private val drawerH = 90f
    private val slotR   = 32f

    private fun drawerRect(): RectF {
        val cx = width / 2f
        val top = (height - drawerH - 16f)
        return RectF(cx - drawerW / 2f, top, cx + drawerW / 2f, top + drawerH)
    }

    private fun tapSlotCentre(): Pair<Float, Float> {
        val r = drawerRect()
        return r.centerX() - 60f to r.centerY()
    }

    private fun joySlotCentre(): Pair<Float, Float> {
        val r = drawerRect()
        return r.centerX() + 60f to r.centerY()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimP)
        canvas.drawLine(0f, gameH.toFloat(), width.toFloat(), gameH.toFloat(), divP)

        labelP.textSize = 26f
        canvas.drawText("GAME AREA — drag button here to set tap target", width / 2f, (gameH / 2f), labelP)
        labelP.textSize = 22f
        canvas.drawText("BUTTON PANEL — drag to move | ⇲ to resize | × to delete",
            width / 2f, gameH + 40f, labelP)

        cfg.buttons.forEachIndexed { i, btn ->
            if (i != dragIdx && btn.targetX >= 0 && btn.targetY >= 0)
                drawTarget(canvas, btn.targetX.toFloat(), btn.targetY.toFloat(), btn.label)
        }

        cfg.buttons.forEachIndexed { i, btn ->
            if (i != dragIdx) {
                val (bx, by) = panelCoords(btn)
                drawButton(canvas, bx, by, btnRadius(btn), btn, dragging = false)
            }
        }

        if (dragIdx >= 0 && dragType == DragType.MOVE) {
            val btn = cfg.buttons[dragIdx]
            val (ox, oy) = panelCoords(btn)
            canvas.drawLine(ox, oy, dragX, dragY, lineP)
            drawButton(canvas, dragX, dragY, btnRadius(btn), btn, dragging = true)
            if (dragY < gameH) drawTarget(canvas, dragX, dragY, btn.label, live = true)
        }

        drawDrawer(canvas)

        if (dragType == DragType.SPAWN && spawnType != null) {
            val ghost = ButtonConfig(
                id = "preview", label = if (spawnType == ButtonType.TAP) "?" else "JOY",
                type = spawnType!!, panelX = 0.5f, panelY = 0.5f, size = 0.15f
            )
            drawButton(canvas, dragX, dragY, btnRadius(ghost), ghost, dragging = true)
        }

        drawDoneBtn(canvas)
    }

    private fun drawDrawer(canvas: Canvas) {
        val r = drawerRect()
        canvas.drawRoundRect(r, 18f, 18f, drawerBg)
        canvas.drawRoundRect(r, 18f, 18f, drawerEdge)

        canvas.drawText("⇡ drag a button up to spawn", r.centerX(), r.top + 18f, drawerHint)

        val (tx, ty) = tapSlotCentre()
        canvas.drawCircle(tx, ty + 6f, slotR, btnFill)
        canvas.drawCircle(tx, ty + 6f, slotR, btnStroke)
        textP.textSize = slotR * 0.55f
        textP.color = Color.WHITE
        canvas.drawText("TAP", tx, ty + 6f + textP.textSize * 0.36f, textP)

        val (jx, jy) = joySlotCentre()
        canvas.drawCircle(jx, jy + 6f, slotR, btnFill)
        canvas.drawCircle(jx, jy + 6f, slotR, joyRing)
        canvas.drawCircle(jx, jy + 6f, slotR * 0.35f, btnFill)
        labelP.textSize = 16f
        canvas.drawText("JOY", jx, jy + slotR + 22f, labelP)
        canvas.drawText("TAP", tx, ty + slotR + 22f, labelP)
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
        textP.textSize = r * 0.46f; textP.color = Color.WHITE
        canvas.drawText(btn.label, cx, cy + textP.textSize * 0.36f, textP)

        if (!dragging) {
            val badgeR = (r * 0.28f).coerceAtLeast(14f)
            val bOff   = r * 0.72f

            canvas.drawCircle(cx + bOff, cy - bOff, badgeR, deletePaint)
            badgeTextP.textSize = badgeR * 1.0f
            canvas.drawText("×", cx + bOff, cy - bOff + badgeTextP.textSize * 0.36f, badgeTextP)

            canvas.drawCircle(cx + bOff, cy + bOff, badgeR, resizePaint)
            badgeTextP.textSize = badgeR * 0.8f
            canvas.drawText("⇲", cx + bOff, cy + bOff + badgeTextP.textSize * 0.36f, badgeTextP)

            if (btn.type == ButtonType.JOYSTICK) {
                canvas.drawCircle(cx - bOff, cy - bOff, badgeR, modeBadgeP)
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

    private fun drawDoneBtn(canvas: Canvas) {
        val cx = width / 2f; val cy = 50f; val rw = 170f; val rh = 36f
        val rect = RectF(cx - rw, cy - rh, cx + rw, cy + rh)
        canvas.drawRoundRect(rect, rh, rh, doneP)
        canvas.drawRoundRect(rect, rh, rh, doneStroke)
        textP.textSize = 26f; textP.color = Color.WHITE
        canvas.drawText("✓  DONE – tap to save", cx, cy + 9f, textP)
    }

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
        val dcx = width / 2f
        if (y in 14f..86f && x in (dcx - 170f)..(dcx + 170f)) {
            onSave(cfg); onDone(); return true
        }

        val (tx, ty) = tapSlotCentre()
        if (dist(x, y, tx, ty + 6f) < slotR * 1.3f) {
            spawnType = ButtonType.TAP; dragType = DragType.SPAWN
            dragX = x; dragY = y; invalidate(); return true
        }
        val (jx, jy) = joySlotCentre()
        if (dist(x, y, jx, jy + 6f) < slotR * 1.3f) {
            spawnType = ButtonType.JOYSTICK; dragType = DragType.SPAWN
            dragX = x; dragY = y; invalidate(); return true
        }

        cfg.buttons.forEachIndexed { i, btn ->
            val (bx, by) = panelCoords(btn)
            val r      = btnRadius(btn)
            val bOff   = r * 0.72f
            val badgeR = (r * 0.28f).coerceAtLeast(14f)

            if (dist(x, y, bx + bOff, by - bOff) < badgeR * 1.4f) {
                cfg.buttons.removeAt(i); onSave(cfg); invalidate(); return true
            }
            if (dist(x, y, bx + bOff, by + bOff) < badgeR * 1.4f) {
                dragIdx = i; dragType = DragType.RESIZE
                resizeStartSize = btn.size
                resizeStartDist = dist(x, y, bx, by)
                dragX = x; dragY = y; invalidate(); return true
            }
            if (btn.type == ButtonType.JOYSTICK &&
                dist(x, y, bx - bOff, by - bOff) < badgeR * 1.4f) {
                val newMode = if (btn.joystickMode == JoystickMode.STICK) JoystickMode.SWIPE_PAD else JoystickMode.STICK
                cfg.buttons[i] = btn.copy(joystickMode = newMode)
                onSave(cfg); invalidate(); return true
            }
            if (dist(x, y, bx, by) < r * 1.4f) {
                dragIdx = i; dragType = DragType.MOVE
                dragX = x; dragY = y; invalidate(); return true
            }
        }
        return false
    }

    private fun onMove(x: Float, y: Float): Boolean {
        if (dragType == DragType.NONE) return false
        dragX = x; dragY = y
        if (dragType == DragType.RESIZE && dragIdx >= 0) {
            val btn = cfg.buttons[dragIdx]
            val (bx, by) = panelCoords(btn)
            val scale = if (resizeStartDist > 0f) dist(x, y, bx, by) / resizeStartDist else 1f
            cfg.buttons[dragIdx] = btn.copy(size = (resizeStartSize * scale).coerceIn(0.05f, 0.45f))
            onSave(cfg)
        }
        invalidate(); return true
    }

    private fun onUp(x: Float, y: Float): Boolean {
        when (dragType) {
            DragType.SPAWN -> spawnType?.let { type ->
                if (!drawerRect().contains(x, y)) {
                    spawnAt(type, x, y)
                }
            }
            DragType.MOVE -> if (dragIdx >= 0) {
                val btn = cfg.buttons[dragIdx]
                val (origX, origY) = panelCoords(btn)
                val travelled = dist(x, y, origX, origY)
                cfg.buttons[dragIdx] = when {
                    y < gameH -> btn.copy(targetX = x.toInt(), targetY = y.toInt())
                    travelled < 20f -> {
                        val cur  = presetLabels.indexOf(btn.label)
                        val next = presetLabels[(cur + 1).rem(presetLabels.size)]
                        btn.copy(label = next)
                    }
                    else -> btn.copy(
                        panelX = (x / width).coerceIn(0.02f, 0.98f),
                        panelY = ((y - gameH) / panelH).coerceIn(0.02f, 0.98f)
                    )
                }
                onSave(cfg)
            }
            else -> {}
        }
        dragIdx   = -1
        dragType  = DragType.NONE
        spawnType = null
        invalidate(); return true
    }

    private fun spawnAt(type: ButtonType, x: Float, y: Float) {
        val id    = "btn_${System.currentTimeMillis()}"
        val label = if (type == ButtonType.TAP) nextTapLabel() else "JOY"
        val newBtn = if (y < gameH) {
            ButtonConfig(
                id = id, label = label, type = type,
                panelX = 0.5f, panelY = 0.5f, size = 0.15f,
                targetX = x.toInt(), targetY = y.toInt()
            )
        } else {
            ButtonConfig(
                id = id, label = label, type = type,
                panelX = (x / width).coerceIn(0.02f, 0.98f),
                panelY = ((y - gameH) / panelH).coerceIn(0.02f, 0.98f),
                size = 0.15f
            )
        }
        cfg.buttons.add(newBtn); onSave(cfg)
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
