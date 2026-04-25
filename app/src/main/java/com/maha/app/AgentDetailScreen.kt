// AgentDetailScreen.kt

package com.maha.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDetailScreen(
    agent: Agent,
    runList: List<Run>,
    isAnyExecutionRunning: Boolean,
    isCurrentAgentRunning: Boolean,
    onMenuClick: () -> Unit,
    onSaveClick: (Agent) -> Unit,
    onDeleteClick: (Agent) -> Unit,
    onRunClick: (Agent) -> Unit,
    onRunItemClick: (Run) -> Unit,
    onOpenModelCatalogClick: (Agent) -> Unit,
    onBackClick: () -> Unit
) {
    val providerOptions = listOf(
        ModelProviderType.DUMMY,
        ModelProviderType.GOOGLE,
        ModelProviderType.NVIDIA
    )

    var editedName by remember(agent.id) { mutableStateOf(agent.name) }
    var editedDescription by remember(agent.id) { mutableStateOf(agent.description) }
    var editedIsEnabled by remember(agent.id) { mutableStateOf(agent.isEnabled) }
    var editedProviderName by remember(agent.id) {
        mutableStateOf(sanitizeProviderNameForAgentDetail(agent.providerName))
    }
    var editedModelName by remember(agent.id) {
        mutableStateOf(sanitizeModelForProvider(agent.providerName, agent.modelName))
    }
    var providerDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(
        agent.id,
        agent.name,
        agent.description,
        agent.isEnabled,
        agent.providerName,
        agent.modelName
    ) {
        editedName = agent.name
        editedDescription = agent.description
        editedIsEnabled = agent.isEnabled
        editedProviderName = sanitizeProviderNameForAgentDetail(agent.providerName)
        editedModelName = sanitizeModelForProvider(editedProviderName, agent.modelName)
    }

    val currentRunState = when {
        isCurrentAgentRunning -> "RUNNING"
        runList.isNotEmpty() -> {
            val latestResult = runList.firstOrNull()?.results?.firstOrNull()
            latestResult?.status ?: "WAITING"
        }

        else -> "WAITING"
    }

    val stateMessage = when (currentRunState) {
        "RUNNING" -> "현재 이 워커가 실행 중입니다. Run 버튼은 잠시 비활성화됩니다."
        "SUCCESS" -> "가장 최근 실행이 정상 완료되었습니다."
        "FAILED" -> "가장 최근 실행이 실패했습니다."
        else -> "실행 전 대기 상태입니다."
    }

    val selectedModelUsage = ModelUsageManager.getTodayUsage(editedModelName)
    val selectedModelBlocked = ModelUsageManager.isModelBlocked(editedModelName)
    val selectedModelBlockedUntil = ModelUsageManager.getBlockedUntilText(editedModelName)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Agent Detail",
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
                    containerColor = androidx.compose.ui.graphics.Color(0xFF111827),
                    titleContentColor = androidx.compose.ui.graphics.Color(0xFFF8FAFC),
                    navigationIconContentColor = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                )
            )
        },
        containerColor = androidx.compose.ui.graphics.Color(0xFF070B12)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color(0xFF070B12))
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF1A2230)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = editedName,
                            onValueChange = { editedName = it },
                            enabled = !isAnyExecutionRunning,
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editedDescription,
                            onValueChange = { editedDescription = it },
                            enabled = !isAnyExecutionRunning,
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Enabled",
                                style = MaterialTheme.typography.bodyLarge,
                                color = androidx.compose.ui.graphics.Color(0xFFF3F6FB)
                            )

                            Switch(
                                checked = editedIsEnabled,
                                onCheckedChange = { editedIsEnabled = it },
                                enabled = !isAnyExecutionRunning
                            )
                        }

                        ExposedDropdownMenuBox(
                            expanded = providerDropdownExpanded,
                            onExpandedChange = {
                                if (!isAnyExecutionRunning) {
                                    providerDropdownExpanded = !providerDropdownExpanded
                                }
                            }
                        ) {
                            OutlinedTextField(
                                value = editedProviderName,
                                onValueChange = {},
                                readOnly = true,
                                enabled = !isAnyExecutionRunning,
                                label = { Text("Provider") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = providerDropdownExpanded
                                    )
                                },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = providerDropdownExpanded,
                                onDismissRequest = {
                                    providerDropdownExpanded = false
                                }
                            ) {
                                providerOptions.forEach { provider ->
                                    DropdownMenuItem(
                                        text = { Text(provider) },
                                        onClick = {
                                            editedProviderName = provider
                                            editedModelName = getDefaultModelForProvider(provider)
                                            providerDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        InfoRow(label = "Status", value = agent.status)
                        InfoRow(label = "Input Format", value = agent.inputFormat)
                        InfoRow(label = "Output Format", value = agent.outputFormat)
                        InfoRow(label = "Current Provider", value = editedProviderName)
                        InfoRow(label = "Current Model", value = editedModelName)
                        InfoRow(label = "Today Model Usage", value = selectedModelUsage.requestCount.toString())

                        if (selectedModelBlocked) {
                            StatusPanel(
                                title = "Model Status",
                                status = "FAILED",
                                message = "현재 선택 모델은 일시 제한 상태입니다. 재시도 가능 예상 시각: $selectedModelBlockedUntil"
                            )
                        } else {
                            StatusPanel(
                                title = "Model Status",
                                status = "SUCCESS",
                                message = "현재 선택 모델은 사용 가능 상태입니다."
                            )
                        }

                        SecondaryActionButton(
                            text = "모델 카탈로그에서 선택",
                            enabled = !isAnyExecutionRunning && editedProviderName != ModelProviderType.DUMMY,
                            onClick = {
                                val currentAgent = agent.copy(
                                    name = editedName,
                                    description = editedDescription,
                                    isEnabled = editedIsEnabled,
                                    providerName = editedProviderName,
                                    modelName = sanitizeModelForProvider(
                                        providerName = editedProviderName,
                                        modelName = editedModelName
                                    )
                                )
                                onSaveClick(currentAgent)
                                onOpenModelCatalogClick(currentAgent)
                            }
                        )

                        StatusPanel(
                            title = "Run Status",
                            status = currentRunState,
                            message = stateMessage
                        )
                    }
                }
            }

            item {
                SecondaryActionButton(
                    text = "Save",
                    enabled = !isAnyExecutionRunning,
                    onClick = {
                        val updatedAgent = agent.copy(
                            name = editedName,
                            description = editedDescription,
                            isEnabled = editedIsEnabled,
                            providerName = editedProviderName,
                            modelName = sanitizeModelForProvider(
                                providerName = editedProviderName,
                                modelName = editedModelName
                            )
                        )
                        onSaveClick(updatedAgent)
                    }
                )
            }

            item {
                SecondaryActionButton(
                    text = "Delete",
                    enabled = !isAnyExecutionRunning,
                    onClick = {
                        onDeleteClick(agent)
                    }
                )
            }

            item {
                PrimaryActionButton(
                    text = if (isCurrentAgentRunning) "Run Disabled While Running" else "Run",
                    enabled = !isAnyExecutionRunning,
                    onClick = {
                        val currentAgent = agent.copy(
                            name = editedName,
                            description = editedDescription,
                            isEnabled = editedIsEnabled,
                            providerName = editedProviderName,
                            modelName = sanitizeModelForProvider(
                                providerName = editedProviderName,
                                modelName = editedModelName
                            )
                        )
                        onSaveClick(currentAgent)
                        onRunClick(currentAgent)
                    }
                )
            }

            item {
                SecondaryActionButton(
                    text = "Back",
                    enabled = !isAnyExecutionRunning,
                    onClick = onBackClick
                )
            }

            item {
                HorizontalDivider(
                    color = androidx.compose.ui.graphics.Color(0xFF273244)
                )
            }

            item {
                Text(
                    text = "Recent Runs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                )
            }

            if (runList.isEmpty()) {
                item {
                    EmptyInfoCard(text = "No runs yet.")
                }
            } else {
                items(runList) { run ->
                    RunItemCard(
                        run = run,
                        onClick = {
                            if (!isAnyExecutionRunning) {
                                onRunItemClick(run)
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun sanitizeProviderNameForAgentDetail(providerName: String): String {
    return when (providerName) {
        ModelProviderType.GOOGLE -> ModelProviderType.GOOGLE
        ModelProviderType.NVIDIA -> ModelProviderType.NVIDIA
        ModelProviderType.DUMMY -> ModelProviderType.DUMMY
        else -> ModelProviderType.DUMMY
    }
}

private fun sanitizeModelForProvider(
    providerName: String,
    modelName: String
): String {
    val safeProviderName = sanitizeProviderNameForAgentDetail(providerName)
    val safeModelName = modelName.trim().removePrefix("models/")

    return when (safeProviderName) {
        ModelProviderType.DUMMY -> "dummy"

        ModelProviderType.GOOGLE -> {
            if (
                safeModelName.startsWith("gemini") ||
                safeModelName.startsWith("gemma")
            ) {
                GeminiModelType.sanitize(safeModelName)
            } else {
                GeminiModelType.DEFAULT
            }
        }

        ModelProviderType.NVIDIA -> {
            if (safeModelName.contains("/")) {
                safeModelName
            } else {
                "meta/llama-3.1-8b-instruct"
            }
        }

        else -> "dummy"
    }
}

private fun getDefaultModelForProvider(providerName: String): String {
    return when (sanitizeProviderNameForAgentDetail(providerName)) {
        ModelProviderType.GOOGLE -> GeminiModelType.DEFAULT
        ModelProviderType.NVIDIA -> "meta/llama-3.1-8b-instruct"
        else -> "dummy"
    }
}