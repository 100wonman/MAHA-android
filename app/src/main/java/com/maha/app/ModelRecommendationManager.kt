// ModelRecommendationManager.kt

package com.maha.app

data class ModelRecommendation(
    val agentId: String,
    val agentName: String,
    val currentProviderName: String,
    val currentModelName: String,
    val recommendedProviderName: String,
    val recommendedModelName: String,
    val score: Int,
    val reason: String,
    val warning: String,
    val canApply: Boolean
)

private data class ModelCandidateScore(
    val providerName: String,
    val modelName: String,
    val testStatus: String,
    val testSuccessRate: Int,
    val testAverageLatencyMs: Long,
    val logSuccessRate: Int,
    val logAverageLatencyMs: Long,
    val recentFailurePenalty: Int,
    val score: Int,
    val reason: String,
    val warning: String
)

object ModelRecommendationManager {

    fun buildRecommendations(
        agents: List<Agent>,
        logs: List<ExecutionHistoryLog>,
        testRecords: List<ModelTestRecord>
    ): List<ModelRecommendation> {
        val candidates = buildCandidates(
            logs = logs,
            testRecords = testRecords
        )

        return agents.map { agent ->
            val role = resolveWorkerRole(agent.name)
            val rankedCandidates = candidates
                .filter { candidate ->
                    isCandidateAllowed(candidate)
                }
                .map { candidate ->
                    candidate.copy(
                        score = calculateRoleScore(
                            role = role,
                            candidate = candidate
                        )
                    )
                }
                .sortedByDescending { it.score }

            val best = rankedCandidates.firstOrNull()

            if (best == null) {
                ModelRecommendation(
                    agentId = agent.id,
                    agentName = agent.name,
                    currentProviderName = agent.providerName,
                    currentModelName = agent.modelName,
                    recommendedProviderName = agent.providerName,
                    recommendedModelName = agent.modelName,
                    score = 0,
                    reason = "추천 가능한 모델이 없습니다. 먼저 Model Catalog에서 모델 테스트를 실행하세요.",
                    warning = "FAILED / AUTH_REQUIRED / UNSUPPORTED / RATE_LIMITED 모델은 기본 추천에서 제외됩니다.",
                    canApply = false
                )
            } else {
                ModelRecommendation(
                    agentId = agent.id,
                    agentName = agent.name,
                    currentProviderName = agent.providerName,
                    currentModelName = agent.modelName,
                    recommendedProviderName = best.providerName,
                    recommendedModelName = best.modelName,
                    score = best.score,
                    reason = buildRoleReason(
                        role = role,
                        candidate = best
                    ),
                    warning = best.warning,
                    canApply = true
                )
            }
        }
    }

    private fun buildCandidates(
        logs: List<ExecutionHistoryLog>,
        testRecords: List<ModelTestRecord>
    ): List<ModelCandidateScore> {
        val candidateKeys = mutableSetOf<String>()

        testRecords.forEach { record ->
            if (record.providerName.isNotBlank() && record.modelName.isNotBlank()) {
                candidateKeys.add("${record.providerName}:::${record.modelName}")
            }
        }

        logs.forEach { log ->
            if (log.providerName.isNotBlank() && log.modelName.isNotBlank()) {
                candidateKeys.add("${log.providerName}:::${log.modelName}")
            }
        }

        return candidateKeys.map { key ->
            val parts = key.split(":::")
            val providerName = parts.getOrNull(0) ?: ModelProviderType.DUMMY
            val modelName = parts.getOrNull(1) ?: "dummy"

            val record = testRecords.firstOrNull {
                it.providerName == providerName && it.modelName == modelName
            }

            val modelLogs = logs.filter {
                it.providerName == providerName && it.modelName == modelName
            }

            val logSuccessRate = calculateLogSuccessRate(modelLogs)
            val logAverageLatencyMs = calculateAverageLatency(modelLogs)
            val recentFailurePenalty = calculateRecentFailurePenalty(modelLogs)

            val status = record?.status ?: inferStatusFromLogs(modelLogs)
            val testSuccessRate = record?.successRate ?: logSuccessRate
            val testAverageLatencyMs = record?.averageLatencyMs ?: logAverageLatencyMs

            ModelCandidateScore(
                providerName = providerName,
                modelName = modelName,
                testStatus = status,
                testSuccessRate = testSuccessRate,
                testAverageLatencyMs = testAverageLatencyMs,
                logSuccessRate = logSuccessRate,
                logAverageLatencyMs = logAverageLatencyMs,
                recentFailurePenalty = recentFailurePenalty,
                score = 0,
                reason = "",
                warning = buildWarning(status)
            )
        }
    }

    private fun isCandidateAllowed(candidate: ModelCandidateScore): Boolean {
        return when (candidate.testStatus) {
            NvidiaModelTestStatus.FAILED -> false
            NvidiaModelTestStatus.AUTH_REQUIRED -> false
            NvidiaModelTestStatus.UNSUPPORTED -> false
            NvidiaModelTestStatus.RATE_LIMITED -> false
            else -> true
        }
    }

