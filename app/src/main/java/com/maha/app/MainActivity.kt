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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
                status = "Disabled"
            ),
            Agent(
                id = "agent_003",
                name = "Writer",
                description = "최종 결과를 문장 형태로 작성하는 에이전트",
                status = "Enabled"
            )
        )
    }

    var selectedAgent by remember { mutableStateOf<Agent?>(null) }
    val runResults = remember { mutableStateListOf<RunResult>() }

    if (selectedAgent == null) {
        AgentListScreen(
            agentList = agentList,
            runResults = runResults,
            onAgentClick = { agent ->
                selectedAgent = agent
            },
            onRunAllClick = {
                val timeText = SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    Locale.getDefault()
                ).format(Date())

                agentList.forEachIndexed { index, agent ->
                    val result = RunResult(
                        agentId = agent.id,
                        agentName = agent.name,
                        status = "Completed",
                        resultText = "${agent.name} executed successfully in step ${index + 1}.",
                        timestamp = timeText,
                        order = runResults.size + 1
                    )
                    runResults.add(result)
                }
            }
        )
    } else {
        AgentDetailScreen(
            agent = selectedAgent!!,
            runResults = runResults.filter { it.agentId == selectedAgent!!.id },
            onRunClick = { result ->
                val newResult = result.copy(order = runResults.size + 1)
                runResults.add(newResult)
            },
            onBackClick = {
                selectedAgent = null
            }
        )
    }
}