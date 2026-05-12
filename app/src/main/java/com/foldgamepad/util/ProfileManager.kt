package com.foldgamepad.util

import android.content.Context
import com.foldgamepad.model.LayoutConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ProfileManager {

    private const val PREFS      = "foldgamepad_profiles"
    private const val KEY_LIST   = "profile_names"
    private const val KEY_ACTIVE = "active_profile_name"
    const val DEFAULT_NAME       = "Default"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun list(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_LIST, null) ?: return listOf(DEFAULT_NAME)
        return raw.split("\n").filter { it.isNotBlank() }
    }

    fun load(context: Context, name: String): LayoutConfig? {
        val raw = prefs(context).getString(profileKey(name), null) ?: return null
        return try { json.decodeFromString(raw) } catch (e: Exception) { null }
    }

    fun save(context: Context, name: String, config: LayoutConfig) {
        val p     = prefs(context).edit()
        val names = list(context).toMutableList()
        if (name !in names) {
            names.add(name)
            p.putString(KEY_LIST, names.joinToString("\n"))
        }
        p.putString(profileKey(name), json.encodeToString(config))
        p.apply()
    }

    fun delete(context: Context, name: String) {
        if (name == DEFAULT_NAME) return
        val p     = prefs(context).edit()
        val names = list(context).toMutableList().also { it.remove(name) }
        p.putString(KEY_LIST, names.joinToString("\n"))
        p.remove(profileKey(name))
        p.apply()
        if (getActive(context) == name) setActive(context, DEFAULT_NAME)
    }

    fun getActive(context: Context): String =
        prefs(context).getString(KEY_ACTIVE, DEFAULT_NAME) ?: DEFAULT_NAME

    fun setActive(context: Context, name: String) =
        prefs(context).edit().putString(KEY_ACTIVE, name).apply()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun profileKey(name: String) = "profile_${name.trim()}"
}
