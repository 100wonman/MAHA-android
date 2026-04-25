// DiscoveredModel.kt

package com.maha.app

data class DiscoveredModel(
    val modelName: String,
    val displayName: String,
    val description: String,
    val supportedGenerationMethods: List<String>,
    val inputTokenLimit: Int,
    val outputTokenLimit: Int,
    val isGenerateContentSupported: Boolean,
    val lastFetchedAt: String,
    val tags: List<String>,
    val providerName: String = ModelProviderType.GOOGLE,
    val isFreeCandidate: Boolean = false
)