package com.dti.kate.network

import android.content.Context
import com.dti.kate.BuildConfig
import com.dti.kate.core.SecurePreferences
import com.dti.kate.network.models.*
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.IOException
import java.util.concurrent.TimeUnit

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
    
    @POST("api/v1/sync/logs")
    suspend fun uploadLogs(
        @Header("Authorization") token: String,
        @Body logs: List<SyncLogEntry>
    ): BaseResponse
    
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
                val newToken = refreshAccessToken()
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
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
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
    
    fun clearAuth() {
        securePrefs.clearTokens()
    }
}
