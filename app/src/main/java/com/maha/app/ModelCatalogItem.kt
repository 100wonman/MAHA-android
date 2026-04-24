// ModelCatalogItem.kt

package com.maha.app

data class ModelCatalogItem(
    val modelName: String,
    val displayName: String,
    val description: String,
    val stabilityStatus: String,
    val recommendedWorker: String,
    val estimatedDailyLimit: Int
)