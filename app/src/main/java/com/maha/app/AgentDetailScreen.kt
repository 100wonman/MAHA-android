// AgentDetailScreen.kt

package com.maha.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
    onSaveClick: (Agent) -> Unit,
    onDeleteClick: (Agent) -> Unit,
    onRunClick: (Run) -> Unit,
    onRunItemClick: (Run) -> Unit,
    onBackClick: () -> Unit
) {
    var editedName by remember(agent.id) { mutableStateOf(agent.name) }
    var editedDescription by remember(agent.id) { mutableStateOf(agent.description) }
    var editedIsEnabled by remember(agent.id) { mutableStateOf(agent.isEnabled) }
    var runStatus by remember(agent.id) { mutableStateOf("Idle") }
    var latestOutputText by remember(agent.id) { mutableStateOf("") }

    LaunchedEffect(agent.id, agent.name, agent.description, agent.isEnabled) {
        editedName = agent.name
        editedDescription = agent.description
        editedIsEnabled = agent.isEnabled
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Agent Detail",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = editedName,
                            onValueChange = { editedName = it },
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editedDescription,
                            onValueChange = { editedDescription = it },
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
                                style = MaterialTheme.typography.bodyLarge
                            )

                            Switch(
                                checked = editedIsEnabled,
                                onCheckedChange = { editedIsEnabled = it }
                            )
                        }

                        Text(
                            text = "Status: ${agent.status}",
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Text(
                            text = "Input Format: ${agent.inputFormat}",
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Text(
                            text = "Output Format: ${agent.outputFormat}",
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Text(
                            text = "Run Status: $runStatus",
                            style = MaterialTheme.typography.bodyLarge
                        )

                        if (latestOutputText.isNotEmpty()) {
                            Text(
                                text = "Latest Output: $latestOutputText",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val updatedAgent = agent.copy(
                            name = editedName,
                            description = editedDescription,
                            isEnabled = editedIsEnabled
                        )
                        onSaveClick(updatedAgent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Save")
                }
            }

            item {
                Button(
                    onClick = {
                        onDeleteClick(agent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Delete")
                }
            }

            item {
                Button(
                    onClick = {
                        val currentAgent = agent.copy(
                            name = editedName,
                            description = editedDescription,
                            isEnabled = editedIsEnabled
                        )

                        onSaveClick(currentAgent)

                        runStatus = "RUNNING"

                        val timeText = getCurrentTimeText()
                        val inputText = "User request for ${currentAgent.name}"
                        val outputText =
                            "Single run output from ${currentAgent.name} based on: $inputText"

                        val result = RunResult(
                            agentId = currentAgent.id,
                            agentName = currentAgent.name,
                            status = "SUCCESS",
                            inputText = inputText,
                            outputText = outputText,
                            timestamp = timeText,
                            order = 1
                        )

                        val logs = listOf(
                            ExecutionLog(
                                message = "${currentAgent.name} is RUNNING with input: $inputText",
                                timestamp = timeText
                            ),
                            ExecutionLog(
                                message = "${currentAgent.name} finished with SUCCESS and output: $outputText",
                                timestamp = getCurrentTimeText()
                            )
                        )

                        val run = Run(
                            runId = "run_${System.currentTimeMillis()}",
                            title = "Single Run - ${currentAgent.name}",
                            timestamp = timeText,
                            results = listOf(result),
                            logs = logs
                        )

                        onRunClick(run)
                        latestOutputText = outputText
                        runStatus = "SUCCESS"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Run")
                }
            }

            item {
                Button(
                    onClick = onBackClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Back")
                }
            }

            item {
                HorizontalDivider()
            }

            item {
                Text(
                    text = "Recent Runs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (runList.isEmpty()) {
                item {
                    Text(
                        text = "No runs yet.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                items(runList) { run ->
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