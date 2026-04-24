// ModelRequest.kt

package com.maha.app

data class ModelRequest(
    val agentId: String,
    val agentName: String,
    val inputText: String,
    val stepNumber: Int,
    val runType: String
)