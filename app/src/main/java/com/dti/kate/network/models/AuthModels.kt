package com.dti.kate.network.models

import com.google.gson.annotations.SerializedName

// ==================== REQUEST MODELS ====================

data class RegisterRequest(
    val email: String,
    val password: String,
    @SerializedName("full_name") val fullName: String? = null,
)

data class LoginRequest(
    val email: String,
    val password: String,
)

data class RefreshTokenRequest(
    @SerializedName("refresh_token") val refreshToken: String,
)

data class ForgotPasswordRequest(
    val email: String,
)

data class UpdateProfileRequest(
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("sync_training_enabled") val syncTrainingEnabled: Boolean? = null,
    @SerializedName("wake_triggers") val wakeTriggers: Map<String, Boolean>? = null,
)

data class AdminVerifyRequest(
    val passcode: String,
)

// ==================== RESPONSE MODELS ====================

data class AuthResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    val user: UserResponse,
)

data class UserResponse(
    val id: String,
    val email: String,
    @SerializedName("full_name") val fullName: String?,
    val tier: String,
    @SerializedName("usage_count") val usageCount: Int,
    @SerializedName("sync_training_enabled") val syncTrainingEnabled: Boolean,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("is_superuser") val isSuperuser: Boolean,
    @SerializedName("created_at") val createdAt: String,
)

data class BaseResponse(
    val status: String,
    val message: String,
)

data class UsageResponse(
    @SerializedName("total_requests") val totalRequests: Int,
    @SerializedName("monthly_requests") val monthlyRequests: Int,
    @SerializedName("tier_limit") val tierLimit: Int,
    val remaining: Int,
    val tier: String,
)

// ==================== CHAT MODELS ====================

data class ChatRequest(
    val query: String,
    @SerializedName("context") val context: Map<String, Any>? = null,
)

data class ChatResponse(
    val query: String,
    val response: String,
    val intent: String,
    val confidence: Float,
    @SerializedName("used_cloud") val usedCloud: Boolean,
    @SerializedName("latency_ms") val latencyMs: Int,
    @SerializedName("requires_payment") val requiresPayment: Boolean,
)

data class HistoryResponse(
    val conversations: List<ConversationHistoryItem>,
    val limit: Int,
    val offset: Int,
)

data class ConversationHistoryItem(
    val id: String,
    val query: String,
    val response: String,
    val intent: String,
    val confidence: Float,
    @SerializedName("used_cloud") val usedCloud: Boolean,
    @SerializedName("created_at") val createdAt: String,
)

// ==================== SYNC MODELS ====================

data class SyncResponse(
    val status: String,
    val message: String,
    @SerializedName("processed_count") val processedCount: Int,
)

data class ModelUpdateResponse(
    val available: Boolean,
    val message: String,
    val version: String? = null,
    val url: String? = null,
    @SerializedName("size_bytes") val sizeBytes: Long? = null,
    val checksum: String? = null,
    @SerializedName("release_notes") val releaseNotes: String? = null,
    @SerializedName("current_version") val currentVersion: String? = null,
)

data class SyncLogEntry(
    val query: String,
    val response: String,
    val intent: String,
    val confidence: Float,
    @SerializedName("used_cloud") val usedCloud: Boolean,
    @SerializedName("model_version") val modelVersion: String,
    @SerializedName("created_at") val createdAt: String,
)

// ==================== PAYMENT MODELS ====================

data class CreateCheckoutRequest(
    val tier: String, // "premium", "pro", "lifetime"
    val provider: String, // "stripe", "paystack"
)

data class CheckoutResponse(
    @SerializedName("checkout_url") val checkoutUrl: String,
    val provider: String,
    val tier: String,
    @SerializedName("session_id") val sessionId: String? = null,
)

data class SubscriptionStatusResponse(
    val tier: String,
    val status: String,
    val provider: String?,
    @SerializedName("expires_at") val expiresAt: String?,
    @SerializedName("is_active") val isActive: Boolean,
)

// ==================== ADMIN MODELS ====================

data class AdminVerifyResponse(
    val valid: Boolean,
    val role: String,
    @SerializedName("is_admin") val isAdmin: Boolean,
    val token: String? = null,
    val message: String,
)

data class AdminDashboardStats(
    val users: AdminUserStats,
    val conversations: AdminConversationStats,
    val revenue: AdminRevenueStats,
    val errors: AdminErrorStats,
    val models: AdminModelStats,
    val timestamp: String,
)

data class AdminUserStats(
    val total: Int,
    @SerializedName("new_today") val newToday: Int,
    @SerializedName("active_24h") val active24h: Int,
    val premium: Int,
)

data class AdminConversationStats(
    val total: Int,
    val today: Int,
    @SerializedName("intent_accuracy") val intentAccuracy: Double,
)

data class AdminRevenueStats(
    @SerializedName("total_revenue") val totalRevenue: Double,
    @SerializedName("monthly_revenue") val monthlyRevenue: Double,
    @SerializedName("growth_rate") val growthRate: Double,
)

data class AdminErrorStats(
    val total: Int,
    val critical: Int,
    val warnings: Int,
    @SerializedName("last_24h") val last24h: Int,
)

data class AdminModelStats(
    @SerializedName("active_version") val activeVersion: String,
    @SerializedName("latest_version") val latestVersion: String,
    @SerializedName("is_outdated") val isOutdated: Boolean,
    @SerializedName("last_trained_at") val lastTrainedAt: String,
)

data class AdminErrorsResponse(
    val errors: List<AdminErrorItem>,
    val count: Int,
    val timestamp: String,
)

data class AdminErrorItem(
    @SerializedName("error_code") val errorCode: String,
    val message: String,
    val severity: String,
    val category: String,
    val timestamp: String,
    val path: String? = null,
    val method: String? = null,
    @SerializedName("request_id") val requestId: String? = null,
    val details: Map<String, Any>? = null,
    @SerializedName("user_id") val userId: String? = null,
    val source: String? = null,
)

data class AdminActivityResponse(
    val activity: List<AdminActivityItem>,
    val days: Int,
    val timestamp: String,
)

data class AdminActivityItem(
    val date: String,
    val conversations: Int,
)

data class AdminUsersResponse(
    val users: List<AdminUserItem>,
    val pagination: AdminPagination,
    val timestamp: String,
)

data class AdminUserItem(
    val id: String,
    val email: String,
    @SerializedName("full_name") val fullName: String?,
    val tier: String,
    @SerializedName("usage_count") val usageCount: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("last_login_at") val lastLoginAt: String?,
)

data class AdminPagination(
    val page: Int,
    val limit: Int,
    val total: Int,
    val pages: Int,
)

data class AdminSystemResponse(
    val system: Map<String, Any>,
    val timestamp: String,
)

data class ClientErrorReport(
    val message: String,
    val severity: String,
    val timestamp: Long,
    val source: String = "android_app",
    @SerializedName("os_version") val osVersion: String = android.os.Build.VERSION.RELEASE,
    @SerializedName("sdk_version") val sdkVersion: Int = android.os.Build.VERSION.SDK_INT,
    val device: String = android.os.Build.MODEL,
    val manufacturer: String = android.os.Build.MANUFACTURER,
    @SerializedName("exception_type") val exceptionType: String? = null,
    @SerializedName("stack_trace") val stackTrace: String? = null,
    val context: Map<String, Any>? = null,
)
