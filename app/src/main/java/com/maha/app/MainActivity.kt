// MainActivity.kt

package com.maha.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

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
    var selectedAgent by remember { mutableStateOf<Agent?>(null) }

    if (selectedAgent == null) {
        AgentListScreen(
            onAgentClick = { agent ->
                selectedAgent = agent
            }
        )
    } else {
        AgentDetailScreen(
            agent = selectedAgent!!,
            onBackClick = {
                selectedAgent = null
            }
        )
    }
}