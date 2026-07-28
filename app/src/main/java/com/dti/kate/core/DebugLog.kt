package com.dti.kate.core

import android.content.Context
import androidx.core.content.FileProvider
import android.content.Intent
import com.dti.kate.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A small, persistent, file-backed log for the speech pipeline
 * (VoskManager / native engine), independent of Logcat.
 *
 * Why this exists: on this device, the OS logcat main buffer gets flooded
 * by OEM noise (window-manager "Layer: Hide layer..." spam, Hiber/proxy
 * process-freezing chatter) badly enough that our own Log.d/LOGI lines get
 * evicted within seconds - a bug report pulled even a minute after
 * reproducing an issue shows none of our app's logging at all. Writing to
 * a file sidesteps that entirely.
 *
 * Capped at ~200KB (oldest entries dropped) so it can't grow unbounded.
 */
object DebugLog {
    private const val MAX_SIZE_BYTES = 200_000
    private const val TRIM_TO_BYTES = 150_000
    private val lock = ReentrantLock()
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    private fun logFile(context: Context): File =
        File(context.filesDir, "kate_debug.log")

    fun log(context: Context, tag: String, message: String) {
        lock.withLock {
            try {
                val file = logFile(context)
                val line = "${timeFormat.format(Date())} [$tag] $message\n"
                file.appendText(line)

                if (file.length() > MAX_SIZE_BYTES) {
                    val content = file.readText()
                    val trimmed = content.takeLast(TRIM_TO_BYTES)
                    // Drop a possibly-partial first line after trimming
                    val firstNewline = trimmed.indexOf('\n')
                    file.writeText(
                        if (firstNewline >= 0) trimmed.substring(firstNewline + 1) else trimmed
                    )
                }
            } catch (_: Exception) {
                // Logging must never crash the app it's trying to help debug.
            }
        }
    }

    fun clear(context: Context) {
        lock.withLock {
            try {
                logFile(context).writeText("")
            } catch (_: Exception) {
            }
        }
    }

    /** Builds a share intent for the current log file, or null if there's nothing to share. */
    fun exportShareIntent(context: Context): Intent? {
        val file = logFile(context)
        if (!file.exists() || file.length() == 0L) return null

        val uri = FileProvider.getUriForFile(
            context, "${BuildConfig.APPLICATION_ID}.fileprovider", file
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
