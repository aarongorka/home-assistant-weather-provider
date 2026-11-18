package com.aarongorka.hassweather

import android.content.Context
import androidx.core.content.edit

data class Settings(
    val url: String,
    val token: String,
    val entity: String,
    val hourlyEntity: String,
    val location: String,
    val intervalMinutes: Long,
    val gadgetbridgePackage: String,
) {
    val isConfigured get() = url.isNotBlank() && token.isNotBlank() && entity.isNotBlank()

    companion object {
        const val MIN_INTERVAL_MINUTES = 60L
        const val DEFAULT_INTERVAL_MINUTES = 60L
        private const val PREFS = "settings"

        fun load(context: Context): Settings {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return Settings(
                url = prefs.getString("url", "").orEmpty(),
                token = prefs.getString("token", "").orEmpty(),
                entity = prefs.getString("entity", "").orEmpty(),
                hourlyEntity = prefs.getString("hourly", "").orEmpty(),
                location = prefs.getString("location", "").orEmpty(),
                intervalMinutes = prefs.getLong("interval", DEFAULT_INTERVAL_MINUTES),
                gadgetbridgePackage = prefs.getString("package", "").orEmpty(),
            )
        }

        fun saveStatus(context: Context, status: String) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString("status", status) }

        fun loadStatus(context: Context): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("status", null)
    }

    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString("url", url.trim().trimEnd('/'))
            putString("token", token.trim())
            putString("entity", entity.trim())
            putString("hourly", hourlyEntity.trim())
            putString("location", location.trim())
            putLong("interval", intervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES))
            putString("package", gadgetbridgePackage.trim())
        }
    }
}
