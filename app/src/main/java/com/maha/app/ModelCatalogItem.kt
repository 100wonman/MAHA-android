// ModelCatalogItem.kt

package com.maha.app

data class ModelCatalogItem(
    val modelName: String,
    val displayName: String,
    val description: String,
    val stabilityStatus: String,
    val recommendedWorker: String,
    val estimatedDailyLimit: Int,
    val providerName: String = inferProvider(modelName)
)

private fun inferProvider(modelName: String): String {
    return when {
        modelName.startsWith("gemini") -> ModelProviderType.GOOGLE
        modelName.startsWith("gemma") -> ModelProviderType.GOOGLE
        modelName.contains("/") -> ModelProviderType.NVIDIA
        else -> ModelProviderType.DUMMY
    }
}