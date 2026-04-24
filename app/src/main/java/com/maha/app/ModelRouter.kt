// ModelRouter.kt

package com.maha.app

object ModelRouter {

    suspend fun generate(request: ModelRequest): ModelResponse {
        val provider = selectProvider(request)

        return provider.generate(request)
    }

    private fun selectProvider(request: ModelRequest): ModelProvider {
        val providerName = request.providerName.uppercase()
        val modelName = request.modelName.lowercase()

        return when {
            providerName == "GOOGLE" -> GoogleModelProvider
            modelName.contains("gemini") -> GoogleModelProvider
            else -> DummyModelProvider
        }
    }
}