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

    // Fill uses a radial gradient when idle — gives the "lit from inside" look
    // that Game Booster's buttons have. Pressed state dims and shifts warm.
    private val fillPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = 2.5f
        color       = Color.argb(200, 0, 200, 255)
    }
    private val glowPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = 6f
        color       = Color.argb(40, 0, 200, 255)
        maskFilter  = BlurMaskFilter(8f, BlurMaskFilter.Blur.OUTER)
    }
    private val textPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.DEFAULT_BOLD
    }

    private val vibrator by lazy { context.getSystemService<Vibrator>() }

    fun setOnClickCallback(cb: () -> Unit) { onTap = cb }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r  = (minOf(width, height) / 2f) - strokePaint.strokeWidth - 3f

        if (isPressed) {
            fillPaint.shader = null
            fillPaint.color  = Color.argb(240, 0, 110, 160)
            canvas.save()
            canvas.scale(0.88f, 0.88f, cx, cy)
            canvas.drawCircle(cx, cy, r, fillPaint)
            strokePaint.color = Color.argb(255, 0, 230, 255)
            canvas.drawCircle(cx, cy, r, strokePaint)
        } else {
            fillPaint.shader = RadialGradient(
                cx, cy * 0.7f, r,
                intArrayOf(Color.argb(210, 10, 80, 130), Color.argb(200, 0, 50, 90)),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r + 4f, glowPaint)
            canvas.drawCircle(cx, cy, r, fillPaint)
            strokePaint.color = Color.argb(200, 0, 200, 255)
            canvas.save()
            canvas.drawCircle(cx, cy, r, strokePaint)
        }

        textPaint.textSize = r * 0.60f
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
