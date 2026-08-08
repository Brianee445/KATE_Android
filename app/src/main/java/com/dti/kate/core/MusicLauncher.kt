package com.dti.kate.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

private const val TAG = "MusicLauncher"
private const val SPOTIFY_PACKAGE = "com.spotify.music"
private const val AUDIOMACK_PACKAGE = "com.audiomack"

enum class MusicApp { SPOTIFY, AUDIOMACK, NONE }

class MusicLauncher(private val context: Context) {

    private fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * Tries Spotify first (confirmed, documented spotify:search: deep link
     * - opens Spotify directly to search results for the song), then falls
     * back to just bringing Audiomack to the foreground if Spotify isn't
     * installed - Audiomack has no documented/reliable search deep link,
     * so rather than guess a URI scheme that might silently fail, this is
     * honest about only being able to open the app, not search within it.
     */
    fun playSong(song: String): MusicApp {
        if (isInstalled(SPOTIFY_PACKAGE)) {
            return if (openSpotifySearch(song)) MusicApp.SPOTIFY else MusicApp.NONE
        }
        if (isInstalled(AUDIOMACK_PACKAGE)) {
            return if (openAudiomack()) MusicApp.AUDIOMACK else MusicApp.NONE
        }
        return MusicApp.NONE
    }

    private fun openSpotifySearch(song: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(song)}")).apply {
                setPackage(SPOTIFY_PACKAGE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Spotify search: ${e.message}")
            false
        }
    }

    private fun openAudiomack(): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(AUDIOMACK_PACKAGE)
                ?: return false
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Audiomack: ${e.message}")
            false
        }
    }
}
