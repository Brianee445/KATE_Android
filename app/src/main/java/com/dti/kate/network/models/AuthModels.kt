package com.dti.kate.network.models

// ==================== REQUEST MODELS ====================

data class RegisterRequest(
    val email: String,
    val password: String,
    val fullName: String? = null,
)

data class LoginRequest(
    val email: String,
    val password: String,
)

data class RefreshTokenRequest(
    val refreshToken: String,
)

data class ForgotPasswordRequest(
    val email: String,
)

data class UpdateProfileRequest(
    val fullName: String? = null,
    val syncTrainingEnabled: Boolean? = null,
    val wakeTriggers: Map<String, Boolean>? = null,
)

data class AdminVerifyRequest(
    val passcode: String,
)

// ==================== RESPONSE MODELS ====================

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserResponse,
)

data class UserResponse(
    val id: String,
    val email: String,
    val fullName: String?,
    val tier: String,
    val usageCount: Int,
    val syncTrainingEnabled: Boolean,
    val isActive: Boolean,
    val isSuperuser: Boolean,
    val createdAt: String,
)

data class BaseResponse(
    val status: String,
    val message: String,
)

data class UsageResponse(
    val totalRequests: Int,
    val monthlyRequests: Int,
    val tierLimit: Int,
    val remaining: Int,
    val tier: String,
)

// ==================== CHAT MODELS ====================

data class ChatRequest(
    val query: String,
    val context: Map<String, Any>? = null,
)

data class ChatResponse(
    val query: String,
    val response: String,
    val intent: String,
    val confidence: Float,
    val usedCloud: Boolean,
    val latencyMs: Int,
    val requiresPayment: Boolean,
)

data class TranscribeResponse(
    val text: String,
    val confidence: Float,
    val latencyMs: Int,
)

// LLM-routed answer for general Q&A the free instant-answer sources
// (DuckDuckGo, Wikipedia - see WebSearchService/WikipediaService) don't
// cover well. Proxied through the backend so the agent-router key never
// ships inside the APK; the backend is expected to cache by normalized
// query so repeat questions don't re-spend a routing call.
data class SearchRequest(
    val query: String,
)

data class SearchResponse(
    val answer: String,
    val cached: Boolean,
    val source: String,
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
    val usedCloud: Boolean,
    val createdAt: String,
)

// ==================== SYNC MODELS ====================

data class SyncResponse(
    val status: String,
    val message: String,
    val processedCount: Int,
)

data class ModelUpdateResponse(
    val available: Boolean,
    val message: String,
    val version: String? = null,
    val url: String? = null,
    val sizeBytes: Long? = null,
    val checksum: String? = null,
    val releaseNotes: String? = null,
    val currentVersion: String? = null,
)

data class SyncLogEntry(
    val query: String,
    val response: String,
    val intent: String,
    val confidence: Float,
    val usedCloud: Boolean,
    val modelVersion: String,
    val createdAt: String,
)

// ==================== PAYMENT MODELS ====================

data class CreateCheckoutRequest(
    val tier: String, // "premium", "pro", "lifetime"
    val provider: String, // "flutterwave", "google_play"
)

data class CheckoutResponse(
    val checkoutUrl: String,
    val provider: String,
    val tier: String,
    val sessionId: String? = null,
)

data class SubscriptionStatusResponse(
    val tier: String,
    val status: String,
    val provider: String?,
    val expiresAt: String?,
    val isActive: Boolean,
)

// ==================== ADMIN MODELS ====================

data class AdminVerifyResponse(
    val valid: Boolean,
    val role: String,
    val isAdmin: Boolean,
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
    val newToday: Int,
    val active24h: Int,
    val premium: Int,
)

data class AdminConversationStats(
    val total: Int,
    val today: Int,
    val intentAccuracy: Double,
)

data class AdminRevenueStats(
    val totalRevenue: Double,
    val monthlyRevenue: Double,
    val growthRate: Double,
)

data class AdminErrorStats(
    val total: Int,
    val critical: Int,
    val warnings: Int,
    val last24h: Int,
)

data class AdminModelStats(
    val activeVersion: String,
    val latestVersion: String,
    val isOutdated: Boolean,
    val lastTrainedAt: String,
)

data class AdminErrorsResponse(
    val errors: List<AdminErrorItem>,
    val count: Int,
    val timestamp: String,
)

data class AdminErrorItem(
    val errorCode: String,
    val message: String,
    val severity: String,
    val category: String,
    val timestamp: String,
    val path: String? = null,
    val method: String? = null,
    val requestId: String? = null,
    val details: Map<String, Any>? = null,
    val userId: String? = null,
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
    val fullName: String?,
    val tier: String,
    val usageCount: Int,
    val createdAt: String,
    val lastLoginAt: String?,
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
    val osVersion: String = android.os.Build.VERSION.RELEASE,
    val sdkVersion: Int = android.os.Build.VERSION.SDK_INT,
    val device: String = android.os.Build.MODEL,
    val manufacturer: String = android.os.Build.MANUFACTURER,
    val exceptionType: String? = null,
    val stackTrace: String? = null,
    val context: Map<String, Any>? = null,
)
