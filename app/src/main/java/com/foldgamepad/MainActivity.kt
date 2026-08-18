package com.foldgamepad

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.area.WindowAreaCapability
import androidx.window.area.WindowAreaController
import androidx.window.area.WindowAreaInfo
import androidx.window.area.WindowAreaPresentationSessionCallback
import androidx.window.area.WindowAreaSessionPresenter
import com.foldgamepad.model.CoverButton
import com.foldgamepad.model.CoverLayout
import com.foldgamepad.service.CalibrationOverlayService
import com.foldgamepad.util.CoverConfigManager
import com.foldgamepad.util.InputInjector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/** Drag interaction states for the cover-screen edit mode. */
private enum class DragMode { NONE, MOVE, RESIZE }

class MainActivity : AppCompatActivity(), WindowAreaPresentationSessionCallback {

    private lateinit var windowAreaController: WindowAreaController
    private var windowAreaInfo: WindowAreaInfo? = null
    private var windowAreaSession: WindowAreaSessionPresenter? = null
    private var capabilityStatus: WindowAreaCapability.Status =
        WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED

    private val presentOperation = WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA

    private var statusText: TextView? = null
    private var startStopBtn: Button? = null
    private var editModeOn = false
    private var canvasView: CoverButtonCanvasView? = null
    private var layout = CoverLayout()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        layout = CoverConfigManager.load(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        root.addView(TextView(this).apply {
            text = "CoverPad Triggers"
            textSize = 24f
            setPadding(0, 0, 0, 24)
        })

        root.addView(TextView(this).apply {
            text = "Dual-screen mode support:"
            textSize = 14f
            setPadding(0, 0, 0, 8)
        })

        val status = TextView(this).apply { text = "Checking…"; textSize = 15f; setPadding(0, 0, 0, 24) }
        statusText = status
        root.addView(status)

        root.addView(TextView(this).apply {
            text = "1. Enable Accessibility below\n" +
                   "2. Calibrate targets while a game is open\n" +
                   "3. Start cover triggers — buttons appear on the back\n" +
                   "   screen while the phone stays open and the game runs\n" +
                   "4. Press the edges on the back to fire taps on the game"
            textSize = 15f
            setPadding(0, 0, 0, 32)
        })

        root.addView(Button(this).apply {
            text = "1. Enable Accessibility Service"
            setOnClickListener {
                if (!isAccessibilityEnabled()) startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                else Toast.makeText(this@MainActivity, "Already enabled ✓", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(spacer())

        root.addView(Button(this).apply {
            text = "2. Calibrate Trigger Targets (in-game)"
            setOnClickListener {
                if (!hasOverlayPermission()) { requestOverlayPermission(); return@setOnClickListener }
                startForegroundService(Intent(this@MainActivity, CalibrationOverlayService::class.java))
                Toast.makeText(this@MainActivity,
                    "Overlay active — tap a chip, then tap the target point in-game",
                    Toast.LENGTH_LONG).show()
            }
        })

        root.addView(spacer())

        val startStop = Button(this).apply {
            text = "3. Start Cover Screen Triggers"
            setOnClickListener { toggleDualScreenMode() }
        }
        startStopBtn = startStop
        root.addView(startStop)

        root.addView(spacer())

        root.addView(Button(this).apply {
            text = "Toggle Edit Mode (resize/move buttons)"
            setOnClickListener {
                editModeOn = !editModeOn
                canvasView?.setEditMode(editModeOn)
                Toast.makeText(this@MainActivity,
                    if (editModeOn) "Edit mode ON — drag to move, ⇲ to resize" else "Edit mode OFF",
                    Toast.LENGTH_SHORT).show()
            }
        })

        setContentView(root)
        checkDualScreenSupport()
    }

    // ── Dual-screen capability check ────────────────────────────────────────

    private fun checkDualScreenSupport() {
        windowAreaController = WindowAreaController.getOrCreate()
        lifecycleScope.launch(Dispatchers.Main) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                windowAreaController.windowAreaInfos
                    .map { infos -> infos.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING } }
                    .onEach { info -> windowAreaInfo = info }
                    .map { it?.getCapability(presentOperation)?.status
                        ?: WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED }
                    .distinctUntilChanged()
                    .collect { status ->
                        capabilityStatus = status
                        statusText?.text = statusLabel(status)
                    }
            }
        }
    }

    private fun statusLabel(status: WindowAreaCapability.Status) = when (status) {
        WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED -> "❌ UNSUPPORTED"
        WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNAVAILABLE -> "⚠️ UNAVAILABLE right now"
        WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE   -> "✅ AVAILABLE"
        WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE      -> "✅ ACTIVE"
        else -> "❓ UNKNOWN"
    }

    // ── Start / stop dual-screen session ─────────────────────────────────────

    private fun toggleDualScreenMode() {
        val session = windowAreaSession
        if (session != null) {
            session.close()
            return
        }
        val token = windowAreaInfo?.token
        if (token == null) {
            Toast.makeText(this, "No rear display area available right now", Toast.LENGTH_LONG).show()
            return
        }
        windowAreaController.presentContentOnWindowArea(
            token = token,
            activity = this,
            executor = mainExecutor,
            windowAreaPresentationSessionCallback = this
        )
    }

    override fun onSessionStarted(session: WindowAreaSessionPresenter) {
        windowAreaSession = session
        startStopBtn?.text = "Stop Cover Screen Triggers"

        val canvas = CoverButtonCanvasView(
            context = session.context,
            layout = layout,
            onButtonPressed = { btn ->
                if (btn.targetX >= 0 && btn.targetY >= 0 && InputInjector.isReady) {
                    InputInjector.tap(btn.targetX, btn.targetY)
                }
            },
            onLayoutChanged = { updated -> layout = updated; CoverConfigManager.save(this, layout) }
        )
        canvas.setEditMode(editModeOn)
        canvasView = canvas
        session.setContentView(canvas)

        Toast.makeText(this, "Cover triggers active", Toast.LENGTH_SHORT).show()
    }

    override fun onSessionEnded(t: Throwable?) {
        windowAreaSession = null
        canvasView = null
        startStopBtn?.text = "3. Start Cover Screen Triggers"
        if (t != null) {
            Log.e("CoverPad", "Dual-screen session ended with error", t)
            Toast.makeText(this, "Session ended: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onContainerVisibilityChanged(isVisible: Boolean) {
        Log.d("CoverPad", "Cover screen container visible = $isVisible")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun spacer() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 32)
    }

    private fun hasOverlayPermission() = Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    }
}

/**
 * The button UI shown on the cover display via the dual-screen session's
 * content view. Same drag/resize/press logic as the earlier Presentation-based
 * version, just hosted inside a WindowAreaSessionPresenter's context instead.
 */
private class CoverButtonCanvasView(
    context: android.content.Context,
    private var layout: CoverLayout,
    private val onButtonPressed: (CoverButton) -> Unit,
    private val onLayoutChanged: (CoverLayout) -> Unit
) : View(context) {

    private var editMode = false
    private var dragIdx  = -1
    private var dragMode = DragMode.NONE
    private var dragStartX = 0f; private var dragStartY = 0f
    private var origX = 0f; private var origY = 0f; private var origW = 0f; private var origH = 0f

    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(140, 0, 150, 200) }
    private val fillEditP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(180, 255, 150, 0) }
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.argb(220, 0, 220, 255) }
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; textSize = 32f }
    private val hintP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 255, 255); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; textSize = 22f }
    private val resizeBadgeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(220, 210, 180, 0) }

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
        if (editMode) canvas.drawText("Drag to move · drag ⇲ to resize", width / 2f, height - 30f, hintP)
    }

    private fun boundsOf(btn: CoverButton): RectF = RectF(
        btn.x * width, btn.y * height, (btn.x + btn.w) * width, (btn.y + btn.h) * height
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
                dragStartX = x; dragStartY = y; origW = btn.w; origH = btn.h
                return true
            }
            if (rect.contains(x, y)) {
                if (editMode) {
                    dragIdx = i; dragMode = DragMode.MOVE
                    dragStartX = x; dragStartY = y; origX = btn.x; origY = btn.y
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
