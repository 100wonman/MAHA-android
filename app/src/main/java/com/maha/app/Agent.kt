//Agent.kt

package com.maha.app

data class Agent(
    val id: String,
    val name: String,
    val description: String,
    val status: String,
    val inputFormat: String,
    val outputFormat: String,
    val isEnabled: Boolean,
    val providerName: String = ModelProviderType.DUMMY,
    val modelName: String = GeminiModelType.DEFAULT
)