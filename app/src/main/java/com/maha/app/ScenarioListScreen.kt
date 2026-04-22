// ScenarioListScreen.kt

package com.maha.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
fun ScenarioListScreen(
    scenarioList: List<Scenario>,
    onScenarioClick: (Scenario) -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Scenario List",
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
            if (scenarioList.isEmpty()) {
                item {
                    Text(
                        text = "No scenarios saved.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                items(scenarioList) { scenario ->
                    ScenarioItemCard(
                        scenario = scenario,
                        onClick = {
                            onScenarioClick(scenario)
                        }
                    )
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenarioItemCard(
    scenario: Scenario,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = scenario.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Saved At: ${scenario.savedAt}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Agent Count: ${scenario.agents.size}",
                style = MaterialTheme.typography.bodyMedium
            )

            if (scenario.agents.isNotEmpty()) {
                Text(
                    text = "Agents: ${scenario.agents.joinToString { it.name }}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}