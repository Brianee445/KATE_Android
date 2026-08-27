package com.dti.kate.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One exchange: what the user said and what Kate replied. This is the raw
 * material conversation memory is built from - ConversationMemory (in
 * KateCommandProcessor's package) reads the most recent rows to give the
 * classifier short-term context (e.g. resolving "what about tomorrow"
 * against the previous turn's topic).
 *
 * Deliberately flat/denormalized (no separate "message" table with a role
 * column) - a turn is always a user+Kate pair in this app, there's no
 * multi-party chat, so one row per turn keeps queries simple.
 */
@Entity(tableName = "conversation_turns")
data class ConversationTurn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userText: String,
    val kateReply: String,
    /** Coarse category of what the turn was about, e.g. "weather", "smalltalk",
     * "alarm" - lets ConversationMemory answer "what did we just talk about"
     * without re-parsing kateReply's prose. */
    val topic: String,
    val timestampMillis: Long,
)
