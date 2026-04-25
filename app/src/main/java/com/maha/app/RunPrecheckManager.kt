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
            val providerName = inferProviderName(
                modelName = safeModelName,
                fallbackProviderName = fallbackProviderName
            ) ?: return@mapNotNull null

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

    private fun inferProviderName(
        modelName: String,
        fallbackProviderName: String
    ): String? {
        val normalizedModelName = modelName
            .trim()
            .removePrefix("models/")
            .lowercase()

        if (
            normalizedModelName.startsWith("gemini") ||
            normalizedModelName.startsWith("gemma")
        ) {
            return ModelProviderType.GOOGLE
        }

        if (normalizedModelName.contains("/")) {
            return ModelProviderType.NVIDIA
        }

        return when (fallbackProviderName) {
            ModelProviderType.GOOGLE -> ModelProviderType.GOOGLE
            ModelProviderType.NVIDIA -> ModelProviderType.NVIDIA
            else -> null
        }
    }
}