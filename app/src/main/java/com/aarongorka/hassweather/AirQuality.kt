package com.aarongorka.hassweather

import org.json.JSONObject
import kotlin.math.roundToInt

/** Maps a Home Assistant air quality sensor to Gadgetbridge's `airQuality` object. */
object AirQuality {
    val deviceClasses = setOf("aqi", "pm25", "pm10")

    private data class Band(val concLow: Double, val concHigh: Double, val aqiLow: Int, val aqiHigh: Int)

    // US EPA breakpoints, concentrations in µg/m³.
    private val pm25Bands = listOf(
        Band(0.0, 9.0, 0, 50),
        Band(9.1, 35.4, 51, 100),
        Band(35.5, 55.4, 101, 150),
        Band(55.5, 125.4, 151, 200),
        Band(125.5, 225.4, 201, 300),
        Band(225.5, 325.4, 301, 500),
    )
    private val pm10Bands = listOf(
        Band(0.0, 54.0, 0, 50),
        Band(55.0, 154.0, 51, 100),
        Band(155.0, 254.0, 101, 150),
        Band(255.0, 354.0, 151, 200),
        Band(355.0, 424.0, 201, 300),
        Band(425.0, 604.0, 301, 500),
    )

    fun build(sensor: JSONObject?): JSONObject? {
        sensor ?: return null
        val value = sensor.optString("state").toDoubleOrNull() ?: return null
        val attrs = sensor.optJSONObject("attributes") ?: return null
        return when (attrs.optString("device_class")) {
            "aqi" -> JSONObject().put("aqi", value.roundToInt())
            "pm25" -> concentration(value, attrs, "pm25", pm25Bands)
            "pm10" -> concentration(value, attrs, "pm10", pm10Bands)
            else -> null
        }
    }

    private fun concentration(value: Double, attrs: JSONObject, key: String, bands: List<Band>): JSONObject? {
        val micrograms = when (attrs.optString("unit_of_measurement")) {
            "mg/m³" -> value * 1000
            else -> value
        }
        val band = bands.firstOrNull { micrograms <= it.concHigh } ?: bands.last()
        val fraction = ((micrograms - band.concLow) / (band.concHigh - band.concLow)).coerceIn(0.0, 1.0)
        val aqi = band.aqiLow + (band.aqiHigh - band.aqiLow) * fraction
        return JSONObject().put("aqi", aqi.roundToInt()).put(key, micrograms).put("${key}Aqi", aqi.roundToInt())
    }
}
