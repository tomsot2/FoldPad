package com.foldgamepad

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.foldgamepad.service.OverlayService
import com.foldgamepad.util.ConfigManager
import com.foldgamepad.util.InputInjector

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus:          TextView
    private lateinit var btnOverlayPerm:    Button
    private lateinit var btnAccessibility:  Button
    private lateinit var btnPickGame:       Button
    private lateinit var btnStartOverlay:   Button
    private lateinit var btnStopOverlay:    Button
    private lateinit var tvCurrentGame:     TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus         = findViewById(R.id.tv_status)
        btnOverlayPerm   = findViewById(R.id.btn_overlay_perm)
        btnAccessibility = findViewById(R.id.btn_accessibility)
        btnPickGame      = findViewById(R.id.btn_pick_game)
        btnStartOverlay  = findViewById(R.id.btn_start_overlay)
        btnStopOverlay   = findViewById(R.id.btn_stop_overlay)
        tvCurrentGame    = findViewById(R.id.tv_current_game)

        btnOverlayPerm.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
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

    private fun refreshStatus() {
        val hasOverlay       = Settings.canDrawOverlays(this)
        val hasAccessibility = InputInjector.isReady
        val cfg              = ConfigManager.load(this)
        val overlayRunning   = OverlayService.instance != null

        btnOverlayPerm.text      = if (hasOverlay) "✓ Overlay permission granted" else "1. Grant overlay permission"
        btnOverlayPerm.isEnabled = !hasOverlay

        btnAccessibility.text      = if (hasAccessibility) "✓ Accessibility service active" else "2. Enable accessibility service"
        btnAccessibility.isEnabled = !hasAccessibility

        tvCurrentGame.text = if (cfg.gameName.isNotBlank()) "Game: ${cfg.gameName}" else "No game selected"

        btnPickGame.isEnabled     = hasOverlay
        btnStartOverlay.isEnabled = hasOverlay && !overlayRunning && cfg.gamePackage.isNotBlank()
        btnStopOverlay.isEnabled  = overlayRunning

        tvStatus.text = when {
            !hasOverlay           -> "Step 1: grant the overlay permission above."
            !hasAccessibility     -> "Step 2: enable the FoldGamepad accessibility service."
            cfg.gamePackage.isBlank() -> "Step 3: pick a game."
            !overlayRunning       -> "Ready – tap Start Overlay."
            else                  -> "Overlay active. Use the notification to edit layout or switch cover mode."
        }
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) return
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
