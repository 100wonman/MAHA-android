// Scenario.kt

package com.maha.app

data class Scenario(
    val id: String,
    val name: String,
    val agents: List<Agent>,
    val savedAt: String
)