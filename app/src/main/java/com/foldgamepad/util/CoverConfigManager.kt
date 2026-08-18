package com.foldgamepad.util

import android.content.Context
import com.foldgamepad.model.CoverLayout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object CoverConfigManager {
    private const val PREFS = "coverpad_prefs"
    private const val KEY   = "cover_layout"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun save(context: Context, layout: CoverLayout) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, json.encodeToString(layout)).apply()
    }

    fun load(context: Context): CoverLayout {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return CoverLayout()
        return try { json.decodeFromString(raw) } catch (e: Exception) { CoverLayout() }
    }
}
