// DummyModelProvider.kt

package com.maha.app

object DummyModelProvider : ModelProvider {

    override suspend fun generate(request: ModelRequest): ModelResponse {
        val outputText = when (request.runType) {
            "SINGLE" -> {
                "Single run output from ${request.agentName} based on: ${request.inputText}"
            }

            "RUN_ALL" -> {
                "Step ${request.stepNumber} - ${request.agentName} output based on: ${request.inputText}"
            }

            else -> {
                "${request.agentName} output based on: ${request.inputText}"
            }
        }

        return ModelResponse(
            outputText = outputText,
            status = "SUCCESS"
        )
    }
}