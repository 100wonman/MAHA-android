// ModelRouter.kt

package com.maha.app

object ModelRouter {

    suspend fun generate(request: ModelRequest): ModelResponse {
        val provider = selectProvider()

        return provider.generate(request)
    }

    private fun selectProvider(): ModelProvider {
        return when (ApiKeyManager.getSelectedProvider()) {
            ModelProviderType.GOOGLE -> GoogleModelProvider
            ModelProviderType.NVIDIA -> NvidiaModelProvider
            ModelProviderType.DUMMY -> DummyModelProvider
            else -> DummyModelProvider
        }
    }
}