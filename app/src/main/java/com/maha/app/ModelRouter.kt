// ModelRouter.kt

package com.maha.app

object ModelRouter {

    private val provider: ModelProvider = DummyModelProvider

    suspend fun generate(request: ModelRequest): ModelResponse {
        return provider.generate(request)
    }
}