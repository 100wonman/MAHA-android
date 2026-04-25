// ExecutionHistoryLog.kt

package com.maha.app

data class ExecutionHistoryLog(
    val id: String,
    val runId: String,
    val executedAt: String,
    val workerName: String,
    val providerName: String,
    val modelName: String,
    val status: String,
    val latencyMs: Long,
    val errorMessage: String,
    val inputText: String,
    val outputText: String
)