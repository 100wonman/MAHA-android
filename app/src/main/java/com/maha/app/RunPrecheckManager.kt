// RunPrecheckManager.kt

package com.maha.app

import android.content.Context

data class RunPrecheckResult(
    val estimatedApiCallCount: Int,
    val enabledWorkerCount: Int,
    val disabledWorkerCount: Int,
    val warnings: List<RunPrecheckWarning>
)

data class RunPrecheckWarning(
    val agentName: String,
    val modelName: String,
    val providerName: String,
    val status: String,
    val message: String
)

object RunPrecheckManager {

    fun buildRunAllPrecheck(
        context: Context,
        agents: List<Agent>,
        fallbackProviderName: String
    ): RunPrecheckResult {
        val safeAgents = agents
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }

        val enabledAgents = safeAgents.filter { it.isEnabled }
        val disabledWorkerCount = safeAgents.count { !it.isEnabled }

        val warnings = enabledAgents.mapNotNull { agent ->
            val safeModelName = agent.modelName.trim().removePrefix("models/")
            val providerName = resolveProviderName(
                agentProviderName = agent.providerName,
                fallbackProviderName = fallbackProviderName,
                modelName = safeModelName
            )

            if (providerName == ModelProviderType.DUMMY) return@mapNotNull null

            val record = ModelTestManager.getRecord(
                context = context,
                providerName = providerName,
                modelName = safeModelName
            )

            when (record.status.uppercase()) {
                NvidiaModelTestStatus.RATE_LIMITED -> {
                    RunPrecheckWarning(
                        agentName = agent.name,
                        modelName = safeModelName,
                        providerName = providerName,
                        status = record.status,
                        message = "RATE_LIMITED 상태입니다. 실행 시 API 제한 오류가 발생할 수 있습니다."
                    )
                }

                NvidiaModelTestStatus.UNTESTED -> {
                    RunPrecheckWarning(
                        agentName = agent.name,
                        modelName = safeModelName,
                        providerName = providerName,
                        status = record.status,
                        message = "아직 테스트하지 않은 모델입니다."
                    )
                }

                else -> null
            }
        }

        return RunPrecheckResult(
            estimatedApiCallCount = enabledAgents.size,
            enabledWorkerCount = enabledAgents.size,
            disabledWorkerCount = disabledWorkerCount,
            warnings = warnings
        )
    }

    private fun resolveProviderName(
        agentProviderName: String,
        fallbackProviderName: String,
        modelName: String
    ): String {
        sanitizeProviderName(agentProviderName)?.let { return it }
        sanitizeProviderName(fallbackProviderName)?.let { return it }
        inferProviderNameFromModelName(modelName)?.let { return it }
        return ModelProviderType.DUMMY
    }

    private fun sanitizeProviderName(providerName: String): String? {
        return when (providerName) {
            ModelProviderType.DUMMY -> ModelProviderType.DUMMY
            ModelProviderType.GOOGLE -> ModelProviderType.GOOGLE
            ModelProviderType.NVIDIA -> ModelProviderType.NVIDIA
            else -> null
        }
    }

    private fun inferProviderNameFromModelName(modelName: String): String? {
        val normalized = modelName.trim().removePrefix("models/").lowercase()

        return when {
            normalized.startsWith("gemini") || normalized.startsWith("gemma") -> ModelProviderType.GOOGLE
            normalized.contains("/") -> ModelProviderType.NVIDIA
            else -> null
        }
    }
}