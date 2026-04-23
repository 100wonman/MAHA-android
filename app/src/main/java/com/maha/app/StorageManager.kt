// StorageManager.kt

package com.maha.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object StorageManager {

    private const val PREFS_NAME = "maha_prefs"

    private const val KEY_AGENTS = "agents_json_v3"
    private const val KEY_SCENARIOS = "scenarios_json_v3"
    private const val KEY_RUNS = "runs_json_v3"

    private const val KEY_AGENTS_BACKUP = "agents_json_v3_backup"
    private const val KEY_SCENARIOS_BACKUP = "scenarios_json_v3_backup"
    private const val KEY_RUNS_BACKUP = "runs_json_v3_backup"

    fun saveAgents(context: Context, agents: List<Agent>) {
        val safeAgents = agents
            .map { sanitizeAgent(it) }
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }

        val jsonArray = JSONArray()
        safeAgents.forEach { agent ->
            jsonArray.put(agentToJson(agent))
        }

        saveJsonWithBackup(
            context = context,
            key = KEY_AGENTS,
            backupKey = KEY_AGENTS_BACKUP,
            value = jsonArray.toString()
        )
    }

    fun loadAgents(context: Context): List<Agent> {
        val loaded = loadJsonArrayWithBackup(
            context = context,
            key = KEY_AGENTS,
            backupKey = KEY_AGENTS_BACKUP
        ) { jsonArray ->
            val result = mutableListOf<Agent>()

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(i) ?: continue
                val agent = jsonToAgent(item) ?: continue
                result.add(agent)
            }

            result.distinctBy { it.id }
        }

        return loaded
    }

    fun saveScenarios(context: Context, scenarios: List<Scenario>) {
        val safeScenarios = scenarios
            .mapNotNull { sanitizeScenario(it) }
            .distinctBy { it.id }

        val jsonArray = JSONArray()
        safeScenarios.forEach { scenario ->
            jsonArray.put(scenarioToJson(scenario))
        }

        saveJsonWithBackup(
            context = context,
            key = KEY_SCENARIOS,
            backupKey = KEY_SCENARIOS_BACKUP,
            value = jsonArray.toString()
        )
    }

    fun loadScenarios(context: Context): List<Scenario> {
        return loadJsonArrayWithBackup(
            context = context,
            key = KEY_SCENARIOS,
            backupKey = KEY_SCENARIOS_BACKUP
        ) { jsonArray ->
            val result = mutableListOf<Scenario>()

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(i) ?: continue
                val scenario = jsonToScenario(item) ?: continue
                result.add(scenario)
            }

            result.distinctBy { it.id }
        }
    }

    fun saveRuns(context: Context, runs: List<Run>) {
        val safeRuns = runs
            .mapNotNull { sanitizeRun(it) }
            .distinctBy { it.runId }

        val jsonArray = JSONArray()
        safeRuns.forEach { run ->
            jsonArray.put(runToJson(run))
        }

        saveJsonWithBackup(
            context = context,
            key = KEY_RUNS,
            backupKey = KEY_RUNS_BACKUP,
            value = jsonArray.toString()
        )
    }

    fun loadRuns(context: Context): List<Run> {
        return loadJsonArrayWithBackup(
            context = context,
            key = KEY_RUNS,
            backupKey = KEY_RUNS_BACKUP
        ) { jsonArray ->
            val result = mutableListOf<Run>()

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(i) ?: continue
                val run = jsonToRun(item) ?: continue
                result.add(run)
            }

            result.distinctBy { it.runId }
        }
    }

    private fun saveJsonWithBackup(
        context: Context,
        key: String,
        backupKey: String,
        value: String
    ) {
        runCatching {
            getPrefs(context)
                .edit()
                .putString(backupKey, value)
                .putString(key, value)
                .apply()
        }
    }

    private fun <T> loadJsonArrayWithBackup(
        context: Context,
        key: String,
        backupKey: String,
        parser: (JSONArray) -> List<T>
    ): List<T> {
        val primary = runCatching {
            val jsonString = loadString(context, key) ?: return@runCatching emptyList()
            parser(JSONArray(jsonString))
        }.getOrNull()

        if (primary != null) return primary

        val backup = runCatching {
            val jsonString = loadString(context, backupKey) ?: return@runCatching emptyList()
            parser(JSONArray(jsonString))
        }.getOrNull()

        return backup ?: emptyList()
    }

    private fun loadString(context: Context, key: String): String? {
        return getPrefs(context).getString(key, null)
    }

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun sanitizeAgent(agent: Agent): Agent {
        val safeId = agent.id.ifBlank { "" }
        if (safeId.isBlank()) return agent.copy(id = "")

        return agent.copy(
            id = safeId,
            name = agent.name.ifBlank { "Unnamed Agent" },
            description = agent.description.ifBlank { "설명이 없습니다." },
            status = agent.status.ifBlank { "Enabled" },
            inputFormat = agent.inputFormat.ifBlank { "Input Text" },
            outputFormat = agent.outputFormat.ifBlank { "Output Text" }
        )
    }

    private fun agentToJson(agent: Agent): JSONObject {
        val safeAgent = sanitizeAgent(agent)

        return JSONObject().apply {
            put("id", safeAgent.id)
            put("name", safeAgent.name)
            put("description", safeAgent.description)
            put("status", safeAgent.status)
            put("inputFormat", safeAgent.inputFormat)
            put("outputFormat", safeAgent.outputFormat)
            put("isEnabled", safeAgent.isEnabled)
        }
    }

    private fun jsonToAgent(json: JSONObject): Agent? {
        val id = json.optString("id", "").trim()
        if (id.isEmpty()) return null

        return sanitizeAgent(
            Agent(
                id = id,
                name = json.optString("name", "Unnamed Agent"),
                description = json.optString("description", ""),
                status = json.optString("status", "Enabled"),
                inputFormat = json.optString("inputFormat", "Input Text"),
                outputFormat = json.optString("outputFormat", "Output Text"),
                isEnabled = json.optBoolean("isEnabled", true)
            )
        ).takeIf { it.id.isNotBlank() }
    }

    private fun sanitizeScenario(scenario: Scenario): Scenario? {
        val safeId = scenario.id.ifBlank { return null }
        val safeAgents = scenario.agents
            .map { sanitizeAgent(it) }
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }

        return Scenario(
            id = safeId,
            name = scenario.name.ifBlank { "Unnamed Scenario" },
            agents = safeAgents,
            savedAt = scenario.savedAt.ifBlank { "" }
        )
    }

    private fun scenarioToJson(scenario: Scenario): JSONObject {
        val safeScenario = sanitizeScenario(scenario) ?: return JSONObject()

        val agentsArray = JSONArray()
        safeScenario.agents.forEach { agent ->
            agentsArray.put(agentToJson(agent))
        }

        return JSONObject().apply {
            put("id", safeScenario.id)
            put("name", safeScenario.name)
            put("savedAt", safeScenario.savedAt)
            put("agents", agentsArray)
        }
    }

    private fun jsonToScenario(json: JSONObject): Scenario? {
        val id = json.optString("id", "").trim()
        if (id.isEmpty()) return null

        val agentsArray = json.optJSONArray("agents") ?: JSONArray()
        val agents = mutableListOf<Agent>()

        for (i in 0 until agentsArray.length()) {
            val agentObject = agentsArray.optJSONObject(i) ?: continue
            val agent = jsonToAgent(agentObject) ?: continue
            agents.add(agent)
        }

        return sanitizeScenario(
            Scenario(
                id = id,
                name = json.optString("name", "Unnamed Scenario"),
                agents = agents,
                savedAt = json.optString("savedAt", "")
            )
        )
    }

    private fun sanitizeRunResult(result: RunResult): RunResult? {
        val safeAgentId = result.agentId.ifBlank { return null }

        return RunResult(
            agentId = safeAgentId,
            agentName = result.agentName.ifBlank { "Unknown Agent" },
            status = result.status.ifBlank { "UNKNOWN" },
            inputText = result.inputText.ifBlank { "(empty input)" },
            outputText = result.outputText.ifBlank { "(empty output)" },
            timestamp = result.timestamp.ifBlank { "" },
            order = if (result.order <= 0) 1 else result.order
        )
    }

    private fun runResultToJson(result: RunResult): JSONObject {
        val safeResult = sanitizeRunResult(result) ?: return JSONObject()

        return JSONObject().apply {
            put("agentId", safeResult.agentId)
            put("agentName", safeResult.agentName)
            put("status", safeResult.status)
            put("inputText", safeResult.inputText)
            put("outputText", safeResult.outputText)
            put("timestamp", safeResult.timestamp)
            put("order", safeResult.order)
        }
    }

    private fun jsonToRunResult(json: JSONObject): RunResult? {
        val agentId = json.optString("agentId", "").trim()
        if (agentId.isEmpty()) return null

        return sanitizeRunResult(
            RunResult(
                agentId = agentId,
                agentName = json.optString("agentName", "Unknown Agent"),
                status = json.optString("status", "UNKNOWN"),
                inputText = json.optString("inputText", ""),
                outputText = json.optString("outputText", ""),
                timestamp = json.optString("timestamp", ""),
                order = json.optInt("order", 0)
            )
        )
    }

    private fun sanitizeExecutionLog(log: ExecutionLog): ExecutionLog {
        return ExecutionLog(
            message = log.message.ifBlank { "(empty log)" },
            timestamp = log.timestamp.ifBlank { "" }
        )
    }

    private fun executionLogToJson(log: ExecutionLog): JSONObject {
        val safeLog = sanitizeExecutionLog(log)

        return JSONObject().apply {
            put("message", safeLog.message)
            put("timestamp", safeLog.timestamp)
        }
    }

    private fun jsonToExecutionLog(json: JSONObject): ExecutionLog {
        return sanitizeExecutionLog(
            ExecutionLog(
                message = json.optString("message", ""),
                timestamp = json.optString("timestamp", "")
            )
        )
    }

    private fun sanitizeRun(run: Run): Run? {
        val safeRunId = run.runId.ifBlank { return null }

        val safeResults = run.results
            .mapNotNull { sanitizeRunResult(it) }
            .sortedBy { it.order }

        val safeLogs = run.logs.map { sanitizeExecutionLog(it) }

        return Run(
            runId = safeRunId,
            title = run.title.ifBlank { "Untitled Run" },
            timestamp = run.timestamp.ifBlank { "" },
            results = safeResults,
            logs = safeLogs
        )
    }

    private fun runToJson(run: Run): JSONObject {
        val safeRun = sanitizeRun(run) ?: return JSONObject()

        val resultsArray = JSONArray()
        safeRun.results.forEach { result ->
            resultsArray.put(runResultToJson(result))
        }

        val logsArray = JSONArray()
        safeRun.logs.forEach { log ->
            logsArray.put(executionLogToJson(log))
        }

        return JSONObject().apply {
            put("runId", safeRun.runId)
            put("title", safeRun.title)
            put("timestamp", safeRun.timestamp)
            put("results", resultsArray)
            put("logs", logsArray)
        }
    }

    private fun jsonToRun(json: JSONObject): Run? {
        val runId = json.optString("runId", "").trim()
        if (runId.isEmpty()) return null

        val resultsArray = json.optJSONArray("results") ?: JSONArray()
        val results = mutableListOf<RunResult>()
        for (i in 0 until resultsArray.length()) {
            val resultObject = resultsArray.optJSONObject(i) ?: continue
            val result = jsonToRunResult(resultObject) ?: continue
            results.add(result)
        }

        val logsArray = json.optJSONArray("logs") ?: JSONArray()
        val logs = mutableListOf<ExecutionLog>()
        for (i in 0 until logsArray.length()) {
            val logObject = logsArray.optJSONObject(i) ?: continue
            logs.add(jsonToExecutionLog(logObject))
        }

        return sanitizeRun(
            Run(
                runId = runId,
                title = json.optString("title", "Untitled Run"),
                timestamp = json.optString("timestamp", ""),
                results = results,
                logs = logs
            )
        )
    }
}