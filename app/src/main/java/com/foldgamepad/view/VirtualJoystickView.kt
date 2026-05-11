package com.foldgamepad.view

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.foldgamepad.model.ButtonConfig
import kotlin.math.*

class VirtualJoystickView(
    context: Context,
    val config: ButtonConfig
) : View(context) {

    private var onMove:    ((dx: Float, dy: Float) -> Unit)? = null
    private var onRelease: (() -> Unit)?                     = null

    private var normX  = 0f
    private var normY  = 0f
    private var active = false

    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.argb(180, 0, 210, 255)
    }
    private val outerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.argb(60, 0, 150, 200)
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.argb(220, 0, 190, 240)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }

    fun setOnMoveCallback(cb: (Float, Float) -> Unit) { onMove    = cb }
    fun setOnReleaseCallback(cb: () -> Unit)           { onRelease = cb }
    fun getDelta(): Pair<Float, Float> = normX to normY

    override fun onDraw(canvas: Canvas) {
        val cx     = width / 2f
        val cy     = height / 2f
        val outerR = (minOf(width, height) / 2f) - outerPaint.strokeWidth

        canvas.drawCircle(cx, cy, outerR, outerFillPaint)
        canvas.drawCircle(cx, cy, outerR, outerPaint)
        canvas.drawCircle(cx, cy, outerR * 0.2f, outerPaint.also { it.alpha = 60 })
        outerPaint.alpha = 180

        val thumbR = outerR * 0.32f
        thumbPaint.color = if (active) Color.argb(255, 0, 220, 255) else Color.argb(180, 0, 190, 240)
        canvas.drawCircle(cx + normX * (outerR - thumbR), cy + normY * (outerR - thumbR), thumbR, thumbPaint)

        labelPaint.textSize = outerR * 0.3f
        canvas.drawText(config.label, cx, cy + labelPaint.textSize * 0.38f, labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cx     = width / 2f
        val cy     = height / 2f
        val outerR = minOf(width, height) / 2f
        return when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                active = true
                val rawDx = event.x - cx
                val rawDy = event.y - cy
                val dist  = sqrt(rawDx * rawDx + rawDy * rawDy)
                if (dist > outerR) { normX = rawDx / dist; normY = rawDy / dist }
                else               { normX = rawDx / outerR; normY = rawDy / outerR }
                if (sqrt(normX * normX + normY * normY) < 0.12f) { normX = 0f; normY = 0f }
                invalidate(); onMove?.invoke(normX, normY); true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                active = false; normX = 0f; normY = 0f
                invalidate(); onRelease?.invoke(); true
            }
            else -> false
        }
    }
}
