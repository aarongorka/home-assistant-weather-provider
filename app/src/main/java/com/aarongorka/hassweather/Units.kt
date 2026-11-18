package com.aarongorka.hassweather

import org.json.JSONObject
import kotlin.math.ln

/** Converts Home Assistant values into the units Gadgetbridge's WeatherSpec expects. */
class UnitSet(attributes: JSONObject) {
    private val temperature = attributes.optString("temperature_unit")
    private val wind = attributes.optString("wind_speed_unit")
    private val pressure = attributes.optString("pressure_unit")
    private val visibility = attributes.optString("visibility_unit")

    fun kelvin(value: Double): Double = Units.toKelvin(value, temperature)

    fun kmh(value: Double): Double = Units.toKmh(value, wind)

    fun hectopascals(value: Double): Double = Units.toHectopascals(value, pressure)

    fun metres(value: Double): Double = Units.toMetres(value, visibility)
}

object Units {
    private const val KELVIN_OFFSET = 273.15
    private const val MAGNUS_A = 17.62
    private const val MAGNUS_B = 243.12

    private val compass = listOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
    )

    fun toKelvin(value: Double, unit: String?): Double = when (unit?.trim()?.lowercase()) {
        "°f" -> (value - 32) * 5 / 9 + KELVIN_OFFSET
        "k" -> value
        else -> value + KELVIN_OFFSET
    }

    fun toKmh(value: Double, unit: String?): Double = when (unit?.trim()?.lowercase()) {
        "m/s" -> value * 3.6
        "mph" -> value * 1.609344
        "kn" -> value * 1.852
        "ft/s" -> value * 1.09728
        "in/s" -> value * 0.09144
        else -> value
    }

    fun toHectopascals(value: Double, unit: String?): Double = when (unit?.trim()?.lowercase()) {
        "pa" -> value / 100
        "kpa", "cbar" -> value * 10
        "bar" -> value * 1000
        "mmhg" -> value * 1.33322387415
        "inhg" -> value * 33.8638866667
        "psi" -> value * 68.9475729318
        else -> value
    }

    fun toMetres(value: Double, unit: String?): Double = when (unit?.trim()?.lowercase()) {
        "km" -> value * 1000
        "mi" -> value * 1609.344
        "ft" -> value * 0.3048
        "yd" -> value * 0.9144
        "cm" -> value / 100
        "mm" -> value / 1000
        "in" -> value * 0.0254
        else -> value
    }

    /** Magnus approximation, taking and returning Kelvin. */
    fun dewPointKelvin(kelvin: Double, humidityPercent: Double): Double {
        if (humidityPercent <= 0) return kelvin
        val celsius = kelvin - KELVIN_OFFSET
        val gamma = ln(humidityPercent / 100) + MAGNUS_A * celsius / (MAGNUS_B + celsius)
        return MAGNUS_B * gamma / (MAGNUS_A - gamma) + KELVIN_OFFSET
    }

    /** Accepts degrees (number or numeric string) or a compass point such as "ENE". */
    fun windBearing(raw: Any?): Int? = when (raw) {
        is Number -> raw.toInt()
        is String -> raw.trim().toDoubleOrNull()?.toInt()
            ?: compass.indexOf(raw.trim().uppercase()).takeIf { it >= 0 }?.let { it * 360 / compass.size }
        else -> null
    }

    private val conditions = mapOf(
        "clear-night" to Condition(800, "clear"),
        "cloudy" to Condition(804, "cloudy"),
        "exceptional" to Condition(771, "exceptional"),
        "fog" to Condition(741, "fog"),
        "hail" to Condition(906, "hail"),
        "lightning" to Condition(210, "thunderstorm"),
        "lightning-rainy" to Condition(201, "thunderstorm with rain"),
        "partlycloudy" to Condition(802, "partly cloudy"),
        "pouring" to Condition(502, "heavy rain"),
        "rainy" to Condition(500, "rain"),
        "snowy" to Condition(600, "snow"),
        "snowy-rainy" to Condition(616, "rain and snow"),
        "sunny" to Condition(800, "sunny"),
        "windy" to Condition(771, "windy"),
        "windy-variant" to Condition(771, "windy and cloudy"),
    )

    /** OpenWeatherMap condition code, which is what Gadgetbridge uses. 3200 means unknown. */
    fun conditionCode(state: String?): Int = conditions[state]?.code ?: UNKNOWN_CONDITION

    fun conditionText(state: String?): String = conditions[state]?.text ?: state.orEmpty().replace('-', ' ')

    private const val UNKNOWN_CONDITION = 3200

    private data class Condition(val code: Int, val text: String)
}
