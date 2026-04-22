// MainActivity.kt

package com.maha.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview

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

@Preview(showBackground = true)
@Composable
fun MAHAAppPreview() {
    MaterialTheme {
        MAHAApp()
    }
}