// ModelMetadata.kt

package com.maha.app

data class ModelMetadata(
    val providerName: String,
    val modelName: String,
    val apiCapabilities: List<String> = emptyList(),
    val testedCapabilities: List<String> = emptyList(),
    val selfReportedCapabilities: List<String> = emptyList(),
    val manualCapabilities: List<String> = emptyList(),
    val finalCapabilities: List<String> = emptyList(),
    val apiRawText: String = "",
    val selfReportedRawText: String = "",
    val confidenceLevel: String = ModelMetadataConfidence.UNKNOWN,
    val updatedAt: String = ""
)