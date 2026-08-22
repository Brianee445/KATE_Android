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
 * Two resolution strategies are used together (see VoskManager's grammar
 * support and HomeScreen's call/message handling):
 *  1. Grammar-constrained recognition (preferred) - Vosk is told the exact
 *     set of contact names up front for that utterance, so the decoder's
 *     own acoustic search is steered toward the closest real name using
 *     its full acoustic model, not just string similarity after the fact.
 *  2. Fuzzy matching (this class, [findBestMatch]) - a fallback for when
 *     open-vocabulary recognition already produced some text and grammar
 *     re-listening isn't available or didn't help. Works directly on
 *     whatever text came out, which may itself be a garbled guess at a
 *     name Vosk's acoustic model has never seen - so this is a genuine
 *     fallback, not the primary defense, especially for non-English names.
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
