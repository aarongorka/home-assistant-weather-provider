package com.aarongorka.hassweather

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object WeatherSync {
    private const val WORK_NAME = "weather-sync"

    /** Blocking; call off the main thread. Returns a human-readable summary. */
    fun run(context: Context, settings: Settings): String {
        val client = HaClient(settings.url, settings.token)
        val config = client.config()
        val sun = runCatching { client.state("sun.sun") }.getOrNull()
        val hourlyEntity = settings.hourlyEntity.ifBlank { settings.entity }
        val input = WeatherInput(
            state = client.state(settings.entity),
            daily = runCatching { client.forecast(settings.entity, "daily") }.getOrDefault(org.json.JSONArray()),
            hourly = runCatching { client.forecast(hourlyEntity, "hourly") }.getOrDefault(org.json.JSONArray()),
            hourlyState = runCatching { client.state(hourlyEntity) }.getOrNull(),
            sun = sun,
            latitude = config.optDouble("latitude").takeUnless { it.isNaN() },
            longitude = config.optDouble("longitude").takeUnless { it.isNaN() },
            locationName = settings.location.ifBlank { config.optString("location_name") },
            zone = runCatching { ZoneId.of(config.optString("time_zone")) }.getOrDefault(ZoneId.systemDefault()),
            now = Instant.now(),
        )
        val spec = WeatherMapper.build(input)
        val sent = GadgetbridgeSender.send(context, spec, settings.gadgetbridgePackage)
        val summary = "${java.time.LocalTime.now().withNano(0)} ${spec.getInt("currentTemp") - 273}°C, " +
            "${spec.getJSONArray("forecasts").length()}d/${spec.getJSONArray("hourly").length()}h → " +
            if (sent.isEmpty()) "no Gadgetbridge found" else sent.joinToString()
        Settings.saveStatus(context, summary)
        return summary
    }

    fun schedule(context: Context, settings: Settings) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(settings.intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = Settings.load(applicationContext)
        if (!settings.isConfigured) return Result.success()
        return runCatching { WeatherSync.run(applicationContext, settings) }
            .fold(
                onSuccess = { Result.success() },
                onFailure = {
                    Settings.saveStatus(applicationContext, "Failed: ${it.message}")
                    Result.retry()
                },
            )
    }
}
