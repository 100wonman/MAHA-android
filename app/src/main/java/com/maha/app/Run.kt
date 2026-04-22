// Run.kt

package com.maha.app

data class Run(
    val runId: String,
    val title: String,
    val timestamp: String,
    val results: List<RunResult>,
    val logs: List<ExecutionLog>
)