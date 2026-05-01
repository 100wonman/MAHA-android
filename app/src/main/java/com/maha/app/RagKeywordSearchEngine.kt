package com.maha.app

import org.json.JSONObject
import java.io.File
import java.util.Locale

data class RagSearchResult(
    val chunkId: String,
    val sourceType: String,
    val sourceId: String,
    val title: String,
    val textPreview: String,
    val matchedTextSnippet: String,
    val score: Int,
    val filePath: String,
    val updatedAt: Long
)

class RagKeywordSearchEngine(
    private val ragStorageManager: RagStorageManager,
    private val ragIndexStore: RagIndexStore
) {
    fun search(
        query: String,
        topK: Int = DEFAULT_TOP_K,
        maxLoadedChunks: Int = DEFAULT_MAX_LOADED_CHUNKS
    ): List<RagSearchResult> {
        val tokens = query.toSearchTokens()
        if (tokens.isEmpty()) return emptyList()

        val metadata = runCatching { ragIndexStore.loadIndexMetadata() }.getOrElse { return emptyList() }
        if (metadata.chunks.isEmpty()) return emptyList()

        val sourceTitleMap = metadata.sources.associate { source ->
            source.sourceType to source.sourceId to source.title
        }

        val directCandidates = metadata.chunks.filter { chunkEntry ->
            val sourceTitle = sourceTitleMap[chunkEntry.sourceType to chunkEntry.sourceId].orEmpty()
            val metadataText = listOf(
                sourceTitle,
                chunkEntry.textPreview,
                chunkEntry.sourceType,
                chunkEntry.sourceId
            ).joinToString(separator = " ")
            metadataText.matchesAnyToken(tokens)
        }

        val candidates = buildList {
            addAll(directCandidates)
            if (size < topK) {
                metadata.chunks.forEach { chunkEntry ->
                    if (size >= maxLoadedChunks) return@forEach
                    if (none { it.chunkId == chunkEntry.chunkId }) add(chunkEntry)
                }
            }
        }.take(maxLoadedChunks.coerceAtLeast(1))

        return candidates.mapNotNull { chunkEntry ->
            val chunk = loadChunk(chunkEntry.filePath) ?: return@mapNotNull null
            val sourceTitle = sourceTitleMap[chunkEntry.sourceType to chunkEntry.sourceId].orEmpty()
            val score = calculateScore(
                tokens = tokens,
                sourceTitle = sourceTitle,
                chunkEntry = chunkEntry,
                chunk = chunk
            )
            if (score <= 0) return@mapNotNull null

            RagSearchResult(
                chunkId = chunk.chunkId,
                sourceType = chunk.sourceType,
                sourceId = chunk.sourceId,
                title = chunk.title.ifBlank { sourceTitle.ifBlank { chunk.sourceId } },
                textPreview = chunk.textPreview,
                matchedTextSnippet = buildMatchedSnippet(chunk.text, tokens, fallback = chunk.textPreview),
                score = score,
                filePath = chunkEntry.filePath,
                updatedAt = chunk.updatedAt
            )
        }
            .sortedWith(
                compareByDescending<RagSearchResult> { it.score }
                    .thenByDescending { it.updatedAt }
            )
            .take(topK.coerceAtLeast(1))
    }

    private fun loadChunk(filePath: String): RagChunk? {
        val mahaRootDir = ragStorageManager.getRagRootDir().parentFile ?: return null
        val chunkFile = File(mahaRootDir, filePath)
        if (!chunkFile.exists()) return null

        return runCatching {
            val json = JSONObject(chunkFile.readText())
            RagChunk(
                chunkId = json.optString("chunkId"),
                sourceType = json.optString("sourceType"),
                sourceId = json.optString("sourceId"),
                sessionId = json.optNullableString("sessionId"),
                workerId = json.optNullableString("workerId"),
                documentId = json.optNullableString("documentId"),
                title = json.optString("title"),
                text = json.optString("text"),
                textPreview = json.optString("textPreview"),
                tokenEstimate = json.optInt("tokenEstimate", 0),
                createdAt = json.optLong("createdAt", 0L),
                updatedAt = json.optLong("updatedAt", 0L),
                embeddingStatus = json.optEmbeddingStatus("embeddingStatus"),
                embeddingId = json.optNullableString("embeddingId"),
                metadata = json.optJSONObject("metadata").toStringMap()
            )
        }.getOrNull()
    }

    private fun calculateScore(
        tokens: List<String>,
        sourceTitle: String,
        chunkEntry: RagIndexChunkEntry,
        chunk: RagChunk
    ): Int {
        var score = 0
        val titleText = listOf(sourceTitle, chunk.title).joinToString(separator = " ")
        val previewText = listOf(chunkEntry.textPreview, chunk.textPreview).joinToString(separator = " ")
        val metadataText = chunk.metadata.values.joinToString(separator = " ")
        val allText = listOf(titleText, previewText, chunk.text, metadataText).joinToString(separator = " ")

        tokens.forEach { token ->
            if (titleText.containsToken(token)) score += 5
            if (previewText.containsToken(token)) score += 3
            if (chunk.text.containsToken(token)) score += 2
            if (chunk.sourceType.containsToken(token) || chunkEntry.sourceType.containsToken(token)) score += 1
            if (metadataText.containsToken(token)) score += 1
        }

        if (tokens.all { token -> allText.containsToken(token) }) {
            score += 4
        }

        return score
    }

    private fun buildMatchedSnippet(
        text: String,
        tokens: List<String>,
        fallback: String,
        radius: Int = 90
    ): String {
        val normalizedText = text.replace(Regex("\\s+"), " ").trim()
        if (normalizedText.isBlank()) return fallback

        val lowerText = normalizedText.lowercase(Locale.ROOT)
        val firstMatchIndex = tokens
            .mapNotNull { token -> lowerText.indexOf(token).takeIf { it >= 0 } }
            .minOrNull()
            ?: return fallback.ifBlank { normalizedText.take(MAX_SNIPPET_LENGTH) }

        val start = (firstMatchIndex - radius).coerceAtLeast(0)
        val end = (firstMatchIndex + radius).coerceAtMost(normalizedText.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < normalizedText.length) "…" else ""
        return (prefix + normalizedText.substring(start, end).trim() + suffix)
            .take(MAX_SNIPPET_LENGTH)
    }

    private fun String.toSearchTokens(): List<String> {
        return trim()
            .lowercase(Locale.ROOT)
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun String.matchesAnyToken(tokens: List<String>): Boolean {
        return tokens.any { token -> containsToken(token) }
    }

    private fun String.containsToken(token: String): Boolean {
        return lowercase(Locale.ROOT).contains(token)
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().ifBlank { null }
    }

    private fun JSONObject.optEmbeddingStatus(key: String): EmbeddingStatus {
        val rawValue = optString(key, EmbeddingStatus.NONE.name)
        return runCatching { EmbeddingStatus.valueOf(rawValue) }.getOrDefault(EmbeddingStatus.NONE)
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        val result = mutableMapOf<String, String>()
        keys().forEach { key ->
            result[key] = optString(key)
        }
        return result
    }

    companion object {
        private const val DEFAULT_TOP_K = 10
        private const val DEFAULT_MAX_LOADED_CHUNKS = 50
        private const val MAX_SNIPPET_LENGTH = 240
    }
}
