package com.dti.kate.core

import android.content.Context
import com.dti.kate.network.KateApiClient
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Last-resort answer source for KateAction.WebSearch, behind
 * WebSearchService (DuckDuckGo) and WikipediaService in the fallback
 * chain. Those two are free, keyless, and instant when they work - but
 * DuckDuckGo's instant-answer API only covers a narrow slice of queries
 * (definitions, a handful of well-known facts) and Wikipedia summaries
 * miss anything without a clean matching article title (ambiguous
 * "president" queries, grammar questions like "what is a noun", etc.) -
 * which in practice is most day-to-day questions. This routes those
 * through an LLM via the backend's /api/v1/search endpoint, which itself
 * proxies to AgentRouter and caches by normalized query - so the routing
 * key never ships inside the APK, and repeat questions don't re-spend a
 * routing call.
 */
class AgentSearchService(private val context: Context) {

    private val apiClient = KateApiClient(context)

    companion object {
        // The backend can be cold (Render free-tier spin-down after idle,
        // ~30-60s to wake) with no external signal to the client that
        // it's happening - previously this call had no timeout of its own
        // at all and inherited OkHttp's 60s default, which meant a cold
        // backend could leave the overlay sitting in PROCESSING for the
        // better part of a minute. In a voice UX where the user is
        // actively waiting with the mic cycle open, that's long enough to
        // get killed by aggressive OEM background management before ever
        // getting a response. Failing fast and falling through to "no
        // answer" (same as a DDG/Wikipedia miss) is much better UX than
        // hanging, and matches KateSttEngine's CLOUD_TIMEOUT_MS pattern
        // for the exact same reasoning on the transcription path.
        private const val TIMEOUT_MS = 10_000L
    }

    /** @return the answer text, or null on any failure - not logged in, network, timeout, or the backend itself returning nothing useful. Callers should treat null as "genuinely no answer available" since this is the end of the fallback chain. */
    suspend fun ask(query: String): String? {
        return withTimeoutOrNull(TIMEOUT_MS) {
            apiClient.search(query)?.answer?.takeIf { it.isNotBlank() }
        }
    }
}
