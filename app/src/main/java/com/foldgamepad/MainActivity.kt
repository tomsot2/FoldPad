package com.foldgamepad

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.foldgamepad.service.OverlayService
import com.foldgamepad.util.ConfigManager
import com.foldgamepad.util.InputInjector

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus:          TextView
    private lateinit var btnOverlayPerm:    Button
    private lateinit var btnNotifPerm:      Button
    private lateinit var btnAccessibility:  Button
    private lateinit var btnPickGame:       Button
    private lateinit var btnStartOverlay:   Button
    private lateinit var btnStopOverlay:    Button
    private lateinit var tvCurrentGame:     TextView

    // Runtime permission launcher for POST_NOTIFICATIONS (Android 13+)
    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus         = findViewById(R.id.tv_status)
        btnOverlayPerm   = findViewById(R.id.btn_overlay_perm)
        btnNotifPerm     = findViewById(R.id.btn_notif_perm)
        btnAccessibility = findViewById(R.id.btn_accessibility)
        btnPickGame      = findViewById(R.id.btn_pick_game)
        btnStartOverlay  = findViewById(R.id.btn_start_overlay)
        btnStopOverlay   = findViewById(R.id.btn_stop_overlay)
        tvCurrentGame    = findViewById(R.id.tv_current_game)

        btnOverlayPerm.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btnNotifPerm.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btnPickGame.setOnClickListener     { startActivity(Intent(this, AppPickerActivity::class.java)) }
        btnStartOverlay.setOnClickListener { startOverlay() }
        btnStopOverlay.setOnClickListener  { stopOverlay()  }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun hasNotifPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required below Android 13
        }
    }

    private fun refreshStatus() {
        val hasOverlay       = Settings.canDrawOverlays(this)
        val hasNotif         = hasNotifPermission()
        val hasAccessibility = InputInjector.isReady
        val cfg              = ConfigManager.load(this)
        val overlayRunning   = OverlayService.instance != null

        btnOverlayPerm.text      = if (hasOverlay) "✓ Overlay permission granted" else "1. Grant overlay permission"
        btnOverlayPerm.isEnabled = !hasOverlay

        btnNotifPerm.text      = if (hasNotif) "✓ Notification permission granted" else "2. Grant notification permission"
        btnNotifPerm.isEnabled = !hasNotif

        btnAccessibility.text      = if (hasAccessibility) "✓ Accessibility service active" else "3. Enable accessibility service"
        btnAccessibility.isEnabled = !hasAccessibility

        tvCurrentGame.text = if (cfg.gameName.isNotBlank()) "Game: ${cfg.gameName}" else "No game selected"

        btnPickGame.isEnabled     = hasOverlay && hasNotif
        btnStartOverlay.isEnabled = hasOverlay && hasNotif && !overlayRunning && cfg.gamePackage.isNotBlank()
        btnStopOverlay.isEnabled  = overlayRunning

        tvStatus.text = when {
            !hasOverlay       -> "Step 1: grant the overlay permission above."
            !hasNotif         -> "Step 2: grant notification permission (needed for the overlay service)."
            !hasAccessibility -> "Step 3: enable the FoldGamepad accessibility service."
            cfg.gamePackage.isBlank() -> "Step 4: pick a game."
            !overlayRunning   -> "Ready – tap Start Overlay."
            else              -> "Overlay active. Use the notification to edit layout or switch cover mode."
        }
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this) || !hasNotifPermission()) return
        val cfg = ConfigManager.load(this)
        startForegroundService(Intent(this, OverlayService::class.java))
        btnStartOverlay.postDelayed({
            OverlayService.instance?.launchGame(cfg.gamePackage, cfg.gameName)
            refreshStatus()
        }, 600)
    }

    private fun stopOverlay() {
        stopService(Intent(this, OverlayService::class.java))
        refreshStatus()
    }
}
