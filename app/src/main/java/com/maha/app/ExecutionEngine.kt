// ExecutionEngine.kt

package com.maha.app

import kotlinx.coroutines.delay

object ExecutionEngine {

    suspend fun runSingleAgent(
        agent: Agent,
        validAgents: List<Agent>,
        existingRuns: List<Run>,
        inputPrompt: String = "",
        onStateChange: (agentId: String, state: String) -> Unit
    ): Run {
        val currentAgent = sanitizeAgent(agent)
        val timeText = getCurrentTimeText()
        val inputText = inputPrompt.ifBlank { "User request for ${currentAgent.name}" }

        onStateChange(currentAgent.id, "RUNNING")

        delay(500)

        val modelResponse = ModelRouter.generate(
            ModelRequest(
                agentId = currentAgent.id,
                agentName = currentAgent.name,
                inputText = inputText,
                stepNumber = 1,
                runType = "SINGLE"
            )
        )

        val result = sanitizeRunResult(
            RunResult(
                agentId = currentAgent.id,
                agentName = currentAgent.name,
                status = modelResponse.status,
                inputText = inputText,
                outputText = modelResponse.outputText,
                timestamp = timeText,
                order = 1
            )
        ) ?: throw IllegalStateException("Invalid run result")

        val logs = listOf(
            ExecutionLog(
                message = "${currentAgent.name} is RUNNING with input: $inputText",
                timestamp = timeText
            ),
            ExecutionLog(
                message = "${currentAgent.name} finished with ${modelResponse.status} and output: ${modelResponse.outputText}",
                timestamp = getCurrentTimeText()
            )
        )

        val safeValidAgents = normalizeAgents(validAgents + currentAgent)

        val run = sanitizeRunWithAgents(
            run = Run(
                runId = generateUniqueRunId(existingRuns),
                title = "Single Run - ${currentAgent.name}",
                timestamp = timeText,
                results = listOf(result),
                logs = logs
            ),
            validAgents = safeValidAgents
        ) ?: throw IllegalStateException("Invalid run")

        onStateChange(currentAgent.id, modelResponse.status)

        return run
    }

    suspend fun runAllAgents(
        agents: List<Agent>,
        existingRuns: List<Run>,
        inputPrompt: String = "",
        onStateChange: (agentId: String, state: String) -> Unit
    ): Run {
        val safeAgents = normalizeAgents(agents)

        safeAgents.forEach { agent ->
            onStateChange(agent.id, "WAITING")
        }

        val enabledAgents = safeAgents.filter { it.isEnabled }

        val runId = generateUniqueRunId(existingRuns)
        val runTimestamp = getCurrentTimeText()

        val resultList = mutableListOf<RunResult>()
        val logList = mutableListOf<ExecutionLog>()

        var currentInput = inputPrompt.ifBlank {
            "User request: Create a simple MAHA workflow summary."
        }

        logList.add(
            ExecutionLog(
                message = "Run All started",
                timestamp = getCurrentTimeText()
            )
        )

        if (enabledAgents.isEmpty()) {
            logList.add(
                ExecutionLog(
                    message = "No enabled agents. Run All stopped.",
                    timestamp = getCurrentTimeText()
                )
            )

            return sanitizeRunWithAgents(
                run = Run(
                    runId = runId,
                    title = "Run All",
                    timestamp = runTimestamp,
                    results = resultList,
                    logs = logList
                ),
                validAgents = safeAgents
            ) ?: throw IllegalStateException("Invalid empty run")
        }

        enabledAgents.forEachIndexed { index, agent ->
            val stepNumber = index + 1

            onStateChange(agent.id, "RUNNING")

            logList.add(
                ExecutionLog(
                    message = "${agent.name} is RUNNING with input: $currentInput",
                    timestamp = getCurrentTimeText()
                )
            )

            delay(700)

            val modelResponse = ModelRouter.generate(
                ModelRequest(
                    agentId = agent.id,
                    agentName = agent.name,
                    inputText = currentInput,
                    stepNumber = stepNumber,
                    runType = "RUN_ALL"
                )
            )

            val result = sanitizeRunResult(
                RunResult(
                    agentId = agent.id,
                    agentName = agent.name,
                    status = modelResponse.status,
                    inputText = currentInput,
                    outputText = modelResponse.outputText,
                    timestamp = getCurrentTimeText(),
                    order = stepNumber
                )
            ) ?: throw IllegalStateException("Invalid run result")

            resultList.add(result)

            onStateChange(agent.id, modelResponse.status)

            logList.add(
                ExecutionLog(
                    message = "${agent.name} finished with ${modelResponse.status} and output: ${modelResponse.outputText}",
                    timestamp = getCurrentTimeText()
                )
            )

            currentInput = modelResponse.outputText

            delay(300)
        }

        logList.add(
            ExecutionLog(
                message = "Run All completed",
                timestamp = getCurrentTimeText()
            )
        )

        return sanitizeRunWithAgents(
            run = Run(
                runId = runId,
                title = "Run All",
                timestamp = runTimestamp,
                results = resultList,
                logs = logList
            ),
            validAgents = safeAgents
        ) ?: throw IllegalStateException("Invalid run")
    }

