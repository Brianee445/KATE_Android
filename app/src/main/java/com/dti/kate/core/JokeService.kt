package com.dti.kate.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Free, keyless joke source (JokeAPI - https://jokeapi.dev). Category is
 * driven by the tone slider so "sassy" mode gets edgier material and
 * "professional" stays safe-for-work: JokeAPI's blacklistFlags param lets
 * us exclude categories per request rather than filtering client-side
 * after the fact.
 */
class JokeService {

    suspend fun getJoke(tone: KateTone): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = buildUrl(tone)
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)

                if (json.optBoolean("error", false)) return@withContext null

                when (json.optString("type")) {
                    "single" -> json.optString("joke").ifBlank { null }
                    "twopart" -> {
                        val setup = json.optString("setup")
                        val delivery = json.optString("delivery")
                        if (setup.isBlank() || delivery.isBlank()) null
                        // Both parts spoken together in one TTS call - the
                        // overlay has no pause/beat mechanism between two
                        // separate utterances, so a dramatic pause here
                        // would need a second speak() call and a delay,
                        // which isn't worth the complexity for a joke.
                        else "$setup ... $delivery"
                    }
                    else -> null
                }
            } catch (e: Exception) {
                android.util.Log.e("JokeService", "getJoke failed", e)
                null
            }
        }
    }

    private fun buildUrl(tone: KateTone): String {
        // JokeAPI categories: Programming, Misc, Dark, Pun, Spooky, Christmas.
        // Professional: keep it to Pun/Misc and blacklist anything spicy.
        // Balanced: allow a wider default set, still blacklist the roughest flags.
        // Sassy: allow Dark humor through, still blacklist slurs/racist/sexist
        // categories regardless of tone - "sassy" means edgier, not offensive.
        val (categories, blacklist) = when (tone) {
            KateTone.PROFESSIONAL -> "Pun,Misc" to "nsfw,religious,political,racist,sexist,explicit"
            KateTone.BALANCED -> "Any" to "nsfw,religious,political,racist,sexist,explicit"
            KateTone.SASSY -> "Dark,Pun,Misc" to "racist,sexist,explicit"
        }
        return "https://v2.jokeapi.dev/joke/$categories?blacklistFlags=$blacklist&format=json"
    }
}
