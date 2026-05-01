package com.maha.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class RagIndexStore(
    private val ragStorageManager: RagStorageManager
) {
    fun loadIndexMetadata(): RagIndexMetadata {
        val metadataFile = ragStorageManager.getIndexMetadataFile()
        if (!metadataFile.exists()) {
            return createEmptyMetadata()
        }

        return runCatching {
            parseMetadata(metadataFile.readText())
        }.getOrElse {
            createEmptyMetadata()
        }
    }

    fun saveIndexMetadata(metadata: RagIndexMetadata) {
        val metadataFile = ragStorageManager.getIndexMetadataFile()
        metadataFile.parentFile?.mkdirs()
        metadataFile.writeText(metadata.toJsonObject().toString(2))
    }

    fun ensureIndexMetadata(): RagIndexMetadata {
        ragStorageManager.ensureRagDirectories()

        val metadataFile = ragStorageManager.getIndexMetadataFile()
        if (metadataFile.exists()) {
            return loadIndexMetadata()
        }

        val emptyMetadata = createEmptyMetadata()
        saveIndexMetadata(emptyMetadata)
        return emptyMetadata
    }

    fun createEmptyMetadata(): RagIndexMetadata {
        val now = System.currentTimeMillis()
        return RagIndexMetadata(
            indexVersion = 1,
            createdAt = now,
            updatedAt = now,
            status = "EMPTY",
            chunkCount = 0,
            sourceCount = 0,
            embeddingModel = null,
            embeddingEnabled = false,
            sources = emptyList(),
            chunks = emptyList()
        )
    }

    private fun parseMetadata(rawJson: String): RagIndexMetadata {
        val json = JSONObject(rawJson)
        val sources = json.optJSONArray("sources").toSourceEntries()
        val chunks = json.optJSONArray("chunks").toChunkEntries()

        return RagIndexMetadata(
            indexVersion = json.optInt("indexVersion", 1),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
            status = json.optString("status", if (chunks.isEmpty()) "EMPTY" else "READY"),
            chunkCount = json.optInt("chunkCount", chunks.size),
            sourceCount = json.optInt("sourceCount", sources.size),
            embeddingModel = json.optNullableString("embeddingModel"),
            embeddingEnabled = json.optBoolean("embeddingEnabled", false),
            sources = sources,
            chunks = chunks
        )
    }

    private fun JSONArray?.toSourceEntries(): List<RagIndexSourceEntry> {
        if (this == null) return emptyList()

        val result = mutableListOf<RagIndexSourceEntry>()
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            result.add(
                RagIndexSourceEntry(
                    sourceType = item.optString("sourceType"),
                    sourceId = item.optString("sourceId"),
                    title = item.optString("title"),
                    filePath = item.optNullableString("filePath"),
                    chunkCount = item.optInt("chunkCount", 0),
                    indexedAt = item.optLong("indexedAt", 0L),
                    status = item.optString("status", "EMPTY")
                )
            )
        }
        return result
    }

    private fun JSONArray?.toChunkEntries(): List<RagIndexChunkEntry> {
        if (this == null) return emptyList()

        val result = mutableListOf<RagIndexChunkEntry>()
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            result.add(
                RagIndexChunkEntry(
                    chunkId = item.optString("chunkId"),
                    sourceType = item.optString("sourceType"),
                    sourceId = item.optString("sourceId"),
                    filePath = item.optString("filePath"),
                    textPreview = item.optString("textPreview"),
                    embeddingStatus = item.optEmbeddingStatus("embeddingStatus"),
                    updatedAt = item.optLong("updatedAt", 0L)
                )
            )
        }
        return result
    }

    private fun RagIndexMetadata.toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("indexVersion", indexVersion)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
            put("status", status)
            put("chunkCount", chunkCount)
            put("sourceCount", sourceCount)
            put("embeddingModel", embeddingModel ?: JSONObject.NULL)
            put("embeddingEnabled", embeddingEnabled)
            put("sources", JSONArray().apply {
                sources.forEach { source ->
                    put(source.toJsonObject())
                }
            })
            put("chunks", JSONArray().apply {
                chunks.forEach { chunk ->
                    put(chunk.toJsonObject())
                }
            })
        }
    }

    private fun RagIndexSourceEntry.toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("sourceType", sourceType)
            put("sourceId", sourceId)
            put("title", title)
            put("filePath", filePath ?: JSONObject.NULL)
            put("chunkCount", chunkCount)
            put("indexedAt", indexedAt)
            put("status", status)
        }
    }

    private fun RagIndexChunkEntry.toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("chunkId", chunkId)
            put("sourceType", sourceType)
            put("sourceId", sourceId)
            put("filePath", filePath)
            put("textPreview", textPreview)
            put("embeddingStatus", embeddingStatus.name)
            put("updatedAt", updatedAt)
        }
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = optString(key).trim()
        return value.ifBlank { null }
    }

    private fun JSONObject.optEmbeddingStatus(key: String): EmbeddingStatus {
        val rawValue = optString(key, EmbeddingStatus.NONE.name)
        return runCatching {
            EmbeddingStatus.valueOf(rawValue)
        }.getOrDefault(EmbeddingStatus.NONE)
    }
}
