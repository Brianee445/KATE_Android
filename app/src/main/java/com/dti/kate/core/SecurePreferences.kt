package com.dti.kate.core

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

class SecurePreferences(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "kate_secure_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_ADMIN_TOKEN = "admin_token"
        private const val KEY_ADMIN_ROLE = "admin_role"
    }
    
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    // ==================== TOKENS ====================
    
    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            apply()
        }
    }
    
    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)
    
    fun clearTokens() {
        prefs.edit().apply {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_ADMIN_TOKEN)
            remove(KEY_ADMIN_ROLE)
            apply()
        }
    }
    
    // ==================== DEVICE ID ====================
    fun getDeviceId(): String {
        return prefs.getString(KEY_DEVICE_ID, null) ?: run {
            val newId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
            newId
        }
    }
    
    // ==================== ADMIN ====================
    
    fun saveAdminToken(token: String, role: String) {
        prefs.edit().apply {
            putString(KEY_ADMIN_TOKEN, token)
            putString(KEY_ADMIN_ROLE, role)
            apply()
        }
    }
    
    fun getAdminToken(): String? = prefs.getString(KEY_ADMIN_TOKEN, null)
    fun getAdminRole(): String? = prefs.getString(KEY_ADMIN_ROLE, null)
    fun isAdmin(): Boolean = !getAdminToken().isNullOrEmpty()
    
    fun clearAdminTokens() {
        prefs.edit().apply {
            remove(KEY_ADMIN_TOKEN)
            remove(KEY_ADMIN_ROLE)
            apply()
        }
    }
}
