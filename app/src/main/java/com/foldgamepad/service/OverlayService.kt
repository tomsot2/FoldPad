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
import com.foldgamepad.model.JoystickMode
import com.foldgamepad.model.LayoutConfig
import com.foldgamepad.util.ConfigManager
import com.foldgamepad.util.InputInjector
import com.foldgamepad.util.SamsungInputBridge
import com.foldgamepad.view.EditModeView
import com.foldgamepad.view.GamepadPanelLayout
import com.foldgamepad.view.VirtualButtonView
import com.foldgamepad.view.VirtualJoystickView

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var config: LayoutConfig

    private var panelView: FrameLayout? = null
    private var editView:  EditModeView? = null
    private var coverView: FrameLayout? = null

    private var screenW = 0; private var screenH = 0
    private var gameH   = 0; private var panelH   = 0
    private var isEditMode  = false
    private var isCoverMode = false

    private val gameReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_GAME_RESUME -> {
                    val pkg = intent.getStringExtra("packageName") ?: return
                    if (config.gamePackage.isBlank() || config.gamePackage == pkg) {
                        if (!isCoverMode && !isEditMode) { removePanelWindow(); createPanelWindow() }
                    }
                }
                ACTION_GAME_PAUSE -> {
                    if (!isEditMode && !isCoverMode) removePanelWindow()
                }
            }
        }
    }

    companion object {
        var instance: OverlayService? = null
        const val ACTION_EDIT  = "com.foldgamepad.ACTION_EDIT"
        const val ACTION_COVER = "com.foldgamepad.ACTION_COVER"
        const val ACTION_STOP  = "com.foldgamepad.ACTION_STOP"

        private const val ACTION_GAME_RESUME = "com.samsung.android.game.gametools.ACTION_GAME_ON_RESUME"
        private const val ACTION_GAME_PAUSE  = "com.samsung.android.game.gametools.ACTION_GAME_ON_PAUSE"

        private const val FLAG_SPLIT_TOUCH = 0x00800000

        private const val NOTIF_ID   = 1
        private const val CHANNEL_ID = "foldgamepad_overlay"

        private const val EDIT_BTN_W = 96
        private const val EDIT_BTN_H = 44
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm     = getSystemService(WINDOW_SERVICE) as WindowManager
        config = ConfigManager.load(this)
        calcDimensions()
        createPanelWindow()
        startForeground(NOTIF_ID, buildNotification())
        registerGameReceiver()
        applySamsungBridge()
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
        when {
            isCoverMode -> { removeCoverWindow(); createCoverWindow() }
            isEditMode  -> { removeEditWindow(); createEditWindow() }
            else        -> { removePanelWindow(); createPanelWindow() }
        }
        applySamsungBridge()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        SamsungInputBridge.clearMapping()
        try { unregisterReceiver(gameReceiver) } catch (e: Exception) {}
        removePanelWindow(); removeEditWindow(); removeCoverWindow()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun applySamsungBridge() {
        SamsungInputBridge.applyMapping(config.buttons, screenW, gameH, panelH)
        updateNotification()
    }

    private fun registerGameReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_GAME_RESUME)
            addAction(ACTION_GAME_PAUSE)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(gameReceiver, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(gameReceiver, filter)
            }
        } catch (e: Exception) {}
    }

    private fun calcDimensions() {
        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.bounds
        } else {
            @Suppress("DEPRECATION")
            val dm = android.util.DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(dm)
            Rect(0, 0, dm.widthPixels, dm.heightPixels)
        }
        screenW = bounds.width(); screenH = bounds.height()
        gameH   = (screenW * 9f / 16f).toInt()
        panelH  = (screenH - gameH).coerceAtLeast(1)
    }

    private fun createPanelWindow() {
        val panel = GamepadPanelLayout(this)
        panel.setBackgroundColor(Color.argb(230, 10, 10, 16))

        buildPanelButtons(panel)
        addEditButton(panel)

        wm.addView(panel, overlayParams(
            w       = WindowManager.LayoutParams.MATCH_PARENT,
            h       = panelH,
            flags   = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                      FLAG_SPLIT_TOUCH,
            gravity = Gravity.BOTTOM or Gravity.START
        ))
        panelView = panel
    }

    private fun removePanelWindow() {
        panelView?.let { runCatching { wm.removeView(it) } }
        panelView = null
    }

    private fun addEditButton(panel: FrameLayout) {
        val btn = object : View(this) {
            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL; color = Color.argb(160, 20, 20, 30)
            }
            private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1.5f
                color = Color.argb(140, 0, 180, 220)
            }
            private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(200, 200, 220, 255)
                textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; textSize = 22f
            }
            private var pressed = false
            override fun onDraw(canvas: Canvas) {
                val r = height / 2f
                val rect = RectF(2f, 2f, width - 2f, height - 2f)
                bgPaint.color = if (pressed) Color.argb(220, 0, 80, 120) else Color.argb(160, 20, 20, 30)
                canvas.drawRoundRect(rect, r, r, bgPaint)
                canvas.drawRoundRect(rect, r, r, strokePaint)
                canvas.drawText("✎  edit", width / 2f, height / 2f + textPaint.textSize * 0.36f, textPaint)
            }
            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN   -> { pressed = true;  invalidate() }
                    MotionEvent.ACTION_UP     -> { pressed = false; invalidate(); toggleEditMode() }
                    MotionEvent.ACTION_CANCEL -> { pressed = false; invalidate() }
                }
                return true
            }
        }

        panel.addView(btn, FrameLayout.LayoutParams(EDIT_BTN_W, EDIT_BTN_H).apply {
            gravity   = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = 12
        })
    }

    private fun buildPanelButtons(panel: FrameLayout) {
        // Clear stale views before rebuild so old button positions never linger.
        panel.removeAllViews()
        config.buttons.filter { it.isVisible }.forEach { btn ->
            val sizePx = (btn.size * panelH).toInt().coerceAtLeast(60)
            val cx = (btn.panelX * screenW).toInt()
            val cy = (btn.panelY * panelH).toInt()
            val lp = FrameLayout.LayoutParams(sizePx, sizePx).apply {
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
                    wireJoystick(v, btn)
                    panel.addView(v, lp)
                }
            }
        }
    }

    private fun wireJoystick(v: VirtualJoystickView, btn: ButtonConfig) {
        val cx = btn.targetX.takeIf { it >= 0 } ?: (screenW / 2)
        val cy = btn.targetY.takeIf { it >= 0 } ?: (gameH / 2)

        v.onDown   = { _, _ ->
            if (InputInjector.isReady && btn.joystickMode == JoystickMode.STICK)
                InputInjector.joystickDown(cx, cy)
        }
        v.onUpdate = update@{ normX, normY, dX, dY ->
            if (!InputInjector.isReady) return@update
            when (btn.joystickMode) {
                JoystickMode.STICK -> InputInjector.joystickUpdate(
                    cx + normX * btn.joystickGameRadius,
                    cy + normY * btn.joystickGameRadius
                )
                JoystickMode.SWIPE_PAD -> if (dX != 0f || dY != 0f) {
                    val s = btn.joystickGameRadius.toFloat()
                    InputInjector.swipePad(cx.toFloat(), cy.toFloat(), cx + dX * s, cy + dY * s)
                }
            }
        }
        v.onUp     = {
            if (InputInjector.isReady && btn.joystickMode == JoystickMode.STICK)
                InputInjector.joystickUp()
        }
    }

    private fun fireTap(btn: ButtonConfig) {
        if (SamsungInputBridge.isActive) return
        if (!InputInjector.isReady) {
            Toast.makeText(this, "Enable the accessibility service first", Toast.LENGTH_SHORT).show()
            return
        }
        if (btn.targetX < 0 || btn.targetY < 0) {
            Toast.makeText(this, "${btn.label}: set a target in Edit Mode", Toast.LENGTH_SHORT).show()
            return
        }
        InputInjector.tap(btn.targetX, btn.targetY)
    }

    private fun toggleEditMode() { if (isEditMode) exitEditMode() else enterEditMode() }

    private fun enterEditMode() {
        isEditMode = true; updateNotification()
        // Hide panel while editing — EditModeView draws everything itself,
        // and leaving the panel underneath causes ghost buttons at old positions.
        removePanelWindow()
        createEditWindow()
    }

    private fun exitEditMode() {
        isEditMode = false; updateNotification()
        removeEditWindow(); removePanelWindow(); createPanelWindow()
        applySamsungBridge()
    }

    private fun createEditWindow() {
        val ev = EditModeView(
            context = this, config = config,
            screenW = screenW, screenH = screenH, gameH = gameH, panelH = panelH,
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

    private fun toggleCoverMode() {
        if (isCoverMode) {
            isCoverMode = false; removeCoverWindow()
            if (!isEditMode) createPanelWindow()
        } else {
            isCoverMode = true
            removePanelWindow(); removeEditWindow(); isEditMode = false
            createCoverWindow()
        }
        updateNotification()
    }

    private fun createCoverWindow() {
        val cover = FrameLayout(this)
        cover.setBackgroundColor(Color.argb(235, 10, 10, 16))
        cover.addView(makeCoverButton("L2") { fireL2() }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, screenH / 2
        ).apply { gravity = Gravity.TOP })
        val div = View(this).also { it.setBackgroundColor(Color.argb(160, 0, 210, 255)) }
        cover.addView(div, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, 3
        ).apply { topMargin = screenH / 2 - 1; gravity = Gravity.TOP })
        cover.addView(makeCoverButton("R2") { fireR2() }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, screenH / 2
        ).apply { gravity = Gravity.BOTTOM })
        wm.addView(cover, overlayParams(
            w       = FrameLayout.LayoutParams.MATCH_PARENT,
            h       = FrameLayout.LayoutParams.MATCH_PARENT,
            flags   = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            gravity = Gravity.TOP or Gravity.START
        ))
        coverView = cover
    }

    private fun makeCoverButton(label: String, onClick: () -> Unit) =
        android.widget.TextView(this).apply {
            text = label; textSize = 52f; gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { onClick() }
        }

    private fun fireL2() {
        if (SamsungInputBridge.isActive || !InputInjector.isReady) return
        InputInjector.tap(config.l2TargetX.takeIf { it >= 0 } ?: return,
                          config.l2TargetY.takeIf { it >= 0 } ?: return)
    }

    private fun fireR2() {
        if (SamsungInputBridge.isActive || !InputInjector.isReady) return
        InputInjector.tap(config.r2TargetX.takeIf { it >= 0 } ?: return,
                          config.r2TargetY.takeIf { it >= 0 } ?: return)
    }

    private fun removeCoverWindow() {
        coverView?.let { runCatching { wm.removeView(it) } }
        coverView = null
    }

    fun launchGame(packageName: String, appName: String) {
        // Load the layout saved for this game (falls back to default if new)
        config = ConfigManager.loadForPackage(this, packageName).copy(gameName = appName)
        ConfigManager.save(this, config)
        calcDimensions()
        removePanelWindow(); createPanelWindow()
        applySamsungBridge()

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

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "FoldGamepad", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val bridgeTag = if (SamsungInputBridge.isActive) " · Samsung native" else ""
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("FoldGamepad  •  ${config.gameName.ifBlank { "No game" }}$bridgeTag")
            .setContentText(if (isEditMode) "Edit mode active" else "Overlay active")
            .setContentIntent(PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            ))
            .addAction(0, if (isEditMode) "Exit Edit" else "Edit Layout", pendingIntent(ACTION_EDIT))
            .addAction(0, if (isCoverMode) "Exit Cover" else "Cover Mode", pendingIntent(ACTION_COVER))
            .addAction(0, "Stop", pendingIntent(ACTION_STOP))
            .setOngoing(true).build()
    }

    private fun updateNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, buildNotification())
    }

    private fun pendingIntent(action: String): PendingIntent =
        PendingIntent.getService(
            this, action.hashCode(),
            Intent(this, OverlayService::class.java).apply { this.action = action },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun overlayParams(w: Int, h: Int, flags: Int, gravity: Int) =
        WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags, PixelFormat.TRANSLUCENT
        ).apply { this.gravity = gravity }
}
