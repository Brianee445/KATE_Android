package com.dti.kate.network

import android.content.Context
import com.dti.kate.BuildConfig
import com.dti.kate.core.SecurePreferences
import com.dti.kate.network.models.*
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

interface KateApiService {
    
    // ==================== AUTH ====================
    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse
    
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse
    
    @POST("api/v1/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): AuthResponse
    
    @POST("api/v1/auth/logout")
    suspend fun logout(@Header("Authorization") token: String): BaseResponse
    
    @POST("api/v1/auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): BaseResponse
    
    @GET("api/v1/auth/me")
    suspend fun getCurrentUser(@Header("Authorization") token: String): UserResponse
    
    // ==================== CHAT ====================
    @POST("api/v1/chat")
    suspend fun chat(
        @Header("Authorization") token: String,
        @Body request: ChatRequest
    ): ChatResponse

    // ==================== TRANSCRIBE ====================
    // Raw PCM16 mono audio, proxied server-side to Deepgram - see
    // Repository.transcribeCloud() / KateSttEngine's "Kate Pro" mode,
    // which falls back to Kate Classic (Google) if this fails or times out.
    @Multipart
    @POST("api/v1/transcribe")
    suspend fun transcribe(
        @Header("Authorization") token: String,
        @Part audio: MultipartBody.Part,
        @Query("sample_rate") sampleRate: Int = 16000,
        @Query("channels") channels: Int = 1,
    ): TranscribeResponse

    // ==================== SEARCH ====================
    // LLM-routed answer, proxied server-side to agent-router - same
    // reasoning as transcribe() above, key never ships inside the APK.
    // Backend caches by normalized query so repeat questions are free.
    @POST("api/v1/search")
    suspend fun search(
        @Header("Authorization") token: String,
        @Body request: SearchRequest,
    ): SearchResponse
    
    @GET("api/v1/chat/history")
    suspend fun getChatHistory(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("intent_filter") intentFilter: String? = null
    ): HistoryResponse
    
    @DELETE("api/v1/chat/history")
    suspend fun clearChatHistory(@Header("Authorization") token: String): BaseResponse
    
    // ==================== SYNC ====================
    @POST("api/v1/sync/conversations")
    suspend fun syncConversations(@Header("Authorization") token: String): SyncResponse
    
    @GET("api/v1/sync/models")
    suspend fun checkModelUpdates(
        @Header("Authorization") token: String,
        @Query("current_version") currentVersion: String
    ): ModelUpdateResponse
    
    // ==================== PAYMENTS ====================
    @POST("api/v1/payments/create-checkout")
    suspend fun createCheckout(
        @Header("Authorization") token: String,
        @Body request: CreateCheckoutRequest
    ): CheckoutResponse
    
    @GET("api/v1/payments/subscription")
    suspend fun getSubscriptionStatus(@Header("Authorization") token: String): SubscriptionStatusResponse
    
    @POST("api/v1/payments/cancel")
    suspend fun cancelSubscription(@Header("Authorization") token: String): BaseResponse
    
    // ==================== USER ====================
    @PATCH("api/v1/user/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): UserResponse

    @GET("api/v1/user/usage")
    suspend fun getUsage(@Header("Authorization") token: String): UsageResponse

    // Gzipped JSONL file upload - matches the backend's actual UploadFile
    // contract in sync.py (NOT a JSON body; a prior version of this
    // declaration sent @Body List<SyncLogEntry>, which the server would
    // have rejected outright since it expects multipart/form-data).
    @Multipart
    @POST("api/v1/sync/logs")
    suspend fun uploadLogs(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part,
    ): Map<String, @JvmSuppressWildcards Any>
    
    @DELETE("api/v1/user/account")
    suspend fun deleteAccount(@Header("Authorization") token: String): BaseResponse
    
    // ==================== ADMIN ====================
    @POST("api/v1/admin/verify")
    suspend fun verifyAdmin(
        @Header("Authorization") token: String,
        @Body request: AdminVerifyRequest
    ): AdminVerifyResponse
    
    @GET("api/v1/admin/dashboard/stats")
    suspend fun getAdminStats(@Header("Authorization") token: String): AdminDashboardStats
    
    @GET("api/v1/admin/dashboard/errors")
    suspend fun getAdminErrors(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 50,
        @Query("severity") severity: String? = null
    ): AdminErrorsResponse
    
    @GET("api/v1/admin/dashboard/activity")
    suspend fun getAdminActivity(
        @Header("Authorization") token: String,
        @Query("days") days: Int = 7
    ): AdminActivityResponse
    
    @GET("api/v1/admin/dashboard/users")
    suspend fun getAdminUsers(
        @Header("Authorization") token: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("tier") tier: String? = null
    ): AdminUsersResponse
    
    @GET("api/v1/admin/dashboard/system")
    suspend fun getAdminSystemInfo(@Header("Authorization") token: String): AdminSystemResponse
    
    @POST("api/v1/admin/errors/clear")
    suspend fun clearAdminErrors(@Header("Authorization") token: String): BaseResponse
    
    @POST("api/v1/admin/errors/report")
    suspend fun reportClientError(
        @Header("Authorization") token: String,
        @Body error: ClientErrorReport
    ): BaseResponse
}

class KateApiClient(private val context: Context) {
    
    private val securePrefs = SecurePreferences(context)
    
    private val gson = GsonBuilder()
        .setLenient()
        .create()
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        })
        .addInterceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
            
            // Add auth token if available
            securePrefs.getAccessToken()?.let { token ->
                requestBuilder.header("Authorization", "Bearer $token")
            }
            
            // Add device info
            requestBuilder.header("X-Device-ID", securePrefs.getDeviceId())
            requestBuilder.header("X-App-Version", BuildConfig.VERSION_NAME)
            
            chain.proceed(requestBuilder.build())
        }
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            
            // Handle token refresh if needed
            if (response.code == 401) {
                response.close()
                // Try to refresh token
                val newToken = runBlocking { refreshAccessToken() }
                if (newToken != null) {
                    // Retry with new token
                    val newRequest = chain.request().newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .build()
                    return@addInterceptor chain.proceed(newRequest)
                }
            }
            response
        }
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BACKEND_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
    
    val api: KateApiService = retrofit.create(KateApiService::class.java)
    
    private suspend fun refreshAccessToken(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val refreshToken = securePrefs.getRefreshToken()
                if (refreshToken.isNullOrEmpty()) return@withContext null
                
                val response = api.refreshToken(RefreshTokenRequest(refreshToken))
                if (response.accessToken.isNotEmpty()) {
                    securePrefs.saveTokens(
                        response.accessToken,
                        response.refreshToken
                    )
                    return@withContext response.accessToken
                }
                null
            } catch (e: Exception) {
                null
            }
        }
    }
    
    fun isAuthenticated(): Boolean {
        return !securePrefs.getAccessToken().isNullOrEmpty()
    }

    /**
     * Uploads raw PCM16 mono audio for cloud transcription. Returns null on
     * any failure (network, timeout, no auth, server error) rather than
     * throwing - this is a best-effort accuracy enhancement, never a
     * required path, so callers should treat null exactly like "cloud
     * wasn't available right now" and fall back to Kate Classic.
     */
    suspend fun transcribeAudio(
        audioBytes: ByteArray,
        sampleRate: Int = 16000,
        channels: Int = 1,
    ): TranscribeResponse? {
        val token = securePrefs.getAccessToken() ?: return null
        return try {
            val requestBody = audioBytes.toRequestBody("audio/raw".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("audio", "audio.raw", requestBody)
            api.transcribe(
                token = "Bearer $token",
                audio = part,
                sampleRate = sampleRate,
                channels = channels,
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * LLM-routed answer for general Q&A, proxied through the backend.
     * Returns null on any failure (network, timeout, no auth, server
     * error) - same contract as transcribeAudio - callers should treat
     * null exactly like "no answer available" and fall back accordingly.
     */
    suspend fun search(query: String): SearchResponse? {
        val token = securePrefs.getAccessToken() ?: return null
        return try {
            api.search("Bearer $token", SearchRequest(query))
        } catch (e: Exception) {
            null
        }
    }

    fun clearAuth() {
        securePrefs.clearTokens()
    }
}
