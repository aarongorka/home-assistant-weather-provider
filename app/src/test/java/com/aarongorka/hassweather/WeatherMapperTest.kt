package com.aarongorka.hassweather

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class WeatherMapperTest {
    private val zone = ZoneId.of("Australia/Sydney")
    private val now = Instant.parse("2025-01-10T02:00:00Z") // 13:00 on the 10th, Sydney

    private val state =
        JSONObject(
            """{"state":"partlycloudy","attributes":{"temperature":20.0,"temperature_unit":"°C",
        "humidity":60,"wind_speed":10,"wind_speed_unit":"m/s","wind_bearing":"E"}}""",
        )

    private val daily =
        JSONArray(
            """[
        {"datetime":"2025-01-10T00:00:00","condition":"sunny","temperature":28,"templow":18,"precipitation_probability":10},
        {"datetime":"2025-01-11T00:00:00","condition":"rainy","temperature":22,"templow":16,"precipitation_probability":80}
        ]""",
        )

    private val hourly =
        JSONArray(
            """[
        {"datetime":"2025-01-10T14:00:00","condition":"cloudy","temperature":21,"humidity":55},
        {"datetime":"2025-01-05T14:00:00","condition":"cloudy","temperature":5}
        ]""",
        )

    private fun build() = WeatherMapper.build(
        WeatherInput(state, daily, hourly, null, null, -33.8, 151.2, "Home", zone, now),
    )

    @Test
    fun convertsCurrentConditions() {
        val json = build()
        assertEquals(293, json.getInt("currentTemp"))
        assertEquals(802, json.getInt("currentConditionCode"))
        assertEquals(36.0, json.getDouble("windSpeed"), 0.001)
        assertEquals(90, json.getInt("windDirection"))
        assertEquals(60, json.getInt("currentHumidity"))
        assertEquals(10, json.getInt("precipProbability"))
    }

    @Test
    fun todayIsExcludedFromForecastsButUsedForMinMax() {
        val json = build()
        assertEquals(301, json.getInt("todayMaxTemp"))
        assertEquals(291, json.getInt("todayMinTemp"))
        val forecasts = json.getJSONArray("forecasts")
        assertEquals(1, forecasts.length())
        assertEquals(500, forecasts.getJSONObject(0).getInt("conditionCode"))
    }

    @Test
    fun hourlyDropsPastEntries() {
        val hours = build().getJSONArray("hourly")
        assertEquals(1, hours.length())
        assertEquals(294, hours.getJSONObject(0).getInt("temp"))
    }

    @Test
    fun unavailableEntityIsRejected() {
        val bad = JSONObject("""{"state":"unavailable","attributes":{}}""")
        val result =
            runCatching {
                WeatherMapper.build(WeatherInput(bad, daily, hourly, null, null, null, null, "", zone, now))
            }
        assertTrue(result.isFailure)
    }

    @Test
    fun unitConversions() {
        assertEquals(273.15, Units.toKelvin(0.0, "°C"), 0.001)
        assertEquals(255.372, Units.toKelvin(0.0, "°F"), 0.001)
        assertEquals(1013.0, Units.toHectopascals(29.91, "inHg"), 1.0)
        assertEquals(1609.344, Units.toMetres(1.0, "mi"), 0.001)
        assertEquals(1.609344, Units.toKmh(1.0, "mph"), 0.001)
    }

    @Test
    fun bearings() {
        assertEquals(0, Units.windBearing("N"))
        assertEquals(292, Units.windBearing("wnw"))
        assertEquals(45, Units.windBearing(45.0))
        assertEquals(null, Units.windBearing("??"))
    }

    @Test
    fun unknownConditionFallsBack() {
        assertEquals(3200, Units.conditionCode("banana"))
        assertFalse(Units.conditionText(null).contains("null"))
    }

    @Test
    fun gzipRoundTrip() {
        val specs = JSONArray().put(build())
        val text = GadgetbridgeSender.gunzip(GadgetbridgeSender.gzip(specs))
        assertEquals(specs.toString(), text)
    }
}
