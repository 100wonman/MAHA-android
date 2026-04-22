// AgentDetailScreen.kt

package com.maha.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDetailScreen(
    agent: Agent,
    runResults: List<RunResult>,
    onRunClick: (RunResult) -> Unit,
    onBackClick: () -> Unit
) {
    var runStatus by remember { mutableStateOf("Idle") }
    var latestResultText by remember { mutableStateOf("") }

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
                        Text(
                            text = "Name: ${agent.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Description: ${agent.description}",
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Text(
                            text = "Status: ${agent.status}",
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Text(
                            text = "Run Status: $runStatus",
                            style = MaterialTheme.typography.bodyLarge
                        )

                        if (latestResultText.isNotEmpty()) {
                            Text(
                                text = "Latest Result: $latestResultText",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        runStatus = "Running"

                        val timeText = SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss",
                            Locale.getDefault()
                        ).format(Date())

                        val resultText = "${agent.name} executed successfully at $timeText."

                        val newResult = RunResult(
                            agentId = agent.id,
                            agentName = agent.name,
                            status = "Completed",
                            resultText = resultText,
                            timestamp = timeText
                        )

                        onRunClick(newResult)
                        latestResultText = resultText
                        runStatus = "Completed"
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
                    text = "Recent Run Results",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (runResults.isEmpty()) {
                item {
                    Text(
                        text = "No run results yet.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                items(runResults) { result ->
                    RunResultItem(result = result)
                }
            }
        }
    }
}

@Composable
fun RunResultItem(result: RunResult) {
    Card(
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
                text = "${result.order}. ${result.agentName}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Status: ${result.status}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Time: ${result.timestamp}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Result: ${result.resultText}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}