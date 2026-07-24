package com.dti.kate.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class WeatherResult(
    val temperatureC: Double,
    val description: String,
    val isDay: Boolean,
)

class WeatherService {

    suspend fun getCurrentWeather(lat: Double, lon: Double): WeatherResult? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code,is_day"

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val current = json.getJSONObject("current")

                WeatherResult(
                    temperatureC = current.getDouble("temperature_2m"),
                    description = describeWeatherCode(current.getInt("weather_code")),
                    isDay = current.getInt("is_day") == 1,
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    // WMO weather interpretation codes - https://open-meteo.com/en/docs
    private fun describeWeatherCode(code: Int): String = when (code) {
        0 -> "clear sky"
        1, 2 -> "mostly clear"
        3 -> "cloudy"
        45, 48 -> "foggy"
        51, 53, 55 -> "drizzling"
        61, 63, 65 -> "raining"
        66, 67 -> "freezing rain"
        71, 73, 75, 77 -> "snowing"
        80, 81, 82 -> "rain showers"
        85, 86 -> "snow showers"
        95 -> "thundery"
        96, 99 -> "a thunderstorm with hail"
        else -> "unclear conditions"
    }
}
