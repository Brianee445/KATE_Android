package com.dti.kate.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Contact(val name: String, val phoneNumber: String)

/**
 * Reads device contacts and resolves a spoken name against them.
 *
 * Fuzzy string matching ([findBestMatch]) is now the only resolution
 * strategy - see HomeScreen.resolveContact(). Previously, a
 * grammar-constrained re-listen (Vosk told the exact set of contact names
 * up front, steering its own acoustic search toward the closest real name)
 * ran first as a stronger primary defense. That's gone along with Vosk:
 * neither Google's RecognizerIntent nor Deepgram's simple API this app
 * talks to support per-call custom vocabulary the same way, so this class
 * is doing more work than before - names outside common usage (many
 * African names included) may need a retry more often as a result.
 */
class ContactsHelper(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Returns all contacts with a phone number, deduplicated by name (first
     * number wins). suspend + withContext(Dispatchers.IO) - this was
     * previously a plain blocking fun, and every call site invoked it
     * directly from a UI-thread coroutine (tap gesture handlers,
     * LaunchedEffect) with no dispatch off Main. On a slow device with a
     * large contacts list, ContactsProvider2's cross-process query +
     * cursor iteration took long enough to hit Android's 5-second input
     * dispatch timeout - confirmed via ANR trace, main thread blocked
     * directly inside CursorWrapper.getString() called from this function.
     */
    suspend fun getAllContacts(): List<Contact> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val contacts = LinkedHashMap<String, String>() // name -> number, dedup by name
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection, null, null, null,
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx)?.trim().orEmpty()
                val number = cursor.getString(numberIdx)?.trim().orEmpty()
                if (name.isNotEmpty() && number.isNotEmpty() && !contacts.containsKey(name)) {
                    contacts[name] = number
                }
            }
        }

        contacts.map { (name, number) -> Contact(name, number) }
    }

    /**
     * Finds the closest contact name to [spokenText] using normalized
     * Levenshtein distance, or null if nothing clears [minSimilarity].
     *
     * minSimilarity defaults conservatively (0.5) since this runs on
     * already-garbled STT output for names the acoustic model likely
     * never saw in training - a looser threshold here would start
     * confidently calling/messaging the wrong person, which is worse
     * than asking the user to repeat themselves.
     */
    fun findBestMatch(spokenText: String, contacts: List<Contact>, minSimilarity: Double = 0.5): Contact? {
        if (spokenText.isBlank() || contacts.isEmpty()) return null

        val query = spokenText.trim().lowercase()
        var best: Contact? = null
        var bestScore = 0.0

        for (contact in contacts) {
            // Compare against the full name and each individual name part
            // (first/last), since a spoken "chidinma" should match a
            // contact stored as "Chidinma Okafor" even though the full
            // strings differ a lot.
            val candidates = listOf(contact.name.lowercase()) + contact.name.lowercase().split(" ")
            val score = candidates.maxOf { similarity(query, it) }
            if (score > bestScore) {
                bestScore = score
                best = contact
            }
        }

        return if (bestScore >= minSimilarity) best else null
    }

    private fun similarity(a: String, b: String): Double {
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / maxLen
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[a.length][b.length]
    }
}
