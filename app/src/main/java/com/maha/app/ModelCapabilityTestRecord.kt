// ModelCapabilityTestRecord.kt

package com.maha.app

data class ModelCapabilityTestRecord(
    val providerName: String,
    val modelName: String,
    val testType: String,
    val status: String,
    val testedAt: String,
    val latencyMs: Long,
    val message: String,
    val sampleOutput: String
)