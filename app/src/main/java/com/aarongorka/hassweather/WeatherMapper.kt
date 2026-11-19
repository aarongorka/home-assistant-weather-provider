package com.aarongorka.hassweather

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

/** Everything needed to build one Gadgetbridge WeatherSpec. */
data class WeatherInput(
    val state: JSONObject,
    val daily: JSONArray,
    val hourly: JSONArray,
    val hourlyState: JSONObject?,
    val sun: JSONObject?,
    val latitude: Double?,
    val longitude: Double?,
    val locationName: String,
    val zone: ZoneId,
    val now: Instant,
    val airQuality: JSONObject? = null,
)

/** Builds the JSON that Gadgetbridge's GenericWeatherReceiver parses into a WeatherSpec. */
object WeatherMapper {
    private const val MAX_DAYS = 10
    private const val HOURLY_WINDOW_SECONDS = 48 * 3600L
    private const val DAY_SECONDS = 86_400L
    private val unavailable = setOf("unavailable", "unknown")

    fun build(input: WeatherInput): JSONObject {
        val state = input.state.getString("state")
        require(state !in unavailable) { "Weather entity is $state" }
        val attrs = input.state.getJSONObject("attributes")
        val units = UnitSet(attrs)
        val zone = input.zone
        val today = input.now.atZone(zone).toLocalDate()

        val days = input.daily.objects().mapNotNull { item -> forecastTime(item, zone)?.let { it to item } }
        val todayItem = days.firstOrNull { (time, _) -> time.atZone(zone).toLocalDate() == today }?.second
        val future = days.filter { (time, _) -> time.atZone(zone).toLocalDate().isAfter(today) }
            .sortedBy { it.first }
            .take(MAX_DAYS)
            .mapNotNull { (_, item) -> dailyJson(item, units) }

        val temp = units.kelvin(requireNotNull(attrs.number("temperature")) { "Entity has no temperature" })
        val humidity = attrs.number("humidity")

        val json = JSONObject()
            .put("timestamp", input.now.epochSecond)
            .put("location", input.locationName)
            .put("isCurrentLocation", 0)
            .put("currentTemp", temp.roundToInt())
            .put("currentConditionCode", Units.conditionCode(state))
            .put("currentCondition", Units.conditionText(state))
            .put("todayMinTemp", todayTemp(todayItem, "templow", units, temp))
            .put("todayMaxTemp", todayTemp(todayItem, "temperature", units, temp))
            .put("forecasts", JSONArray(future))
        json.putIfPresent("latitude", input.latitude)
        json.putIfPresent("longitude", input.longitude)
        json.putIfPresent("currentHumidity", humidity?.roundToInt())
        json.putIfPresent("windSpeed", attrs.number("wind_speed")?.let(units::kmh))
        json.putIfPresent("windDirection", Units.windBearing(attrs.opt("wind_bearing")))
        json.putIfPresent("uvIndex", attrs.number("uv_index"))
        json.putIfPresent("pressure", attrs.number("pressure")?.let(units::hectopascals))
        json.putIfPresent("cloudCover", attrs.number("cloud_coverage")?.roundToInt())
        json.putIfPresent("visibility", attrs.number("visibility")?.let(units::metres))
        json.putIfPresent("feelsLikeTemp", attrs.number("apparent_temperature")?.let(units::kelvin)?.roundToInt())
        json.putIfPresent(
            "dewPoint",
            (attrs.number("dew_point")?.let(units::kelvin) ?: humidity?.let { Units.dewPointKelvin(temp, it) })
                ?.roundToInt(),
        )
        json.putIfPresent(
            "precipProbability",
            (todayItem ?: days.firstOrNull()?.second)?.number("precipitation_probability")?.roundToInt(),
        )
        putSun(json, input.sun)
        json.putIfPresent("airQuality", AirQuality.build(input.airQuality))

        val hourlyUnits = input.hourlyState?.optJSONObject("attributes")?.let(::UnitSet) ?: units
        val hourlyStart = input.now.epochSecond - 3600
        val hourly = input.hourly.objects()
            .mapNotNull { item -> forecastTime(item, zone)?.let { it to item } }
            .filter { (time, _) -> time.epochSecond in hourlyStart..(input.now.epochSecond + HOURLY_WINDOW_SECONDS) }
            .sortedBy { it.first }
            .mapNotNull { (time, item) -> hourlyJson(time, item, hourlyUnits) }
        json.put("hourly", JSONArray(hourly))
        return json
    }

