// ModelTestRecord.kt

package com.maha.app

data class ModelTestRecord(
    val providerName: String,
    val modelName: String,
    val status: String,
    val lastTestedAt: String,
    val httpStatusCode: Int,
    val message: String,
    val latencyMs: Long
)