package com.dti.kate.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fallback knowledge source for WebSearchService.getInstantAnswer misses.
 * DuckDuckGo's free instant-answer API only returns something for a
 * narrow slice of queries (definitions, a handful of well-known facts) -
 * anything about a specific person, place, film, historical event, etc.
 * usually comes back empty. Wikipedia's REST summary endpoint covers a
 * much wider surface for exactly that kind of "who/what is X" query and
 * is free and keyless, same as DuckDuckGo.
 */
class WikipediaService {

    suspend fun getSummary(query: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val title = URLEncoder.encode(query.trim(), "UTF-8")
                val url = "https://en.wikipedia.org/api/rest_v1/page/summary/$title"

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("User-Agent", "KateAssistant/1.0")

                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)

                // "disambiguation" type means Wikipedia couldn't pick one
                // article for this title - the extract in that case is just
                // a list of options, not useful spoken aloud.
                if (json.optString("type") == "disambiguation") return@withContext null

                val extract = json.optString("extract", "")
                extract.ifBlank { null }
            } catch (e: Exception) {
                android.util.Log.e("WikipediaService", "getSummary failed for query: $query", e)
                null
            }
        }
    }
}
