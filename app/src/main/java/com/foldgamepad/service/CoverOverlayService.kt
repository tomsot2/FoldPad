package com.foldgamepad.service

import android.app.*
import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.os.*
import android.view.*
import androidx.core.app.NotificationCompat
import com.foldgamepad.MainActivity
import com.foldgamepad.model.CoverButton
import com.foldgamepad.model.CoverLayout
import com.foldgamepad.util.CoverConfigManager
import com.foldgamepad.util.InputInjector
import kotlin.math.sqrt

/**
 * Finds the cover (outer) display and shows a resizable trigger-button overlay
 * on it via Presentation. Presses are dispatched as taps at each button's
 * configured target coordinates — dispatchGesture() always lands on the
 * DEFAULT display (the inner screen) regardless of which display the
 * accessibility service's own overlay window is shown on.
 */
class CoverOverlayService : Service() {

    private lateinit var displayManager: DisplayManager
    private var presentation: CoverPresentation? = null
    private var layout = CoverLayout()
    private var isEditMode = false

    companion object {
        var instance: CoverOverlayService? = null
        const val ACTION_TOGGLE_EDIT = "com.foldgamepad.TOGGLE_EDIT"
        const val ACTION_STOP        = "com.foldgamepad.STOP_COVER"
        private const val NOTIF_ID   = 2
        private const val CHANNEL_ID = "coverpad_overlay"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        layout = CoverConfigManager.load(this)
        startForeground(NOTIF_ID, buildNotification())
        showOnCoverDisplay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_EDIT -> { isEditMode = !isEditMode; presentation?.setEditMode(isEditMode); updateNotification() }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        presentation?.dismiss()
        presentation = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Finds the non-default display (the cover screen) and shows the button
     * overlay there. This is attempted regardless of fold state — the cover
     * display should be enumerable and drawable via Presentation whether the
     * phone is open or closed, the same way Camera shows a live preview on
     * the cover screen while unfolded.
     */
    private fun showOnCoverDisplay() {
        val displays = displayManager.displays

        // Diagnostic: log every display Android reports, so we can see exactly
        // what's available rather than guessing.
        val info = displays.joinToString("\n") { d ->
            "id=${d.displayId} name=\"${d.name}\" state=${stateName(d.state)} flags=${d.flags}"
        }
        android.util.Log.i("CoverOverlay", "Displays found:\n$info")

        val coverDisplay = displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }

        if (coverDisplay == null) {
            android.widget.Toast.makeText(this,
                "Only 1 display detected (${displays.size} total). " +
                "The cover screen isn't being reported to this app — see logcat 'CoverOverlay' for details.",
                android.widget.Toast.LENGTH_LONG).show()
            return
        }

        android.widget.Toast.makeText(this,
            "Found cover display: id=${coverDisplay.displayId}, state=${stateName(coverDisplay.state)}",
            android.widget.Toast.LENGTH_LONG).show()

        presentation = CoverPresentation(this, coverDisplay, layout,
            onButtonPressed = { btn ->
                if (btn.targetX >= 0 && btn.targetY >= 0 && InputInjector.isReady) {
                    // Plain dispatchGesture() always targets the default display
                    // (the inner screen), regardless of the fact that this press
                    // originated on the cover display's Presentation.
                    InputInjector.tap(btn.targetX, btn.targetY)
                }
            },
            onLayoutChanged = { updated ->
                layout = updated
                CoverConfigManager.save(this, layout)
            }
        )
        try {
            presentation?.show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this,
                "Presentation.show() failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            android.util.Log.e("CoverOverlay", "show() failed", e)
        }
        updateNotification()
    }

