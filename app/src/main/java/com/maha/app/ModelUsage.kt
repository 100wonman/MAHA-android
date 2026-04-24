// ModelUsage.kt

package com.maha.app

data class ModelUsage(
    val modelName: String,
    val date: String,
    val requestCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val rateLimitCount: Int,
    val lastUsedAt: String,
    val blockedUntilMillis: Long
)