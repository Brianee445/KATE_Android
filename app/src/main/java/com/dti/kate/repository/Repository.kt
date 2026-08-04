package com.dti.kate.repository

import android.content.Context
import com.dti.kate.core.SecurePreferences
import com.dti.kate.network.KateApiClient
import com.dti.kate.network.models.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

class Repository @Inject constructor(@ApplicationContext private val context: Context) {

    private val apiClient = KateApiClient(context)
    private val securePrefs = SecurePreferences(context)
    private val api = apiClient.api

    // ==================== AUTH ====================

    suspend fun register(email: String, password: String, fullName: String?): Result<AuthResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.register(RegisterRequest(email, password, fullName))
                securePrefs.saveTokens(response.accessToken, response.refreshToken)
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun login(email: String, password: String): Result<AuthResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.login(LoginRequest(email, password))
                securePrefs.saveTokens(response.accessToken, response.refreshToken)
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun forgotPassword(email: String): Result<BaseResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.forgotPassword(ForgotPasswordRequest(email))
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun logout(): Result<BaseResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = securePrefs.getAccessToken() ?: return@withContext Result.failure(Exception("Not logged in"))
                val response = api.logout("Bearer $token")
                securePrefs.clearTokens()
                Result.success(response)
            } catch (e: Exception) {
                securePrefs.clearTokens()
                Result.failure(e)
            }
        }
    }

    suspend fun getCurrentUser(): Result<UserResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = securePrefs.getAccessToken() ?: return@withContext Result.failure(Exception("Not logged in"))
                val response = api.getCurrentUser("Bearer $token")
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ==================== CHAT ====================

    suspend fun sendChat(query: String): Result<ChatResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = securePrefs.getAccessToken() ?: return@withContext Result.failure(Exception("Not logged in"))
                val response = api.chat("Bearer $token", ChatRequest(query))
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getChatHistory(limit: Int = 50, offset: Int = 0, intentFilter: String? = null): Result<HistoryResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = securePrefs.getAccessToken() ?: return@withContext Result.failure(Exception("Not logged in"))
                val response = api.getChatHistory("Bearer $token", limit, offset, intentFilter)
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun clearChatHistory(): Result<BaseResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = securePrefs.getAccessToken() ?: return@withContext Result.failure(Exception("Not logged in"))
                val response = api.clearChatHistory("Bearer $token")
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ==================== SYNC ====================

    suspend fun syncConversations(): Result<SyncResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = securePrefs.getAccessToken() ?: return@withContext Result.failure(Exception("Not logged in"))
                val response = api.syncConversations("Bearer $token")
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun checkModelUpdates(currentVersion: String): Result<ModelUpdateResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = securePrefs.getAccessToken() ?: return@withContext Result.failure(Exception("Not logged in"))
                val response = api.checkModelUpdates("Bearer $token", currentVersion)
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ==================== PAYMENTS ====================

    suspend fun createCheckout(tier: String, provider: String): Result<CheckoutResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = securePrefs.getAccessToken() ?: return@withContext Result.failure(Exception("Not logged in"))
                val response = api.createCheckout("Bearer $token", CreateCheckoutRequest(tier, provider))
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun chat(query: String): Result<ChatResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = securePrefs.getAccessToken() ?: return@withContext Result.failure(Exception("Not logged in"))
                val response = api.chat("Bearer $token", ChatRequest(query = query))
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Uploads a batch of locally-logged voice interactions to the
     * training pipeline, gzipped as JSONL to match sync.py's actual
     * UploadFile contract. The caller (HomeScreen.maybeSyncVoiceLogs)
     * only removes entries from the local queue after this succeeds, so a
     * network failure here just means the batch gets retried next time.
     *
     * A 400 almost always means sync_training_enabled is off server-side
     * for this user - an intentional choice, not an error - so it's
     * still reported as success, since there's no reason to keep retrying
     * uploads that'll never be accepted while the setting stays off.
     */
    suspend fun uploadSyncLogs(batch: List<SyncLogEntry>): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val token = securePrefs.getAccessToken() ?: return@withContext Result.failure(Exception("Not logged in"))
                if (batch.isEmpty()) return@withContext Result.success(Unit)

                val jsonl = buildString {
                    for (entry in batch) {
                        append(
                            org.json.JSONObject().apply {
                                put("query", entry.query)
                                put("response", entry.response)
                                put("intent", entry.intent)
                                put("confidence", entry.confidence)
                                put("used_cloud", entry.usedCloud)
                                put("model_version", entry.modelVersion)
                                put("created_at", entry.createdAt)
                            }.toString()
                        )
                        append("\n")
                    }
                }

                val compressed = java.io.ByteArrayOutputStream().also { bos ->
                    java.util.zip.GZIPOutputStream(bos).use { it.write(jsonl.toByteArray(Charsets.UTF_8)) }
                }.toByteArray()

                val requestBody = compressed.toRequestBody("application/gzip".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", "voice_logs.jsonl.gz", requestBody)

                api.uploadLogs(token = "Bearer $token", file = part)
                Result.success(Unit)
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 400) Result.success(Unit) else Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getSubscriptionStatus(): Result<SubscriptionStatusResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = securePrefs.getAccessToken() ?: return@withContext Result.failure(Exception("Not logged in"))
                val response = api.getSubscriptionStatus("Bearer $token")
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun cancelSubscription(): Result<BaseResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = securePrefs.getAccessToken() ?: return@withContext Result.failure(Exception("Not logged in"))
                val response = api.cancelSubscription("Bearer $token")
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ==================== USER ====================

    suspend fun updateProfile(fullName: String? = null, syncTraining: Boolean? = null): Result<UserResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = securePrefs.getAccessToken() ?: return@withContext Result.failure(Exception("Not logged in"))
                val response = api.updateProfile("Bearer $token", UpdateProfileRequest(fullName, syncTraining))
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getUsage(): Result<UsageResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = securePrefs.getAccessToken() ?: return@withContext Result.failure(Exception("Not logged in"))
                val response = api.getUsage("Bearer $token")
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ==================== ADMIN ====================

    suspend fun verifyAdmin(passcode: String): Result<AdminVerifyResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = securePrefs.getAccessToken() ?: return@withContext Result.failure(Exception("Not logged in"))
                val response = api.verifyAdmin("Bearer $token", AdminVerifyRequest(passcode))
                if (response.valid) {
                    response.token?.let { adminToken ->
                        securePrefs.saveAdminToken(adminToken, response.role)
                    }
                }
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getAdminStats(): Result<AdminDashboardStats> {
        return withContext(Dispatchers.IO) {
            try {
                val adminToken = securePrefs.getAdminToken() ?: return@withContext Result.failure(Exception("Not admin"))
                val response = api.getAdminStats("Bearer $adminToken")
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getAdminErrors(limit: Int = 50, severity: String? = null): Result<AdminErrorsResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val adminToken = securePrefs.getAdminToken() ?: return@withContext Result.failure(Exception("Not admin"))
                val response = api.getAdminErrors("Bearer $adminToken", limit, severity)
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getAdminActivity(days: Int = 7): Result<AdminActivityResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val adminToken = securePrefs.getAdminToken() ?: return@withContext Result.failure(Exception("Not admin"))
                val response = api.getAdminActivity("Bearer $adminToken", days)
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getAdminUsers(page: Int = 1, limit: Int = 20, tier: String? = null): Result<AdminUsersResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val adminToken = securePrefs.getAdminToken() ?: return@withContext Result.failure(Exception("Not admin"))
                val response = api.getAdminUsers("Bearer $adminToken", page, limit, tier)
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getAdminSystemInfo(): Result<AdminSystemResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val adminToken = securePrefs.getAdminToken() ?: return@withContext Result.failure(Exception("Not admin"))
                val response = api.getAdminSystemInfo("Bearer $adminToken")
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun reportClientError(error: ClientErrorReport): Result<BaseResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = securePrefs.getAccessToken() ?: return@withContext Result.failure(Exception("Not logged in"))
                val response = api.reportClientError("Bearer $token", error)
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun clearAdminErrors(): Result<BaseResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val adminToken = securePrefs.getAdminToken() ?: return@withContext Result.failure(Exception("Not admin"))
                val response = api.clearAdminErrors("Bearer $adminToken")
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ==================== HELPERS ====================

    fun isAuthenticated(): Boolean = apiClient.isAuthenticated()
    fun isAdmin(): Boolean = securePrefs.isAdmin()
    fun getAdminRole(): String? = securePrefs.getAdminRole()

    fun logoutLocal() {
        securePrefs.clearTokens()
        securePrefs.clearAdminTokens()
    }
}
