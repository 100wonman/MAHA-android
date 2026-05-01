package com.maha.app

enum class EmbeddingStatus {
    NONE,
    PENDING,
    READY,
    FAILED,
    STALE
}

data class RagChunk(
    val chunkId: String,
    val sourceType: String,
    val sourceId: String,
    val sessionId: String? = null,
    val workerId: String? = null,
    val documentId: String? = null,
    val title: String,
    val text: String,
    val textPreview: String,
    val tokenEstimate: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val embeddingStatus: EmbeddingStatus = EmbeddingStatus.NONE,
    val embeddingId: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class RagIndexMetadata(
    val indexVersion: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val status: String,
    val chunkCount: Int,
    val sourceCount: Int,
    val embeddingModel: String? = null,
    val embeddingEnabled: Boolean = false,
    val sources: List<RagIndexSourceEntry> = emptyList(),
    val chunks: List<RagIndexChunkEntry> = emptyList()
)

data class RagIndexSourceEntry(
    val sourceType: String,
    val sourceId: String,
    val title: String,
    val filePath: String? = null,
    val chunkCount: Int,
    val indexedAt: Long,
    val status: String
)

data class RagIndexChunkEntry(
    val chunkId: String,
    val sourceType: String,
    val sourceId: String,
    val filePath: String,
    val textPreview: String,
    val embeddingStatus: EmbeddingStatus = EmbeddingStatus.NONE,
    val updatedAt: Long
)
