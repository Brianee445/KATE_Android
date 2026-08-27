package com.dti.kate.core

import android.content.Context
import com.dti.kate.data.db.ConversationTurn
import com.dti.kate.data.db.KateDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How many turns of context the classifier gets to resolve references like
 * "what about tomorrow" or "tell him I said hi" against. Kept well below
 * MAX_STORED_TURNS - this is short-term working memory, not a transcript
 * search - but wide enough that a normal back-and-forth conversation
 * (several greetings/follow-ups in a row) doesn't fall out of context
 * mid-exchange, which is what happened at the previous value of 6. */
private const val CONTEXT_WINDOW = 20

/** Row cap enforced after every insert. Generous relative to CONTEXT_WINDOW
 * so re-opening the app soon after still has a bit of history, without the
 * table growing unbounded on installs that run for months. */
private const val MAX_STORED_TURNS = 200

/**
 * Bridges KateCommandProcessor/KateResponseGenerator (which need fast,
 * synchronous-feeling access mid-classification) to the Room-backed
 * ConversationDao (which is durable across process death, since the
 * overlay service and app process both get killed by Android regularly).
 *
 * Two kinds of memory live here, deliberately kept separate:
 *  - `recentTurns`: short-term, in-memory, reloaded from Room on init -
 *    resets its *ordering priority* each session but the underlying rows
 *    persist, so "what did I just ask" survives a process restart.
 *  - the user's name and other durable one-off facts: these are NOT
 *    turn-based, so they don't belong in conversation_turns at all - they
 *    live in LocalSettingsStore instead (see KateUserProfile below), which
 *    is the existing SharedPreferences store for exactly this kind of
 *    single durable value.
 */
class ConversationMemory(context: Context) {

    private val dao = KateDatabase.getInstance(context).conversationDao()

    /** In-memory cache, most-recent-last (chronological). Refreshed from
     * Room on construction so a fresh process still has recent context. */
    private var cache: MutableList<ConversationTurn> = mutableListOf()
    private var loaded = false

    private suspend fun ensureLoaded() {
        if (loaded) return
        cache = dao.getRecent(CONTEXT_WINDOW).asReversed().toMutableList()
        loaded = true
    }

    /** Call after every processed turn - fire-and-forget from the caller's
     * perspective (suspend, but cheap: one insert + a conditional trim). */
    suspend fun record(userText: String, kateReply: String, topic: String) {
        ensureLoaded()
        val turn = ConversationTurn(
            userText = userText,
            kateReply = kateReply,
            topic = topic,
            timestampMillis = System.currentTimeMillis(),
        )
        withContext(Dispatchers.IO) {
            dao.insert(turn)
            dao.trimTo(MAX_STORED_TURNS)
        }
        cache.add(turn)
        if (cache.size > CONTEXT_WINDOW) cache.removeAt(0)
    }

    /** Chronological (oldest first) list of the most recent turns, for
     * building classifier context. Empty on a fresh install. */
    suspend fun recentTurns(): List<ConversationTurn> {
        ensureLoaded()
        return cache.toList()
    }

    /** The topic of the single most recent turn, or null if there isn't
     * one yet - the common case callers actually need ("are we still
     * talking about the same thing"). */
    suspend fun lastTopic(): String? = recentTurns().lastOrNull()?.topic

    suspend fun clear() {
        withContext(Dispatchers.IO) { dao.clearAll() }
        cache.clear()
        loaded = true
    }
}
