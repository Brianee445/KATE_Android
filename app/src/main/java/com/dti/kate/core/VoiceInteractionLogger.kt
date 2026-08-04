package com.dti.kate.core

import android.content.Context
import com.dti.kate.network.models.SyncLogEntry
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Local, append-only queue of voice interactions (query -> response/intent),
 * feeding the backend's sync/logs -> SyncLog -> training pipeline.
 *
 * That pipeline already existed server-side (R2 archival, training queue,
 * model versioning) but nothing ever populated it - voice commands are
 * handled entirely on-device and never touched the backend, and the
 * client's upload call was built against the wrong request format besides.
 * This is the missing capture side of that pipeline.
 *
 * Queue semantics (peekBatch + removeOldest) rather than read-all-then-clear:
 * a failed or partial upload should only drop what's actually confirmed
 * sent, not the whole local backlog - otherwise a flaky connection mid-sync
 * silently loses data instead of just retrying it next time.
 *
 * File-based (JSONL) rather than Room: this is purely an append/peek/trim
 * queue with no querying needs, which a flat file handles with far less
 * code than a database table would.
 */
object VoiceInteractionLogger {
    private const val LOG_FILE_NAME = "voice_interactions.jsonl"
    private val lock = ReentrantLock()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun logFile(context: Context): File = File(context.filesDir, LOG_FILE_NAME)

    fun logInteraction(
        context: Context,
        query: String,
        response: String,
        intent: String,
        confidence: Float,
        usedCloud: Boolean,
        modelVersion: String,
    ) {
        if (query.isBlank()) return
        if (!LocalSettingsStore(context).getSyncTrainingEnabled()) return
        lock.withLock {
            try {
                val entry = JSONObject().apply {
                    put("query", query)
                    put("response", response)
                    put("intent", intent)
                    put("confidence", confidence)
                    put("usedCloud", usedCloud)
                    put("modelVersion", modelVersion)
                    put("createdAt", dateFormat.format(Date()))
                }
                logFile(context).appendText(entry.toString() + "\n")
            } catch (_: Exception) {
                // Logging must never disrupt the actual voice interaction it's recording.
            }
        }
    }

    fun unsyncedCount(context: Context): Int = lock.withLock {
        try {
            logFile(context).takeIf { it.exists() }?.readLines()?.count { it.isNotBlank() } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    /** Returns up to [limit] oldest unsynced entries, without removing them. */
    fun peekBatch(context: Context, limit: Int): List<SyncLogEntry> = lock.withLock {
        try {
            val file = logFile(context)
            if (!file.exists()) return@withLock emptyList()
            file.readLines()
                .filter { it.isNotBlank() }
                .take(limit)
                .mapNotNull { line ->
                    try {
                        val json = JSONObject(line)
                        SyncLogEntry(
                            query = json.getString("query"),
                            response = json.optString("response", ""),
                            intent = json.optString("intent", "Unknown"),
                            confidence = json.optDouble("confidence", 0.0).toFloat(),
                            usedCloud = json.optBoolean("usedCloud", false),
                            modelVersion = json.optString("modelVersion", "unknown"),
                            createdAt = json.optString("createdAt", dateFormat.format(Date())),
                        )
                    } catch (_: Exception) {
                        null // skip a malformed line rather than fail the whole batch
                    }
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Removes the oldest [count] entries after they've been confirmed uploaded. */
    fun removeOldest(context: Context, count: Int) = lock.withLock {
        try {
            val file = logFile(context)
            if (!file.exists()) return@withLock
            val remaining = file.readLines().filter { it.isNotBlank() }.drop(count)
            file.writeText(if (remaining.isEmpty()) "" else remaining.joinToString("\n") + "\n")
        } catch (_: Exception) {
        }
    }
}
