// AgentListScreen.kt

package com.maha.app

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentListScreen(
    agentList: List<Agent>,
    runList: List<Run>,
    executionStateMap: Map<String, String>,
    onAgentClick: (Agent) -> Unit,
    onAddAgentClick: () -> Unit,
    onSaveScenarioClick: () -> Unit,
    onOpenScenarioListClick: () -> Unit,
    onMoveUpClick: (Agent) -> Unit,
    onMoveDownClick: (Agent) -> Unit,
    onRunItemClick: (Run) -> Unit,
    onRunAllClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "MAHA - Agent List",
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
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(agentList) { index, agent ->
                AgentListItem(
                    agent = agent,
                    executionState = executionStateMap[agent.id] ?: "WAITING",
                    canMoveUp = index > 0,
                    canMoveDown = index < agentList.lastIndex,
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
                Button(
                    onClick = onAddAgentClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Add Agent")
                }
            }

            item {
                Button(
                    onClick = onSaveScenarioClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Save Scenario")
                }
            }

            item {
                Button(
                    onClick = onOpenScenarioListClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Load Scenario")
                }
            }

            item {
                Button(
                    onClick = onRunAllClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Run All")
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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
    onClick: () -> Unit,
    onMoveUpClick: () -> Unit,
    onMoveDownClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = agent.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Description: ${agent.description}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(
                text = "Enabled: ${if (agent.isEnabled) "ON" else "OFF"}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(
                text = "Status: ${agent.status}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(
                text = "Input: ${agent.inputFormat}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = "Output: ${agent.outputFormat}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = "Run State: $executionState",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onMoveUpClick,
                    enabled = canMoveUp,
                    modifier = Modifier.width(110.dp)
                ) {
                    Text(text = "Move Up")
                }

                Button(
                    onClick = onMoveDownClick,
                    enabled = canMoveDown,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .width(110.dp)
                ) {
                    Text(text = "Move Down")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunItemCard(
    run: Run,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = run.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Run ID: ${run.runId}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Time: ${run.timestamp}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Result Count: ${run.results.size}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Log Count: ${run.logs.size}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}