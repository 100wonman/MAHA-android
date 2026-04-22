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
        listOf(
            Agent(
                id = "agent_001",
                name = "Planner",
                description = "작업 순서를 먼저 정리하는 에이전트",
                status = "Enabled"
            ),
            Agent(
                id = "agent_002",
                name = "Researcher",
                description = "필요한 정보를 조사하고 정리하는 에이전트",
                status = "Enabled"
            ),
            Agent(
                id = "agent_003",
                name = "Writer",
                description = "최종 결과를 문장 형태로 작성하는 에이전트",
                status = "Enabled"
            )
        )
    }

    val runList = remember { mutableStateListOf<Run>() }
    val executionStateMap = remember { mutableStateMapOf<String, String>() }

    var selectedAgent by remember { mutableStateOf<Agent?>(null) }
    var selectedRun by remember { mutableStateOf<Run?>(null) }

    agentList.forEach { agent ->
        if (executionStateMap[agent.id] == null) {
            executionStateMap[agent.id] = "WAITING"
        }
    }

    when {
        selectedRun != null -> {
            RunDetailScreen(
                run = selectedRun!!,
                onBackClick = {
                    selectedRun = null
                }
            )
        }

        selectedAgent != null -> {
            AgentDetailScreen(
                agent = selectedAgent!!,
                runList = runList.filter { run ->
                    run.results.any { it.agentId == selectedAgent!!.id }
                },
                onRunClick = { newRun ->
                    runList.add(0, newRun)
                },
                onRunItemClick = { run ->
                    selectedRun = run
                },
                onBackClick = {
                    selectedAgent = null
                }
            )
        }

        else -> {
            AgentListScreen(
                agentList = agentList,
                runList = runList,
                executionStateMap = executionStateMap,
                onAgentClick = { agent ->
                    selectedAgent = agent
                },
                onRunItemClick = { run ->
                    selectedRun = run
                },
                onRunAllClick = {
                    scope.launch {
                        agentList.forEach { agent ->
                            executionStateMap[agent.id] = "WAITING"
                        }

                        val runId = "run_${System.currentTimeMillis()}"
                        val runTimestamp = getCurrentTimeText()

                        val resultList = mutableListOf<RunResult>()
                        val logList = mutableListOf<ExecutionLog>()

                        logList.add(
                            ExecutionLog(
                                message = "Run All started",
                                timestamp = getCurrentTimeText()
                            )
                        )

                        agentList.forEachIndexed { index, agent ->
                            executionStateMap[agent.id] = "RUNNING"

                            logList.add(
                                ExecutionLog(
                                    message = "${agent.name} is RUNNING",
                                    timestamp = getCurrentTimeText()
                                )
                            )

                            delay(700)

                            val result = RunResult(
                                agentId = agent.id,
                                agentName = agent.name,
                                status = "SUCCESS",
                                resultText = "${agent.name} executed successfully in step ${index + 1}.",
                                timestamp = getCurrentTimeText(),
                                order = index + 1
                            )

                            resultList.add(result)
                            executionStateMap[agent.id] = "SUCCESS"

                            logList.add(
                                ExecutionLog(
                                    message = "${agent.name} finished with SUCCESS",
                                    timestamp = getCurrentTimeText()
                                )
                            )

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