    private fun calculateRoleScore(
        role: String,
        candidate: ModelCandidateScore
    ): Int {
        val successRate = resolveCombinedSuccessRate(candidate)
        val latencyMs = resolveCombinedLatency(candidate)

        val successScore = when {
            successRate >= 90 -> 40
            successRate >= 70 -> 30
            successRate >= 50 -> 15
            else -> 0
        }

        val latencyScore = when {
            latencyMs <= 0L -> 5
            latencyMs <= 1_000L -> 30
            latencyMs <= 3_000L -> 20
            latencyMs <= 8_000L -> 10
            else -> 0
        }

        val statusScore = when (candidate.testStatus) {
            NvidiaModelTestStatus.AVAILABLE -> 20
            NvidiaModelTestStatus.UNTESTED -> -20
            else -> 0
        }

        val roleBonus = when (role) {
            "PLANNER" -> successScore
            "WRITER" -> latencyScore
            "RESEARCHER" -> (successScore + latencyScore) / 2
            else -> successScore
        }

        return (
                successScore +
                        latencyScore +
                        statusScore +
                        roleBonus -
                        candidate.recentFailurePenalty
                ).coerceIn(0, 100)
    }

    private fun resolveCombinedSuccessRate(candidate: ModelCandidateScore): Int {
        val values = mutableListOf<Int>()

        if (candidate.testSuccessRate > 0) {
            values.add(candidate.testSuccessRate)
        }

        if (candidate.logSuccessRate > 0) {
            values.add(candidate.logSuccessRate)
        }

        if (values.isEmpty()) return 0

        return values.average().toInt()
    }

    private fun resolveCombinedLatency(candidate: ModelCandidateScore): Long {
        val values = mutableListOf<Long>()

        if (candidate.testAverageLatencyMs > 0L) {
            values.add(candidate.testAverageLatencyMs)
        }

        if (candidate.logAverageLatencyMs > 0L) {
            values.add(candidate.logAverageLatencyMs)
        }

        if (values.isEmpty()) return 0L

        return values.average().toLong()
    }

    private fun calculateLogSuccessRate(logs: List<ExecutionHistoryLog>): Int {
        if (logs.isEmpty()) return 0

        val successCount = logs.count { it.status == "SUCCESS" }

        return ((successCount.toDouble() / logs.size.toDouble()) * 100).toInt()
    }

    private fun calculateAverageLatency(logs: List<ExecutionHistoryLog>): Long {
        if (logs.isEmpty()) return 0L

        return logs.map { it.latencyMs }.average().toLong()
    }

    private fun calculateRecentFailurePenalty(logs: List<ExecutionHistoryLog>): Int {
        val recentLogs = logs.sortedByDescending { it.executedAt }.take(3)
        val joined = recentLogs.joinToString(" ") {
            "${it.status} ${it.errorMessage} ${it.outputText}"
        }.uppercase()

        return when {
            joined.contains("RATE_LIMIT") || joined.contains("RATE_LIMITED") || joined.contains("429") -> 40
            joined.contains("TIMEOUT") -> 30
            recentLogs.any { it.status == "FAILED" } -> 25
            else -> 0
        }
    }

    private fun inferStatusFromLogs(logs: List<ExecutionHistoryLog>): String {
        if (logs.isEmpty()) return NvidiaModelTestStatus.UNTESTED

        val joined = logs.joinToString(" ") {
            "${it.status} ${it.errorMessage} ${it.outputText}"
        }.uppercase()

        return when {
            joined.contains("RATE_LIMIT") || joined.contains("RATE_LIMITED") || joined.contains("429") -> NvidiaModelTestStatus.RATE_LIMITED
            logs.any { it.status == "SUCCESS" } -> NvidiaModelTestStatus.AVAILABLE
            logs.any { it.status == "FAILED" } -> NvidiaModelTestStatus.FAILED
            else -> NvidiaModelTestStatus.UNTESTED
        }
    }

    private fun resolveWorkerRole(agentName: String): String {
        val lowerName = agentName.trim().lowercase()

        return when {
            lowerName.contains("planner") -> "PLANNER"
            lowerName.contains("writer") -> "WRITER"
            lowerName.contains("researcher") -> "RESEARCHER"
            else -> "OTHER"
        }
    }

    private fun buildRoleReason(
        role: String,
        candidate: ModelCandidateScore
    ): String {
        val successRate = resolveCombinedSuccessRate(candidate)
        val latencyMs = resolveCombinedLatency(candidate)

        return when (role) {
            "PLANNER" -> "Planner는 성공률을 우선합니다. 이 모델은 성공률 ${successRate}%, 평균 latency ${latencyMs}ms 기준으로 추천되었습니다."
            "WRITER" -> "Writer는 빠른 응답을 우선합니다. 이 모델은 평균 latency ${latencyMs}ms 기준으로 추천되었습니다."
            "RESEARCHER" -> "Researcher는 성공률과 응답 시간 균형을 봅니다. 성공률 ${successRate}%, 평균 latency ${latencyMs}ms입니다."
            else -> "전체 안정성 기준으로 추천되었습니다. 성공률 ${successRate}%, 평균 latency ${latencyMs}ms입니다."
        }
    }

    private fun buildWarning(status: String): String {
        return when (status) {
            NvidiaModelTestStatus.UNTESTED -> "아직 테스트되지 않은 모델입니다. 안정성 확인 후 사용하는 것이 좋습니다."
            NvidiaModelTestStatus.RATE_LIMITED -> "최근 제한 상태가 감지된 모델입니다."
            else -> ""
        }
    }
}