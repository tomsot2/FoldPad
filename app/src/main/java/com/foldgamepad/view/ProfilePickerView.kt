package com.foldgamepad.view

import android.content.Context
import android.graphics.*
import android.text.InputType
import android.view.*
import android.widget.*
import com.foldgamepad.model.LayoutConfig
import com.foldgamepad.util.ProfileManager

/**
 * Full-screen overlay for switching, saving, and deleting named layout profiles.
 * Added to WindowManager by OverlayService when the profile pill is tapped.
 */
class ProfilePickerView(
    context: Context,
    private val currentConfig: LayoutConfig,
    private val onPickProfile: (name: String, config: LayoutConfig) -> Unit,
    private val onDismiss: () -> Unit
) : FrameLayout(context) {

    private val card: LinearLayout

    init {
        setBackgroundColor(Color.argb(160, 0, 0, 0))
        setOnClickListener { onDismiss() }

        card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(240, 14, 14, 22))
            setPadding(40, 40, 40, 40)
        }
        card.setOnClickListener { /* consume */ }

        addView(card, LayoutParams(900, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        })

        rebuild()
    }

    private fun rebuild() {
        card.removeAllViews()
        card.addView(titleText("Layout Profiles"))
        card.addView(divider())

        val profiles = ProfileManager.list(context)
        val active   = ProfileManager.getActive(context)
        profiles.forEach { card.addView(profileRow(it, it == active)) }

        card.addView(divider())
        card.addView(saveNewRow())
        card.addView(divider())
        card.addView(closeButton())
    }

    private fun profileRow(name: String, isActive: Boolean): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
        }
        row.addView(TextView(context).apply {
            text = if (isActive) "● $name" else "  $name"
            textSize = 20f
            setTextColor(if (isActive) Color.argb(255, 0, 220, 255) else Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        })
        if (!isActive) row.addView(pillButton("Load", Color.argb(220, 0, 120, 180)) {
            ProfileManager.load(context, name)?.let { cfg ->
                ProfileManager.setActive(context, name)
                onPickProfile(name, cfg)
            }
        })
        row.addView(pillButton("Save", Color.argb(220, 0, 140, 60)) {
            ProfileManager.save(context, name, currentConfig)
            rebuild()
        })
        if (name != ProfileManager.DEFAULT_NAME) row.addView(pillButton("✕", Color.argb(220, 180, 40, 40)) {
            ProfileManager.delete(context, name)
            rebuild()
        })
        return row
    }

    private fun saveNewRow(): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, 12, 0, 12)
        }
        val input = EditText(context).apply {
            hint = "New layout name…"; inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(Color.WHITE); setHintTextColor(Color.argb(120, 200, 200, 200))
            background = null; setPadding(8, 8, 8, 8); textSize = 18f
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(input)
        row.addView(pillButton("Save new", Color.argb(220, 60, 100, 0)) {
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) {
                ProfileManager.save(context, name, currentConfig)
                ProfileManager.setActive(context, name)
                onPickProfile(name, currentConfig)
            }
        })
        return row
    }

    private fun titleText(s: String) = TextView(context).apply {
        text = s; textSize = 24f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE); setPadding(0, 0, 0, 16)
    }

    private fun divider() = View(context).apply {
        setBackgroundColor(Color.argb(80, 0, 200, 255))
        layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1).apply { setMargins(0, 8, 0, 8) }
    }

    private fun pillButton(label: String, bg: Int, onClick: () -> Unit) =
        TextView(context).apply {
            text = label; textSize = 16f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); setBackgroundColor(bg)
            setPadding(24, 12, 24, 12); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                .apply { setMargins(8, 0, 0, 0) }
            setOnClickListener { onClick() }
        }

    private fun closeButton() = TextView(context).apply {
        text = "Close"; textSize = 18f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE); gravity = Gravity.CENTER
        setPadding(0, 20, 0, 4)
        layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        setOnClickListener { onDismiss() }
    }
}
