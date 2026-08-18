package com.foldgamepad

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.foldgamepad.service.CalibrationOverlayService
import com.foldgamepad.service.CoverOverlayService

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        val title = TextView(this).apply {
            text = "CoverPad Triggers"
            textSize = 24f
            setPadding(0, 0, 0, 48)
        }
        root.addView(title)

        val desc = TextView(this).apply {
            text = "1. Enable Accessibility below\n" +
                   "2. Calibrate targets while a game is open (unfolded)\n" +
                   "3. Fold the phone and start the cover triggers\n" +
                   "4. Press the edges to fire taps on the game"
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
            text = "2. Calibrate Trigger Targets (do this unfolded, in-game)"
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
                    "Cover triggers active — fold the phone",
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
