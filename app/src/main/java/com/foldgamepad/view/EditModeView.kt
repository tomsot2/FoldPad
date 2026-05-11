package com.foldgamepad.view

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.foldgamepad.model.ButtonConfig
import com.foldgamepad.model.ButtonType
import com.foldgamepad.model.LayoutConfig
import kotlin.math.*

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

    private var dragIdx = -1
    private var dragX   = 0f
    private var dragY   = 0f

    private val dimPaint      = Paint().apply { style = Paint.Style.FILL; color = Color.argb(160, 0, 0, 0) }
    private val dividerPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(180, 0, 210, 255)
        pathEffect = DashPathEffect(floatArrayOf(20f, 12f), 0f)
    }
    private val btnFill       = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(210, 0, 150, 200) }
    private val btnFillDrag   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(240, 255, 160, 0) }
    private val btnStroke     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.argb(255, 0, 220, 255) }
    private val joystickRing  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.argb(200, 0, 220, 255) }
    private val textPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val labelPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 255, 255, 255); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val targetPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(200, 255, 80, 80) }
    private val targetFill    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL;   color = Color.argb(120, 255, 80, 80) }
    private val linePaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(160, 255, 200, 0)
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val donePaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL;   color = Color.argb(230, 0, 180, 80) }
    private val doneStroke    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.WHITE }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawLine(0f, gameH.toFloat(), width.toFloat(), gameH.toFloat(), dividerPaint)

        labelPaint.textSize = 28f
        canvas.drawText("▲  GAME AREA  – drag buttons here to set tap target", width / 2f, gameH / 2f, labelPaint)
        canvas.drawText("▼  BUTTON PANEL  – drag within here to reposition",  width / 2f, (gameH + screenH) / 2f, labelPaint)

        cfg.buttons.forEachIndexed { i, btn ->
            if (i != dragIdx && btn.targetX >= 0 && btn.targetY >= 0)
                drawTarget(canvas, btn.targetX.toFloat(), btn.targetY.toFloat(), btn.label)
        }
        cfg.buttons.forEachIndexed { i, btn ->
            if (i != dragIdx) { val (bx, by) = panelCoords(btn); drawBtn(canvas, bx, by, btnRadius(btn), btn, false) }
        }
        if (dragIdx >= 0) {
            val btn = cfg.buttons[dragIdx]
            val (ox, oy) = panelCoords(btn)
            canvas.drawLine(ox, oy, dragX, dragY, linePaint)
            drawBtn(canvas, dragX, dragY, btnRadius(btn), btn, true)
            if (dragY < gameH) drawTarget(canvas, dragX, dragY, btn.label, live = true)
        }
        drawDoneBtn(canvas)
    }

    private fun drawBtn(canvas: Canvas, cx: Float, cy: Float, r: Float, btn: ButtonConfig, dragging: Boolean) {
        val fill = if (dragging) btnFillDrag else btnFill
        when (btn.type) {
            ButtonType.TAP -> { canvas.drawCircle(cx, cy, r, fill); canvas.drawCircle(cx, cy, r, btnStroke) }
            ButtonType.JOYSTICK -> { canvas.drawCircle(cx, cy, r, fill); canvas.drawCircle(cx, cy, r, joystickRing); canvas.drawCircle(cx, cy, r * 0.35f, btnFill) }
        }
        textPaint.textSize = r * 0.48f
        canvas.drawText(btn.label, cx, cy + textPaint.textSize * 0.38f, textPaint)
    }

    private fun drawTarget(canvas: Canvas, tx: Float, ty: Float, label: String, live: Boolean = false) {
        val r = 22f
        canvas.drawCircle(tx, ty, r, targetFill); canvas.drawCircle(tx, ty, r, targetPaint)
        canvas.drawLine(tx - r * 1.8f, ty, tx + r * 1.8f, ty, targetPaint)
        canvas.drawLine(tx, ty - r * 1.8f, tx, ty + r * 1.8f, targetPaint)
        textPaint.textSize = 18f
        textPaint.color = if (live) Color.YELLOW else Color.argb(200, 255, 100, 100)
        canvas.drawText(label, tx, ty - r * 2.2f, textPaint)
        textPaint.color = Color.WHITE
    }

    private fun drawDoneBtn(canvas: Canvas) {
        val cx = width / 2f; val cy = 52f; val rw = 160f; val rh = 38f
        val rect = RectF(cx - rw, cy - rh, cx + rw, cy + rh)
        canvas.drawRoundRect(rect, rh, rh, donePaint); canvas.drawRoundRect(rect, rh, rh, doneStroke)
        textPaint.textSize = 28f
        canvas.drawText("✓  DONE – tap to save", cx, cy + 10f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (y in 14f..90f && x in (width / 2f - 160f)..(width / 2f + 160f)) { onSave(cfg); onDone(); return true }
                dragIdx = cfg.buttons.indexOfFirst { btn ->
                    val (bx, by) = panelCoords(btn)
                    sqrt((x - bx).pow(2) + (y - by).pow(2)) < btnRadius(btn) * 1.5f
                }
                if (dragIdx >= 0) { dragX = x; dragY = y; invalidate() }
                dragIdx >= 0
            }
            MotionEvent.ACTION_MOVE -> { if (dragIdx >= 0) { dragX = x; dragY = y; invalidate(); true } else false }
            MotionEvent.ACTION_UP -> {
                if (dragIdx >= 0) {
                    val btn = cfg.buttons[dragIdx]
                    cfg.buttons[dragIdx] = if (y < gameH)
                        btn.copy(targetX = x.toInt(), targetY = y.toInt())
                    else
                        btn.copy(panelX = (x / width).coerceIn(0.02f, 0.98f), panelY = ((y - gameH) / panelH).coerceIn(0.02f, 0.98f))
                    onSave(cfg); dragIdx = -1; invalidate()
                }
                true
            }
            else -> false
        }
    }

    private fun panelCoords(btn: ButtonConfig): Pair<Float, Float> = (btn.panelX * width) to (gameH + btn.panelY * panelH)
    private fun btnRadius(btn: ButtonConfig): Float = (btn.size * panelH / 2f).coerceAtLeast(28f)
}
