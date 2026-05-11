package com.foldgamepad.view

import android.content.Context
import android.graphics.*
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import androidx.core.content.getSystemService
import com.foldgamepad.model.ButtonConfig

class VirtualButtonView(
    context: Context,
    val config: ButtonConfig
) : View(context) {

    private var onTap: (() -> Unit)? = null
    private var isPressed = false

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.argb(220, 0, 210, 255)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.DEFAULT_BOLD
    }

    private val vibrator by lazy { context.getSystemService<Vibrator>() }

    fun setOnClickCallback(cb: () -> Unit) { onTap = cb }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r  = (minOf(width, height) / 2f) - strokePaint.strokeWidth

        fillPaint.color = if (isPressed) Color.argb(230, 0, 90, 130) else Color.argb(190, 0, 150, 200)

        val scale = if (isPressed) 0.88f else 1f
        canvas.save()
        canvas.scale(scale, scale, cx, cy)
        canvas.drawCircle(cx, cy, r, fillPaint)
        canvas.drawCircle(cx, cy, r, strokePaint)
        textPaint.textSize = r * 0.55f
        canvas.drawText(config.label, cx, cy + textPaint.textSize * 0.36f, textPaint)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN   -> { isPressed = true;  invalidate(); true }
            MotionEvent.ACTION_UP     -> { isPressed = false; invalidate(); vibrate(); onTap?.invoke(); true }
            MotionEvent.ACTION_CANCEL -> { isPressed = false; invalidate(); true }
            else -> false
        }
    }

    private fun vibrate() {
        try { vibrator?.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE)) }
        catch (_: Exception) {}
    }
}