    private fun stateName(state: Int): String = when (state) {
        Display.STATE_ON -> "ON"
        Display.STATE_OFF -> "OFF"
        Display.STATE_DOZE -> "DOZE"
        Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND"
        Display.STATE_ON_SUSPEND -> "ON_SUSPEND"
        Display.STATE_UNKNOWN -> "UNKNOWN"
        else -> "OTHER($state)"
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "CoverPad", NotificationManager.IMPORTANCE_LOW))
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("CoverPad triggers active")
            .setContentText(if (isEditMode) "Edit mode — drag to resize/move" else "Ready — tap notification to edit")
            .setContentIntent(PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .addAction(0, if (isEditMode) "Done Editing" else "Edit Buttons", pendingIntent(ACTION_TOGGLE_EDIT))
            .addAction(0, "Stop", pendingIntent(ACTION_STOP))
            .setOngoing(true).build()
    }

    private fun updateNotification() =
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, buildNotification())

    private fun pendingIntent(action: String) = PendingIntent.getService(
        this, action.hashCode(),
        Intent(this, CoverOverlayService::class.java).apply { this.action = action },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

/** Drag interaction states for the cover-screen edit mode. Top-level so it can
 *  be referenced from the inner View class (Kotlin disallows enums nested
 *  directly inside inner classes). */
private enum class DragMode { NONE, MOVE, RESIZE }

/**
 * The UI shown on the cover display. Draws resizable/movable button zones.
 * In edit mode: drag body to move, drag the ⇲ badge to resize.
 */
private class CoverPresentation(
    context: Context,
    display: Display,
    private var layout: CoverLayout,
    private val onButtonPressed: (CoverButton) -> Unit,
    private val onLayoutChanged: (CoverLayout) -> Unit
) : Presentation(context, display) {

    private lateinit var canvasView: ButtonCanvasView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        canvasView = ButtonCanvasView(context)
        setContentView(canvasView)
    }

    fun setEditMode(enabled: Boolean) = canvasView.setEditMode(enabled)

    private inner class ButtonCanvasView(ctx: Context) : View(ctx) {

        private var editMode = false
        private var dragIdx  = -1
        private var dragMode = DragMode.NONE
        private var dragStartX = 0f; private var dragStartY = 0f
        private var origX = 0f; private var origY = 0f; private var origW = 0f; private var origH = 0f

        private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = Color.argb(140, 0, 150, 200)
        }
        private val fillEditP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = Color.argb(180, 255, 150, 0)
        }
        private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.argb(220, 0, 220, 255)
        }
        private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD; textSize = 32f
        }
        private val hintP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 255, 255, 255); textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD; textSize = 22f
        }
        private val resizeBadgeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = Color.argb(220, 210, 180, 0)
        }

        fun setEditMode(enabled: Boolean) { editMode = enabled; invalidate() }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.argb(30, 0, 0, 0))
            layout.buttons.forEachIndexed { i, btn ->
                val rect = boundsOf(btn)
                canvas.drawRoundRect(rect, 24f, 24f, if (editMode) fillEditP else fillP)
                canvas.drawRoundRect(rect, 24f, 24f, strokeP)
                canvas.drawText(btn.label, rect.centerX(), rect.centerY() + 12f, textP)

                if (editMode) {
                    val badgeR = 28f
                    canvas.drawCircle(rect.right, rect.bottom, badgeR, resizeBadgeP)
                    textP.textSize = 22f
                    canvas.drawText("⇲", rect.right, rect.bottom + 8f, textP)
                    textP.textSize = 32f
                }
            }
            if (editMode) {
                canvas.drawText("Drag to move · drag ⇲ to resize",
                    width / 2f, height - 30f, hintP)
            }
        }

        private fun boundsOf(btn: CoverButton): RectF = RectF(
            btn.x * width, btn.y * height,
            (btn.x + btn.w) * width, (btn.y + btn.h) * height
        )

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
            layout.buttons.forEachIndexed { i, btn ->
                val rect = boundsOf(btn)
                val badgeDist = sqrt((x - rect.right).let{it*it} + (y - rect.bottom).let{it*it})

                if (editMode && badgeDist < 40f) {
                    dragIdx = i; dragMode = DragMode.RESIZE
                    dragStartX = x; dragStartY = y
                    origW = btn.w; origH = btn.h
                    return true
                }
                if (rect.contains(x, y)) {
                    if (editMode) {
                        dragIdx = i; dragMode = DragMode.MOVE
                        dragStartX = x; dragStartY = y
                        origX = btn.x; origY = btn.y
                    } else {
                        onButtonPressed(btn)
                    }
                    return true
                }
            }
            return false
        }

        private fun onMove(x: Float, y: Float): Boolean {
            if (dragIdx < 0 || dragMode == DragMode.NONE) return false
            val btn = layout.buttons[dragIdx]
            val dxFrac = (x - dragStartX) / width
            val dyFrac = (y - dragStartY) / height

            layout.buttons[dragIdx] = when (dragMode) {
                DragMode.MOVE -> btn.copy(
                    x = (origX + dxFrac).coerceIn(0f, 1f - btn.w),
                    y = (origY + dyFrac).coerceIn(0f, 1f - btn.h)
                )
                DragMode.RESIZE -> btn.copy(
                    w = (origW + dxFrac).coerceIn(0.06f, 1f - btn.x),
                    h = (origH + dyFrac).coerceIn(0.06f, 1f - btn.y)
                )
                else -> btn
            }
            invalidate()
            return true
        }

        private fun onUp(x: Float, y: Float): Boolean {
            if (dragIdx >= 0) onLayoutChanged(layout)
            dragIdx = -1; dragMode = DragMode.NONE
            return true
        }
    }
}
