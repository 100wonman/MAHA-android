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
            outputText = "GOOGLE_PROVIDER_READY_BUT_NETWORK_CALL_NOT_IMPLEMENTED",
            status = "SUCCESS"
        )
    }
}