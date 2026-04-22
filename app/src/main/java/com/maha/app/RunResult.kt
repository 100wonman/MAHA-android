// RunResult.kt

package com.maha.app

data class RunResult(
    val agentId: String,
    val agentName: String,
    val status: String,
    val resultText: String,
    val timestamp: String
)