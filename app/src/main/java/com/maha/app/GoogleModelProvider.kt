// GoogleModelProvider.kt

package com.maha.app

object GoogleModelProvider : ModelProvider {

    override suspend fun generate(request: ModelRequest): ModelResponse {
        return ModelResponse(
            outputText = "GOOGLE_PROVIDER_NOT_CONNECTED",
            status = "SUCCESS"
        )
    }
}