// GoogleModelProvider.kt

package com.maha.app

object GoogleModelProvider : ModelProvider {

    override suspend fun generate(request: ModelRequest): ModelResponse {
        if (!ApiKeyManager.hasGoogleApiKey()) {
            return ModelResponse(
                outputText = "GOOGLE_API_KEY_NOT_SET",
                status = "SUCCESS"
            )
        }

        return ModelResponse(
            outputText = "API_KEY_READY",
            status = "SUCCESS"
        )
    }
}