    private fun todayTemp(item: JSONObject?, key: String, units: UnitSet, fallback: Double): Int {
        val value = item?.number(key) ?: item?.number("temperature")
        return (value?.let(units::kelvin) ?: fallback).roundToInt()
    }

    private fun putSun(json: JSONObject, sun: JSONObject?) {
        val attrs = sun?.optJSONObject("attributes") ?: return
        val rise = attrs.optString("next_rising").toEpochOrNull() ?: return
        val set = attrs.optString("next_setting").toEpochOrNull() ?: return
        // The "next" events straddle today and tomorrow; rise must precede set within a single day.
        json.put("sunRise", if (rise > set) rise - DAY_SECONDS else rise)
        json.put("sunSet", set)
    }

    private fun String.toEpochOrNull(): Long? = runCatching { OffsetDateTime.parse(this).toEpochSecond() }.getOrNull()

    private fun dailyJson(item: JSONObject, units: UnitSet): JSONObject? {
        val high = item.number("temperature") ?: return null
        val low = item.number("templow") ?: high
        val json = JSONObject()
            .put("conditionCode", Units.conditionCode(item.optString("condition")))
            .put("maxTemp", units.kelvin(high).roundToInt())
            .put("minTemp", units.kelvin(low).roundToInt())
        json.putIfPresent("humidity", item.number("humidity")?.roundToInt())
        json.putIfPresent("windSpeed", item.number("wind_speed")?.let(units::kmh))
        json.putIfPresent("windDirection", Units.windBearing(item.opt("wind_bearing")))
        json.putIfPresent("uvIndex", item.number("uv_index"))
        json.putIfPresent("precipProbability", item.number("precipitation_probability")?.roundToInt())
        json.putIfPresent("pressure", item.number("pressure")?.let(units::hectopascals))
        json.putIfPresent("cloudCover", item.number("cloud_coverage")?.roundToInt())
        return json
    }

    private fun hourlyJson(time: Instant, item: JSONObject, units: UnitSet): JSONObject? {
        val temp = units.kelvin(item.number("temperature") ?: return null)
        val humidity = item.number("humidity")
        val json = JSONObject()
            .put("timestamp", time.epochSecond)
            .put("temp", temp.roundToInt())
            .put("conditionCode", Units.conditionCode(item.optString("condition")))
        json.putIfPresent("humidity", humidity?.roundToInt())
        json.putIfPresent("windSpeed", item.number("wind_speed")?.let(units::kmh))
        json.putIfPresent("windDirection", Units.windBearing(item.opt("wind_bearing")))
        json.putIfPresent("uvIndex", item.number("uv_index"))
        json.putIfPresent("precipProbability", item.number("precipitation_probability")?.roundToInt())
        json.putIfPresent("pressure", item.number("pressure")?.let(units::hectopascals))
        json.putIfPresent("cloudCover", item.number("cloud_coverage")?.roundToInt())
        json.putIfPresent("dewPoint", humidity?.let { Units.dewPointKelvin(temp, it).roundToInt() })
        return json
    }

    /** Offset-less timestamps are in Home Assistant's own time zone. */
    fun forecastTime(item: JSONObject, zone: ZoneId): Instant? {
        val raw = item.optString("datetime").ifBlank { return null }
        return runCatching { OffsetDateTime.parse(raw).toInstant() }
            .recoverCatching { LocalDateTime.parse(raw).atZone(zone).toInstant() }
            .recoverCatching { LocalDate.parse(raw).atStartOfDay(zone).toInstant() }
            .getOrNull()
    }

    private fun JSONObject.number(name: String): Double? =
        if (has(name) && !isNull(name)) optDouble(name).takeUnless { it.isNaN() } else null

    private fun JSONObject.putIfPresent(name: String, value: Any?) {
        if (value != null) put(name, value)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
}
