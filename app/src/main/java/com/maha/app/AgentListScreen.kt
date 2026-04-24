// AgentListScreen.kt

package com.maha.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentListScreen(
    agentList: List<Agent>,
    runList: List<Run>,
    executionStateMap: Map<String, String>,
    isRunAllRunning: Boolean,
    promptText: String,
    onPromptTextChange: (String) -> Unit,
    onMenuClick: () -> Unit,
    onAgentClick: (Agent) -> Unit,
    onAddAgentClick: () -> Unit,
    onSaveScenarioClick: () -> Unit,
    onOpenScenarioListClick: () -> Unit,
    onMoveUpClick: (Agent) -> Unit,
    onMoveDownClick: (Agent) -> Unit,
    onRunItemClick: (Run) -> Unit,
    onRunAllClick: () -> Unit
) {
    val overallStatusText = if (isRunAllRunning) "RUNNING" else "WAITING"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "MAHA - Agent List",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Text(
                            text = "☰",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF111827),
                    titleContentColor = Color(0xFFF8FAFC),
                    navigationIconContentColor = Color(0xFFF8FAFC)
                )
            )
        },
        containerColor = Color(0xFF070B12)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070B12))
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                StatusPanel(
                    title = "Run All Status",
                    status = overallStatusText,
                    message = if (isRunAllRunning) {
                        "전체 워커가 순서대로 실행 중입니다. Run All 버튼은 잠시 비활성화됩니다."
                    } else {
                        "프롬프트를 입력한 뒤 Run Prompt 버튼으로 실행할 수 있습니다."
                    }
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A2230)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Prompt Input",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF8FAFC)
                        )

                        OutlinedTextField(
                            value = promptText,
                            onValueChange = onPromptTextChange,
                            enabled = !isRunAllRunning,
                            label = { Text("Enter prompt") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )

                        PrimaryActionButton(
                            text = if (isRunAllRunning) "Running..." else "Run Prompt",
                            enabled = !isRunAllRunning,
                            onClick = onRunAllClick
                        )
                    }
                }
            }

            itemsIndexed(agentList) { index, agent ->
                AgentListItem(
                    agent = agent,
                    executionState = executionStateMap[agent.id] ?: "WAITING",
                    canMoveUp = index > 0 && !isRunAllRunning,
                    canMoveDown = index < agentList.lastIndex && !isRunAllRunning,
                    isInteractionEnabled = !isRunAllRunning,
                    onClick = {
                        onAgentClick(agent)
                    },
                    onMoveUpClick = {
                        onMoveUpClick(agent)
                    },
                    onMoveDownClick = {
                        onMoveDownClick(agent)
                    }
                )
            }

            item {
                PrimaryActionButton(
                    text = "Add Agent",
                    enabled = !isRunAllRunning,
                    onClick = onAddAgentClick
                )
            }

            item {
                SecondaryActionButton(
                    text = "Save Scenario",
                    enabled = !isRunAllRunning,
                    onClick = onSaveScenarioClick
                )
            }

            item {
                SecondaryActionButton(
                    text = "Load Scenario",
                    enabled = !isRunAllRunning,
                    onClick = onOpenScenarioListClick
                )
            }

            item {
                PrimaryActionButton(
                    text = if (isRunAllRunning) "Run All Disabled While Running" else "Run All",
                    enabled = !isRunAllRunning,
                    onClick = onRunAllClick
                )
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = Color(0xFF273244)
                )
            }

            item {
                Text(
                    text = "Recent Runs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF8FAFC)
                )
            }

            if (runList.isEmpty()) {
                item {
                    EmptyInfoCard(text = "No runs yet.")
                }
            } else {
                itemsIndexed(runList) { _, run ->
                    RunItemCard(
                        run = run,
                        onClick = {
                            onRunItemClick(run)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentListItem(
    agent: Agent,
    executionState: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isInteractionEnabled: Boolean,
    onClick: () -> Unit,
    onMoveUpClick: () -> Unit,
    onMoveDownClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = isInteractionEnabled,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A2230)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = agent.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF8FAFC)
                    )

                    Text(
                        text = agent.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFD3DBE7),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                StateBadge(status = executionState)
            }

            InfoRow(label = "Enabled", value = if (agent.isEnabled) "ON" else "OFF")
            InfoRow(label = "Status", value = agent.status)
            InfoRow(label = "Input", value = agent.inputFormat)
            InfoRow(label = "Output", value = agent.outputFormat)

            StatusPanel(
                title = "Worker State",
                status = executionState,
                message = when (executionState) {
                    "RUNNING" -> "현재 이 워커가 실행 중입니다."
                    "SUCCESS" -> "가장 최근 실행이 정상 완료되었습니다."
                    "FAILED" -> "가장 최근 실행에서 실패했습니다."
                    else -> "아직 실행 전이거나 다음 실행을 기다리는 상태입니다."
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.End
            ) {
                SecondarySmallButton(
                    text = "Move Up",
                    enabled = canMoveUp,
                    onClick = onMoveUpClick
                )

                SecondarySmallButton(
                    text = "Move Down",
                    enabled = canMoveDown,
                    onClick = onMoveDownClick,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
fun StatusPanel(
    title: String,
    status: String,
    message: String
) {
    val backgroundColor = getStatusColor(status).copy(alpha = 0.16f)
    val borderColor = getStatusColor(status)

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF8FAFC)
            )

            Surface(
                color = borderColor.copy(alpha = 0.26f)
            ) {
                Text(
                    text = "STATE: $status",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = borderColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }

            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFE6EDF7)
            )
        }
    }
}

