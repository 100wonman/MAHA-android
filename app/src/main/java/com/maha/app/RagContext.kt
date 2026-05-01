package com.maha.app

data class RagContext(
    val query: String,
    val enabled: Boolean,
    val results: List<RagSearchResult>,
    val contextText: String,
    val totalTokenEstimate: Int,
    val maxResults: Int,
    val maxContextChars: Int,
    val createdAt: Long,
    val fallback: Boolean,
    val fallbackReason: String?
)
