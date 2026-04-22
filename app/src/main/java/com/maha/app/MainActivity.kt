// MainActivity.kt

package com.maha.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                MAHAApp()
            }
        }
    }
}

@Composable
fun MAHAApp() {
    val scope = rememberCoroutineScope()

    val agentList = remember {
        mutableStateListOf(
            Agent(
                id = "agent_001",
                name = "Planner",
                description = "작업 순서를 먼저 정리하는 에이전트",
                status = "Enabled",
                inputFormat = "User Request",
                outputFormat = "Plan Text",
                isEnabled = true
            ),
            Agent(
                id = "agent_002",
                name = "Researcher",
                description = "필요한 정보를 조사하고 정리하는 에이전트",
                status = "Enabled",
                inputFormat = "Plan Text",
                outputFormat = "Research Notes",
                isEnabled = true
            ),
            Agent(
                id = "agent_003",
                name = "Writer",
                description = "최종 결과를 문장 형태로 작성하는 에이전트",
                status = "Enabled",
                inputFormat = "Research Notes",
                outputFormat = "Final Answer",
                isEnabled = true
            )
        )
    }

    val runList = remember { mutableStateListOf<Run>() }
    val scenarioList = remember { mutableStateListOf<Scenario>() }
    val executionStateMap = remember { mutableStateMapOf<String, String>() }

    var selectedAgentId by remember { mutableStateOf<String?>(null) }
    var selectedRun by remember { mutableStateOf<Run?>(null) }
    var isScenarioScreenOpen by remember { mutableStateOf(false) }

    agentList.forEach { agent ->
        if (executionStateMap[agent.id] == null) {
            executionStateMap[agent.id] = "WAITING"
        }
    }

    val selectedAgent = agentList.find { it.id == selectedAgentId }

    when {
        selectedRun != null -> {
            RunDetailScreen(
                run = selectedRun!!,
                onBackClick = {
                    selectedRun = null
                }
            )
        }

        isScenarioScreenOpen -> {
            ScenarioListScreen(
                scenarioList = scenarioList,
                onScenarioClick = { scenario ->
                    agentList.clear()
                    agentList.addAll(
                        scenario.agents.map { savedAgent ->
                            savedAgent.copy()
                        }
                    )

                    executionStateMap.clear()
                    agentList.forEach { agent ->
                        executionStateMap[agent.id] = "WAITING"
                    }

                    isScenarioScreenOpen = false
                },
                onBackClick = {
                    isScenarioScreenOpen = false
                }
            )
        }

        selectedAgent != null -> {
            AgentDetailScreen(
                agent = selectedAgent,
                runList = runList.filter { run ->
                    run.results.any { it.agentId == selectedAgent.id }
                },
                onSaveClick = { updatedAgent ->
                    val index = agentList.indexOfFirst { it.id == updatedAgent.id }
                    if (index != -1) {
                        agentList[index] = updatedAgent
                        executionStateMap[updatedAgent.id] = "WAITING"
                    }
                },
                onDeleteClick = { agentToDelete ->
                    agentList.removeAll { it.id == agentToDelete.id }
                    executionStateMap.remove(agentToDelete.id)
                    selectedAgentId = null
                },
                onRunClick = { newRun ->
                    runList.add(0, newRun)
                },
                onRunItemClick = { run ->
                    selectedRun = run
                },
                onBackClick = {
                    selectedAgentId = null
                }
            )
        }

        else -> {
            AgentListScreen(
                agentList = agentList,
                runList = runList,
                executionStateMap = executionStateMap,
                onAgentClick = { agent ->
                    selectedAgentId = agent.id
                },
                onAddAgentClick = {
                    val newAgent = Agent(
                        id = "agent_${System.currentTimeMillis()}",
                        name = "New Agent",
                        description = "새 워커 설명",
                        status = "Enabled",
                        inputFormat = "Input Text",
                        outputFormat = "Output Text",
                        isEnabled = true
                    )
                    agentList.add(newAgent)
                    executionStateMap[newAgent.id] = "WAITING"
                    selectedAgentId = newAgent.id
                },
                onSaveScenarioClick = {
                    val newScenario = Scenario(
                        id = "scenario_${System.currentTimeMillis()}",
                        name = "Scenario ${scenarioList.size + 1}",
                        agents = agentList.map { it.copy() },
                        savedAt = getCurrentTimeText()
                    )
                    scenarioList.add(0, newScenario)
                },
                onOpenScenarioListClick = {
                    isScenarioScreenOpen = true
                },
                onMoveUpClick = { agent ->
                    val index = agentList.indexOfFirst { it.id == agent.id }
                    if (index > 0) {
                        val temp = agentList[index - 1]
                        agentList[index - 1] = agentList[index]
                        agentList[index] = temp
                    }
                },
                onMoveDownClick = { agent ->
                    val index = agentList.indexOfFirst { it.id == agent.id }
                    if (index != -1 && index < agentList.lastIndex) {
                        val temp = agentList[index + 1]
                        agentList[index + 1] = agentList[index]
                        agentList[index] = temp
                    }
                },
                onRunItemClick = { run ->
                    selectedRun = run
                },
                onRunAllClick = {
                    scope.launch {
                        agentList.forEach { agent ->
                            executionStateMap[agent.id] = "WAITING"
                        }

                        val enabledAgents = agentList.filter { it.isEnabled }

                        val runId = "run_${System.currentTimeMillis()}"
                        val runTimestamp = getCurrentTimeText()

                        val resultList = mutableListOf<RunResult>()
                        val logList = mutableListOf<ExecutionLog>()

                        var currentInput = "User request: Create a simple MAHA workflow summary."

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

                            val newRun = Run(
                                runId = runId,
                                title = "Run All",
                                timestamp = runTimestamp,
                                results = resultList,
                                logs = logList
                            )

                            runList.add(0, newRun)
                            return@launch
                        }

                        enabledAgents.forEachIndexed { index, agent ->
                            executionStateMap[agent.id] = "RUNNING"

                            logList.add(
                                ExecutionLog(
                                    message = "${agent.name} is RUNNING with input: $currentInput",
                                    timestamp = getCurrentTimeText()
                                )
                            )

                            delay(700)

                            val outputText = buildDummyOutput(
                                agentName = agent.name,
                                stepNumber = index + 1,
                                input = currentInput
                            )

                            val result = RunResult(
                                agentId = agent.id,
                                agentName = agent.name,
                                status = "SUCCESS",
                                inputText = currentInput,
                                outputText = outputText,
                                timestamp = getCurrentTimeText(),
                                order = index + 1
                            )

                            resultList.add(result)
                            executionStateMap[agent.id] = "SUCCESS"

                            logList.add(
                                ExecutionLog(
                                    message = "${agent.name} finished with SUCCESS and output: $outputText",
                                    timestamp = getCurrentTimeText()
                                )
                            )

                            currentInput = outputText

                            delay(300)
                        }

                        logList.add(
                            ExecutionLog(
                                message = "Run All completed",
                                timestamp = getCurrentTimeText()
                            )
                        )

                        val newRun = Run(
                            runId = runId,
                            title = "Run All",
                            timestamp = runTimestamp,
                            results = resultList,
                            logs = logList
                        )

                        runList.add(0, newRun)
                    }
                }
            )
        }
    }
}

fun getCurrentTimeText(): String {
    return SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss",
        Locale.getDefault()
    ).format(Date())
}

fun buildDummyOutput(
    agentName: String,
    stepNumber: Int,
    input: String
): String {
    return "Step $stepNumber - $agentName output based on: $input"
}