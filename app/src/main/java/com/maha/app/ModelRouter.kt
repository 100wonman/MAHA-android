//ModelRouter.kt

package com.maha.app

object ModelRouter {

    suspend fun generate(request: ModelRequest): ModelResponse {
        val provider = selectProvider(request.providerName)
        return provider.generate(request)
    }

    private fun selectProvider(providerName: String): ModelProvider {
        return when (providerName) {
            ModelProviderType.GOOGLE -> GoogleModelProvider
            ModelProviderType.NVIDIA -> NvidiaModelProvider
            ModelProviderType.DUMMY -> DummyModelProvider
            else -> DummyModelProvider
        }
    }
}