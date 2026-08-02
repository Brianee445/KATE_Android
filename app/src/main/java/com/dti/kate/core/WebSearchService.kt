package com.dti.kate.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WebSearchService {

    /**
     * Best-effort instant answer via DuckDuckGo's free API. Works well for
     * factual/definition-style queries ("what is photosynthesis", "capital
     * of France"). Returns null for queries needing live/real-time data
     * (scores, breaking news) since no free key-less API covers those -
     * callers should fall back to offering a browser search in that case.
     */
    suspend fun getInstantAnswer(query: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)

                val abstractText = json.optString("AbstractText", "")
                if (abstractText.isNotBlank()) return@withContext abstractText

                val answer = json.optString("Answer", "")
                if (answer.isNotBlank()) return@withContext answer

                val definition = json.optString("Definition", "")
                if (definition.isNotBlank()) return@withContext definition

                null
            } catch (e: Exception) {
                android.util.Log.e("WebSearchService", "getInstantAnswer failed for query: $query", e)
                null
            }
        }
    }
}