    private fun generateUniqueRunId(existingRuns: List<Run>): String {
        val existingIds = existingRuns.map { it.runId }.toSet()
        var index = System.currentTimeMillis()
        var candidate = "run_$index"

        while (candidate in existingIds) {
            index += 1
            candidate = "run_$index"
        }

        return candidate
    }

    private fun normalizeAgents(source: List<Agent>): List<Agent> {
        return source
            .map { sanitizeAgent(it) }
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
    }

    private fun sanitizeAgent(agent: Agent): Agent {
        val safeId = agent.id.ifBlank { "agent_${System.currentTimeMillis()}" }
        val safeName = agent.name.ifBlank { "Unnamed Agent" }
        val safeDescription = agent.description.ifBlank { "설명이 없습니다." }
        val safeStatus = agent.status.ifBlank { "Enabled" }
        val safeInputFormat = agent.inputFormat.ifBlank { "Input Text" }
        val safeOutputFormat = agent.outputFormat.ifBlank { "Output Text" }

        return agent.copy(
            id = safeId,
            name = safeName,
            description = safeDescription,
            status = safeStatus,
            inputFormat = safeInputFormat,
            outputFormat = safeOutputFormat
        )
    }

    private fun sanitizeRunResult(result: RunResult): RunResult? {
        val safeAgentId = result.agentId.ifBlank { return null }

        return result.copy(
            agentId = safeAgentId,
            agentName = result.agentName.ifBlank { "Unknown Agent" },
            status = result.status.ifBlank { "UNKNOWN" },
            inputText = result.inputText.ifBlank { "(empty input)" },
            outputText = result.outputText.ifBlank { "(empty output)" },
            timestamp = result.timestamp.ifBlank { getCurrentTimeText() },
            order = if (result.order <= 0) 1 else result.order
        )
    }

    private fun sanitizeRunWithAgents(
        run: Run,
        validAgents: List<Agent>
    ): Run? {
        val validAgentMap = normalizeAgents(validAgents).associateBy { it.id }
        val safeRunId = run.runId.ifBlank { return null }
        val safeTitle = run.title.ifBlank { "Untitled Run" }
        val safeTimestamp = run.timestamp.ifBlank { getCurrentTimeText() }

        val safeResults = run.results
            .mapNotNull { sanitizeRunResult(it) }
            .filter { it.agentId in validAgentMap.keys }
            .map { result ->
                val matchedAgent = validAgentMap[result.agentId]
                if (matchedAgent != null) {
                    result.copy(agentName = matchedAgent.name)
                } else {
                    result
                }
            }
            .sortedBy { it.order }

        val safeLogs = run.logs.map { log ->
            ExecutionLog(
                message = log.message.ifBlank { "(empty log)" },
                timestamp = log.timestamp.ifBlank { getCurrentTimeText() }
            )
        }

        return Run(
            runId = safeRunId,
            title = safeTitle,
            timestamp = safeTimestamp,
            results = safeResults,
            logs = safeLogs
        )
    }
}