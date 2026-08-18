package com.foldgamepad

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.area.WindowAreaCapability
import androidx.window.area.WindowAreaController
import androidx.window.area.WindowAreaInfo
import com.foldgamepad.service.CalibrationOverlayService
import com.foldgamepad.service.CoverOverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var windowAreaController: WindowAreaController
    private var windowAreaInfo: WindowAreaInfo? = null
    private var statusText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        val title = TextView(this).apply {
            text = "CoverPad Triggers"
            textSize = 24f
            setPadding(0, 0, 0, 24)
        }
        root.addView(title)

        // ── Dual-screen mode diagnostic ──────────────────────────────────
        val diagLabel = TextView(this).apply {
            text = "Dual-screen mode (WindowAreaController) support on this device:"
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }
        root.addView(diagLabel)

        val status = TextView(this).apply {
            text = "Checking…"
            textSize = 15f
            setPadding(0, 0, 0, 24)
        }
        statusText = status
        root.addView(status)

        checkDualScreenSupport()

        root.addView(spacer())

        val desc = TextView(this).apply {
            text = "1. Enable Accessibility below\n" +
                   "2. Calibrate targets while a game is open\n" +
                   "3. Start cover triggers — they show on the back screen\n" +
                   "   while the phone stays open\n" +
                   "4. Press the edges on the back to fire taps on the game"
            textSize = 15f
            setPadding(0, 0, 0, 48)
        }
        root.addView(desc)

        root.addView(Button(this).apply {
            text = "1. Enable Accessibility Service"
            setOnClickListener {
                if (!isAccessibilityEnabled()) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else {
                    Toast.makeText(this@MainActivity, "Already enabled ✓", Toast.LENGTH_SHORT).show()
                }
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

        root.addView(Button(this).apply {
            text = "3. Start Cover Screen Triggers"
            setOnClickListener {
                if (!hasOverlayPermission()) { requestOverlayPermission(); return@setOnClickListener }
                startForegroundService(Intent(this@MainActivity, CoverOverlayService::class.java))
                Toast.makeText(this@MainActivity,
                    "Starting — check the toast/notification for display diagnostics",
                    Toast.LENGTH_LONG).show()
            }
        })

        root.addView(spacer())

        root.addView(Button(this).apply {
            text = "Stop Cover Triggers"
            setOnClickListener {
                stopService(Intent(this@MainActivity, CoverOverlayService::class.java))
            }
        })

        setContentView(root)
    }

    /**
     * Queries WindowAreaController for the rear-facing display area and reports
     * whether OPERATION_PRESENT_ON_AREA (true simultaneous dual-screen mode) is
     * supported/available on this exact device — this is the definitive test,
     * since Google's docs only confirm Pixel Fold support and Samsung's own
     * blog only covers the single-screen "rear display" variant.
     */
    private fun checkDualScreenSupport() {
        windowAreaController = WindowAreaController.getOrCreate()
        val presentOperation = WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA

        lifecycleScope.launch(Dispatchers.Main) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                windowAreaController.windowAreaInfos
                    .map { infos -> infos.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING } }
                    .onEach { info -> windowAreaInfo = info }
                    .map { it?.getCapability(presentOperation)?.status
                        ?: WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED }
                    .distinctUntilChanged()
                    .collect { status ->
                        val msg = when (status) {
                            WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED ->
                                "❌ UNSUPPORTED — dual-screen mode isn't available on this device/OS build at all"
                            WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNAVAILABLE ->
                                "⚠️ UNAVAILABLE right now (device state doesn't allow it currently)"
                            WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE ->
                                "✅ AVAILABLE — can be enabled!"
                            WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE ->
                                "✅ ACTIVE — already running"
                            else -> "❓ UNKNOWN status: $status"
                        }
                        Log.i("CoverPad", "Dual-screen capability status: $msg")
                        statusText?.text = msg
                    }
            }
        }
    }

    private fun spacer() = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 32)
    }

    private fun hasOverlayPermission() = Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        startActivity(Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        ))
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }
}
