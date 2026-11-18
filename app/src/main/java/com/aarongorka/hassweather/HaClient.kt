package com.aarongorka.hassweather

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HaClient(baseUrl: String, private val token: String) {
    private val base = baseUrl.trim().trimEnd('/')

    fun config(): JSONObject = JSONObject(request("GET", "/api/config"))

    fun state(entityId: String): JSONObject = JSONObject(request("GET", "/api/states/$entityId"))

    fun weatherEntities(): List<String> {
        val states = JSONArray(request("GET", "/api/states"))
        return (0 until states.length())
            .map { states.getJSONObject(it).getString("entity_id") }
            .filter { it.startsWith("weather.") }
            .sorted()
    }

    fun forecast(entityId: String, type: String): JSONArray {
        val body = JSONObject().put("entity_id", entityId).put("type", type)
        val response = JSONObject(request("POST", "/api/services/weather/get_forecasts?return_response", body))
        return response.getJSONObject("service_response").getJSONObject(entityId).getJSONArray("forecast")
    }

    private fun request(method: String, path: String, body: JSONObject? = null): String {
        val connection = URL(base + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = connection.responseCode
            val stream = if (code in HTTP_OK_RANGE) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(code in HTTP_OK_RANGE) { "HTTP $code from $path" }
            return text
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
        val HTTP_OK_RANGE = 200..299
    }
}