@Composable
fun StateBadge(status: String) {
    val color = getStatusColor(status)

    Surface(
        color = color.copy(alpha = 0.24f)
    ) {
        Text(
            text = status,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFB6C2D2)
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFF3F6FB)
        )
    }
}

@Composable
fun EmptyInfoCard(text: String) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141B27))
                .padding(16.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFE5ECF6)
            )
        }
    }
}

fun getStatusColor(status: String): Color {
    return when (status) {
        "RUNNING" -> Color(0xFF3B82F6)
        "SUCCESS" -> Color(0xFF22C55E)
        "FAILED" -> Color(0xFFEF4444)
        else -> Color(0xFF94A3B8)
    }
}

@Composable
fun PrimaryActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFDCF2FF),
            contentColor = Color(0xFF09131D),
            disabledContainerColor = Color(0xFF4B5563),
            disabledContentColor = Color(0xFFCBD5E1)
        )
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SecondaryActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF273244),
            contentColor = Color(0xFFF8FAFC),
            disabledContainerColor = Color(0xFF1F2937),
            disabledContentColor = Color(0xFF7C8798)
        )
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SecondarySmallButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.width(118.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFDCE8F7),
            contentColor = Color(0xFF0B1220),
            disabledContainerColor = Color(0xFF3A4352),
            disabledContentColor = Color(0xFF8E9AAC)
        )
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunItemCard(
    run: Run,
    onClick: () -> Unit
) {
    val successCount = run.results.count { it.status == "SUCCESS" }
    val failedCount = run.results.count { it.status == "FAILED" }
    val summaryStatus = when {
        failedCount > 0 -> "FAILED"
        successCount > 0 -> "SUCCESS"
        else -> "WAITING"
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A2230)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = run.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF8FAFC)
                    )

                    Text(
                        text = run.timestamp,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD5DDE8),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                StateBadge(status = summaryStatus)
            }

            InfoRow(label = "Run ID", value = run.runId)
            InfoRow(label = "Results", value = run.results.size.toString())
            InfoRow(label = "Logs", value = run.logs.size.toString())

            val latestResult = run.results.lastOrNull()
            if (latestResult != null) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF101722))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Latest Output",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF8FAFC)
                        )

                        Text(
                            text = latestResult.outputText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFE5ECF6)
                        )
                    }
                }
            }
        }
    }
}