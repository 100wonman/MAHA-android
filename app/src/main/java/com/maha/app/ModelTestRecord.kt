// ModelTestRecord.kt

package com.maha.app

data class ModelTestRecord(
    val providerName: String,
    val modelName: String,
    val status: String,
    val lastTestedAt: String,
    val httpStatusCode: Int,
    val message: String,
    val latencyMs: Long,
    val testCount: Int = 0,
    val successCount: Int = 0,
    val successRate: Int = 0,
    val averageLatencyMs: Long = 0L,
    val selfReportedInfo: ModelInfo = ModelInfo()
)