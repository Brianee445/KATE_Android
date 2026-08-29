package com.dti.kate.core

import android.content.Context
import com.dti.kate.network.KateApiClient

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
 * proxies to agent-router and caches by normalized query - so the routing
 * key never ships inside the APK, and repeat questions don't re-spend a
 * routing call.
 */
class AgentSearchService(private val context: Context) {

    private val apiClient = KateApiClient(context)

    /** @return the answer text, or null on any failure - not logged in, network, timeout, or the backend itself returning nothing useful. Callers should treat null as "genuinely no answer available" since this is the end of the fallback chain. */
    suspend fun ask(query: String): String? {
        return apiClient.search(query)?.answer?.takeIf { it.isNotBlank() }
    }
}
