package com.foldgamepad.service

import android.app.*
import android.content.*
import android.content.res.Configuration
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.foldgamepad.MainActivity
import com.foldgamepad.R
import com.foldgamepad.model.ButtonConfig
import com.foldgamepad.model.ButtonType
import com.foldgamepad.model.LayoutConfig
import com.foldgamepad.util.ConfigManager
import com.foldgamepad.util.InputInjector
import com.foldgamepad.view.EditModeView
import com.foldgamepad.view.VirtualButtonView
import com.foldgamepad.view.VirtualJoystickView

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var config: LayoutConfig

    private var panelView:    FrameLayout? = null
    private var editView:     EditModeView? = null
    private var coverView:    FrameLayout? = null

    private var screenW   = 0
    private var screenH   = 0
    private var gameH     = 0
    private var panelH    = 0

    private var isEditMode  = false
    private var isCoverMode = false

    companion object {
        var instance: OverlayService? = null
        const val ACTION_EDIT   = "com.foldgamepad.ACTION_EDIT"
        const val ACTION_COVER  = "com.foldgamepad.ACTION_COVER"
        const val ACTION_STOP   = "com.foldgamepad.ACTION_STOP"
        private const val NOTIF_ID   = 1
        private const val CHANNEL_ID = "foldgamepad_overlay"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm     = getSystemService(WINDOW_SERVICE) as WindowManager
        config = ConfigManager.load(this)
        calcDimensions()
        createPanelWindow()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_EDIT  -> toggleEditMode()
            ACTION_COVER -> toggleCoverMode()
            ACTION_STOP  -> stopSelf()
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        calcDimensions()
        if (isCoverMode) {
            removeCoverWindow(); createCoverWindow()
        } else if (isEditMode) {
            removeEditWindow(); removePanelWindow(); createPanelWindow(); createEditWindow()
        } else {
            removePanelWindow(); createPanelWindow()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        removePanelWindow(); removeEditWindow(); removeCoverWindow()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun calcDimensions() {
        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.bounds
        } else {
            @Suppress("DEPRECATION")
            val dm = android.util.DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(dm)
            Rect(0, 0, dm.widthPixels, dm.heightPixels)
        }
        screenW = bounds.width()
        screenH = bounds.height()
        gameH   = (screenW * 9f / 16f).toInt()
        panelH  = (screenH - gameH).coerceAtLeast(1)
    }

    // ── Panel window ──────────────────────────────────────────────────────────

    private fun createPanelWindow() {
        val panel = FrameLayout(this)
        panel.setBackgroundColor(Color.argb(220, 12, 12, 18))
        buildPanelButtons(panel)
        val params = overlayParams(
            w       = WindowManager.LayoutParams.MATCH_PARENT,
            h       = panelH,
            flags   = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            gravity = Gravity.BOTTOM or Gravity.START
        )
        wm.addView(panel, params)
        panelView = panel
    }

    private fun removePanelWindow() {
        panelView?.let { runCatching { wm.removeView(it) } }
        panelView = null
    }

    private fun buildPanelButtons(panel: FrameLayout) {
        panel.removeAllViews()
        config.buttons.filter { it.isVisible }.forEach { btn ->
            val sizePx = (btn.size * panelH).toInt().coerceAtLeast(60)
            val cx     = (btn.panelX * screenW).toInt()
            val cy     = (btn.panelY * panelH).toInt()
            val lp     = FrameLayout.LayoutParams(sizePx, sizePx).apply {
                leftMargin = (cx - sizePx / 2).coerceAtLeast(0)
                topMargin  = (cy - sizePx / 2).coerceAtLeast(0)
            }
            when (btn.type) {
                ButtonType.TAP -> {
                    val v = VirtualButtonView(this, btn)
                    v.setOnClickCallback { fireTap(btn) }
                    panel.addView(v, lp)
                }
                ButtonType.JOYSTICK -> {
                    val v = VirtualJoystickView(this, btn)
                    v.setOnMoveCallback { dx, dy ->
                        if (!InputInjector.isReady) return@setOnMoveCallback
                        InputInjector.startJoystick(
                            btn.targetX.takeIf { it >= 0 } ?: (screenW / 2),
                            btn.targetY.takeIf { it >= 0 } ?: (gameH / 2),
                            btn.joystickGameRadius
                        ) { v.getDelta() }
                    }
                    v.setOnReleaseCallback { InputInjector.stopJoystick() }
                    panel.addView(v, lp)
                }
            }
        }
    }

    private fun fireTap(btn: ButtonConfig) {
        if (!InputInjector.isReady) {
            Toast.makeText(this, "Enable the FoldGamepad accessibility service first", Toast.LENGTH_SHORT).show()
            return
        }
        if (btn.targetX < 0 || btn.targetY < 0) {
            Toast.makeText(this, "${btn.label}: open Edit Mode to set a target", Toast.LENGTH_SHORT).show()
            return
        }
        InputInjector.tap(btn.targetX, btn.targetY)
    }

    // ── Edit mode ─────────────────────────────────────────────────────────────

    private fun toggleEditMode() { if (isEditMode) exitEditMode() else enterEditMode() }

    private fun enterEditMode() {
        isEditMode = true
        updateNotification()
        createEditWindow()
    }

    private fun exitEditMode() {
        isEditMode = false
        updateNotification()
        removeEditWindow()
        removePanelWindow()
        createPanelWindow()
    }

    private fun createEditWindow() {
        val ev = EditModeView(
            context = this,
            config  = config,
            screenW = screenW,
            screenH = screenH,
            gameH   = gameH,
            panelH  = panelH,
            onSave  = { updated -> config = updated; ConfigManager.save(this, config) },
            onDone  = { exitEditMode() }
        )
        wm.addView(ev, overlayParams(
            w       = WindowManager.LayoutParams.MATCH_PARENT,
            h       = WindowManager.LayoutParams.MATCH_PARENT,
            flags   = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            gravity = Gravity.TOP or Gravity.START
        ))
        editView = ev
    }

    private fun removeEditWindow() {
        editView?.let { runCatching { wm.removeView(it) } }
        editView = null
    }

    // ── Cover mode ────────────────────────────────────────────────────────────

    private fun toggleCoverMode() {
        if (isCoverMode) {
            isCoverMode = false
            removeCoverWindow()
            if (!isEditMode) createPanelWindow()
        } else {
            isCoverMode = true
            removePanelWindow(); removeEditWindow()
            isEditMode = false
            createCoverWindow()
        }
        updateNotification()
    }

    private fun createCoverWindow() {
        val cover = FrameLayout(this)
        cover.setBackgroundColor(Color.argb(235, 10, 10, 16))

        val l2 = makeCoverButton("L2") { fireL2() }
        cover.addView(l2, FrameLayout.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, screenH / 2
        ).apply { gravity = Gravity.TOP })

        val divider = android.view.View(this)
        divider.setBackgroundColor(Color.argb(160, 0, 210, 255))
        cover.addView(divider, FrameLayout.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, 3
        ).apply { topMargin = screenH / 2 - 1; gravity = Gravity.TOP })

        val r2 = makeCoverButton("R2") { fireR2() }
        cover.addView(r2, FrameLayout.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, screenH / 2
        ).apply { gravity = Gravity.BOTTOM })

        wm.addView(cover, overlayParams(
            w       = WindowManager.LayoutParams.MATCH_PARENT,
            h       = WindowManager.LayoutParams.MATCH_PARENT,
            flags   = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            gravity = Gravity.TOP or Gravity.START
        ))
        coverView = cover
    }

    private fun makeCoverButton(label: String, onClick: () -> Unit): android.widget.TextView =
        android.widget.TextView(this).apply {
            text      = label
            textSize  = 52f
            gravity   = Gravity.CENTER
            typeface  = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { onClick() }
        }

    private fun fireL2() {
        if (!InputInjector.isReady) return
        val x = config.l2TargetX.takeIf { it >= 0 } ?: return
        val y = config.l2TargetY.takeIf { it >= 0 } ?: return
        InputInjector.tap(x, y)
    }

    private fun fireR2() {
        if (!InputInjector.isReady) return
        val x = config.r2TargetX.takeIf { it >= 0 } ?: return
        val y = config.r2TargetY.takeIf { it >= 0 } ?: return
        InputInjector.tap(x, y)
    }

    private fun removeCoverWindow() {
        coverView?.let { runCatching { wm.removeView(it) } }
        coverView = null
    }

    // ── App launch ────────────────────────────────────────────────────────────

    fun launchGame(packageName: String, appName: String) {
        config = config.copy(gamePackage = packageName, gameName = appName)
        ConfigManager.save(this, config)
        calcDimensions()
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: run {
            Toast.makeText(this, "Cannot launch $appName", Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        val options = ActivityOptions.makeBasic()
        options.launchBounds = Rect(0, 0, screenW, gameH)
        startActivity(intent, options.toBundle())
        Toast.makeText(this, "Launched $appName at 16:9", Toast.LENGTH_SHORT).show()
        updateNotification()
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "FoldGamepad", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("FoldGamepad  •  ${config.gameName.ifBlank { "No game" }}")
            .setContentText(if (isEditMode) "Edit mode – drag buttons to targets" else "Overlay active")
            .setContentIntent(PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .addAction(0, if (isEditMode) "Exit Edit" else "Edit Layout", pendingIntent(ACTION_EDIT))
            .addAction(0, if (isCoverMode) "Exit Cover" else "Cover Mode", pendingIntent(ACTION_COVER))
            .addAction(0, "Stop", pendingIntent(ACTION_STOP))
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, buildNotification())
    }

    private fun pendingIntent(action: String): PendingIntent =
        PendingIntent.getService(this, action.hashCode(),
            Intent(this, OverlayService::class.java).apply { this.action = action },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun overlayParams(w: Int, h: Int, flags: Int, gravity: Int) =
        WindowManager.LayoutParams(w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT
        ).apply { this.gravity = gravity }
}
