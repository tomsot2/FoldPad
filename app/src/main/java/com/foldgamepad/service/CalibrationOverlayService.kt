package com.foldgamepad.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.*
import android.view.*
import androidx.core.app.NotificationCompat
import com.foldgamepad.MainActivity
import com.foldgamepad.model.CoverButton
import com.foldgamepad.util.CoverConfigManager

/**
 * Runs ONLY on the inner screen while unfolded. Shows small chips for each
 * cover button; tap a chip to "arm" it, then tap anywhere on the game screen
 * to set that as the point the cover-screen trigger will tap.
 */
class CalibrationOverlayService : Service() {

    private lateinit var wm: WindowManager
    private var overlayView: CalibrationView? = null

    companion object {
        var instance: CalibrationOverlayService? = null
        const val ACTION_STOP = "com.foldgamepad.STOP_CALIBRATION"
        private const val NOTIF_ID = 3
        private const val CHANNEL_ID = "calibration_overlay"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(NOTIF_ID, buildNotification())
        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        overlayView?.let { runCatching { wm.removeView(it) } }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        val layout = CoverConfigManager.load(this)
        val view = CalibrationView(this, layout.buttons) { updatedButtons ->
            CoverConfigManager.save(this, layout.copy(buttons = updatedButtons.toMutableList()))
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(view, params)
        overlayView = view
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "Calibration", NotificationManager.IMPORTANCE_LOW))
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Calibrating trigger targets")
            .setContentText("Tap a chip, then tap where it should press")
            .setContentIntent(PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .addAction(0, "Done", PendingIntent.getService(
                this, 0,
                Intent(this, CalibrationOverlayService::class.java).apply { action = ACTION_STOP },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setOngoing(true).build()
    }
}

private class CalibrationView(
    context: Context,
    initialButtons: List<CoverButton>,
    private val onSaved: (List<CoverButton>) -> Unit
) : View(context) {

    private val buttons = initialButtons.toMutableList()
    private var armedIdx = -1

    private val dimP = Paint().apply { color = Color.argb(40, 0, 0, 0) }
    private val chipArmedP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.argb(230, 255, 150, 0)
    }
    private val chipIdleP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.argb(200, 0, 140, 200)
    }
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; textSize = 28f
    }
    private val hintP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 255, 255); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; textSize = 24f
    }
    private val crossP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.argb(220, 255, 80, 80)
    }

    private fun chipRect(i: Int): RectF {
        val w = 140f; val h = 70f
        val left = 30f + i * (w + 20f)
        return RectF(left, 60f, left + w, 60f + h)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimP)

        buttons.forEachIndexed { i, btn ->
            val r = chipRect(i)
            canvas.drawRoundRect(r, 16f, 16f, if (i == armedIdx) chipArmedP else chipIdleP)
            canvas.drawText(btn.label, r.centerX(), r.centerY() + 10f, textP)
            if (btn.targetX >= 0) {
                canvas.drawCircle(btn.targetX.toFloat(), btn.targetY.toFloat(), 30f, crossP)
            }
        }

        val hint = if (armedIdx >= 0)
            "Tap where '${buttons[armedIdx].label}' should press"
        else "Tap a chip above, then tap the target point"
        canvas.drawText(hint, width / 2f, height - 60f, hintP)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        val x = event.x; val y = event.y

        buttons.forEachIndexed { i, _ ->
            if (chipRect(i).contains(x, y)) {
                armedIdx = i
                invalidate()
                return true
            }
        }

        if (armedIdx >= 0) {
            buttons[armedIdx] = buttons[armedIdx].copy(targetX = x.toInt(), targetY = y.toInt())
            onSaved(buttons)
            armedIdx = -1
            invalidate()
        }
        return true
    }
}
