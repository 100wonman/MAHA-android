// ModelRouter.kt

package com.maha.app

object ModelRouter {

    suspend fun generate(request: ModelRequest): ModelResponse {
        val provider = selectProvider(request.modelName)
        return provider.generate(request)
    }

    private fun selectProvider(modelName: String): ModelProvider {
        val safeModelName = modelName.trim().removePrefix("models/")

        return when {
            isGoogleModel(safeModelName) -> GoogleModelProvider
            isNvidiaModel(safeModelName) -> NvidiaModelProvider
            else -> selectProviderFromSettings()
        }
    }

    private fun selectProviderFromSettings(): ModelProvider {
        return when (ApiKeyManager.getSelectedProvider()) {
            ModelProviderType.GOOGLE -> GoogleModelProvider
            ModelProviderType.NVIDIA -> NvidiaModelProvider
            ModelProviderType.DUMMY -> DummyModelProvider
            else -> DummyModelProvider
        }
    }

    private fun isGoogleModel(modelName: String): Boolean {
        return modelName.startsWith("gemini") ||
                modelName.startsWith("gemma")
    }

    private fun isNvidiaModel(modelName: String): Boolean {
        return modelName.contains("/")
    }
}