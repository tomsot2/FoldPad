package com.foldgamepad.view

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.foldgamepad.model.ButtonConfig
import com.foldgamepad.model.JoystickMode
import kotlin.math.*

class VirtualJoystickView(
    context: Context,
    val config: ButtonConfig
) : View(context) {

    // Callbacks
    var onDown:   ((cx: Int, cy: Int) -> Unit)?                = null
    var onUpdate: ((normX: Float, normY: Float, dX: Float, dY: Float) -> Unit)? = null
    var onUp:     (() -> Unit)?                                = null

    private var normX  = 0f
    private var normY  = 0f
    private var prevNX = 0f
    private var prevNY = 0f
    private var active = false
    private var everMoved = false   // only fire onDown once thumb leaves centre

    // Paints
    private val outerFill   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL;   color = Color.argb(60,  0, 150, 200) }
    private val outerStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.argb(180, 0, 210, 255) }
    private val thumbPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL;   color = Color.argb(220, 0, 190, 240) }
    private val labelPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 255, 255, 255); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val modePaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 200, 0);   textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }

    override fun onDraw(canvas: Canvas) {
        val cx     = width / 2f
        val cy     = height / 2f
        val outerR = (minOf(width, height) / 2f) - outerStroke.strokeWidth

        canvas.drawCircle(cx, cy, outerR, outerFill)
        canvas.drawCircle(cx, cy, outerR, outerStroke)
        canvas.drawCircle(cx, cy, outerR * 0.18f, outerStroke.also { it.alpha = 60 })
        outerStroke.alpha = 180

        val thumbR = outerR * 0.32f
        thumbPaint.color = when {
            !active -> Color.argb(180, 0, 190, 240)
            config.joystickMode == JoystickMode.SWIPE_PAD -> Color.argb(255, 255, 160, 0)
            else    -> Color.argb(255, 0, 220, 255)
        }
        canvas.drawCircle(cx + normX * (outerR - thumbR), cy + normY * (outerR - thumbR), thumbR, thumbPaint)

        labelPaint.textSize = outerR * 0.28f
        canvas.drawText(config.label, cx, cy + labelPaint.textSize * 0.38f, labelPaint)

        // Mode indicator
        modePaint.textSize = outerR * 0.22f
        val modeStr = if (config.joystickMode == JoystickMode.SWIPE_PAD) "SWIPE" else "STICK"
        canvas.drawText(modeStr, cx, cy + outerR - 4f, modePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cx     = width / 2f
        val cy     = height / 2f
        val outerR = minOf(width, height) / 2f
        val deadZone = 0.12f

        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                active    = true
                everMoved = false
                prevNX    = 0f;  prevNY = 0f
                normX     = 0f;  normY  = 0f
                invalidate()
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val rawDx = event.x - cx
                val rawDy = event.y - cy
                val dist  = sqrt(rawDx * rawDx + rawDy * rawDy)
                if (dist > outerR) { normX = rawDx / dist; normY = rawDy / dist }
                else               { normX = rawDx / outerR; normY = rawDy / outerR }
                if (sqrt(normX * normX + normY * normY) < deadZone) { normX = 0f; normY = 0f }

                val dX = normX - prevNX
                val dY = normY - prevNY

                // Fire onDown once thumb first leaves dead zone
                if (!everMoved && (normX != 0f || normY != 0f)) {
                    everMoved = true
                    onDown?.invoke(cx.toInt(), cy.toInt())  // cx/cy in view coords; caller maps to game coords
                }

                if (everMoved) onUpdate?.invoke(normX, normY, dX, dY)

                prevNX = normX; prevNY = normY
                invalidate()
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                active    = false
                everMoved = false
                normX     = 0f;  normY  = 0f
                prevNX    = 0f;  prevNY = 0f
                invalidate()
                onUp?.invoke()
                true
            }
            else -> false
        }
    }
}
