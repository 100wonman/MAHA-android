// RunResult.kt

package com.maha.app

data class RunResult(
    val agentId: String,
    val agentName: String,
    val status: String,
    val inputText: String,
    val outputText: String,
    val timestamp: String,
    val order: Int = 0
)