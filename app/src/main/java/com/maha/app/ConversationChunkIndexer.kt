package com.maha.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

class ConversationChunkIndexer(
    private val context: Context,
    private val ragStorageManager: RagStorageManager,
    private val ragIndexStore: RagIndexStore
) {
    fun indexConversationSession(
        sessionId: String,
        title: String
    ): ConversationChunkIndexResult {
        val sessionDir = findAppSpecificSessionDir(sessionId)
            ?: return ConversationChunkIndexResult(
                createdChunkCount = 0,
                processedMessageCount = 0,
                failedCount = 1,
                message = "세션 폴더를 찾을 수 없습니다."
            )

        val messagesFile = File(sessionDir, MESSAGES_FILE_NAME)
        if (!messagesFile.exists()) {
            removeExistingConversationChunks(sessionId)
            updateIndexMetadata(sessionId = sessionId, title = title, messagesFile = messagesFile, chunks = emptyList())
            return ConversationChunkIndexResult(
                createdChunkCount = 0,
                processedMessageCount = 0,
                failedCount = 0,
                message = "messages.jsonl 파일이 없어 기존 chunk만 정리했습니다."
            )
        }

        val messages = readConversationMessages(messagesFile)
        if (messages.isEmpty()) {
            removeExistingConversationChunks(sessionId)
            updateIndexMetadata(sessionId = sessionId, title = title, messagesFile = messagesFile, chunks = emptyList())
            return ConversationChunkIndexResult(
                createdChunkCount = 0,
                processedMessageCount = 0,
                failedCount = 0,
                message = "인덱싱할 메시지가 없어 기존 chunk만 정리했습니다."
            )
        }

        val now = System.currentTimeMillis()
        val chunks = buildConversationChunks(
            sessionId = sessionId,
            title = title,
            messages = messages,
            now = now
        )

        removeExistingConversationChunks(sessionId)
        val savedCount = saveChunks(sessionId = sessionId, chunks = chunks)
        val failedCount = chunks.size - savedCount

        updateIndexMetadata(
            sessionId = sessionId,
            title = title,
            messagesFile = messagesFile,
            chunks = chunks.take(savedCount)
        )

        return ConversationChunkIndexResult(
            createdChunkCount = savedCount,
            processedMessageCount = messages.size,
            failedCount = failedCount,
            message = "인덱싱 완료: ${savedCount} chunks / ${messages.size} messages"
        )
    }

    private fun findAppSpecificSessionDir(sessionId: String): File? {
        val rootDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "MAHA")
        val conversationsDir = File(rootDir, "conversations")
        if (!conversationsDir.exists()) return null

        return conversationsDir.listFiles()?.firstOrNull { folder ->
            if (!folder.isDirectory || !folder.name.startsWith("session_")) return@firstOrNull false
            val sessionJsonFile = File(folder, SESSION_FILE_NAME)
            if (!sessionJsonFile.exists()) return@firstOrNull false
            runCatching {
                JSONObject(sessionJsonFile.readText()).optString("sessionId") == sessionId
            }.getOrDefault(false)
        }
    }

    private fun readConversationMessages(messagesFile: File): List<ConversationChunkMessage> {
        return messagesFile.readLines()
            .mapNotNull { line ->
                val trimmedLine = line.trim()
                if (trimmedLine.isBlank()) return@mapNotNull null
                runCatching {
                    val json = JSONObject(trimmedLine)
                    val role = json.optString("role", "UNKNOWN")
                    val blocks = json.optJSONArray("blocks") ?: JSONArray()
                    val content = buildString {
                        for (index in 0 until blocks.length()) {
                            val block = blocks.optJSONObject(index) ?: continue
                            val blockContent = block.optString("content").trim()
                            if (blockContent.isNotBlank()) {
                                if (isNotEmpty()) append("\n\n")
                                append(blockContent)
                            }
                        }
                    }.trim()

                    if (content.isBlank()) null else ConversationChunkMessage(role = role, content = content)
                }.getOrNull()
            }
    }

    private fun buildConversationChunks(
        sessionId: String,
        title: String,
        messages: List<ConversationChunkMessage>,
        now: Long
    ): List<RagChunk> {
        val result = mutableListOf<RagChunk>()
        val buffer = mutableListOf<ConversationChunkMessage>()
        var bufferTokenEstimate = 0
        var messageStartIndex = 0

        fun flushBuffer(endIndexExclusive: Int) {
            if (buffer.isEmpty()) return
            val chunkNumber = result.size + 1
            val chunkText = buffer.joinToString(separator = "\n\n") { message ->
                "[${message.role.uppercase(Locale.ROOT)}]\n${message.content}"
            }
            val chunkId = "${safeId(sessionId)}_${chunkNumber.toString().padStart(3, '0')}"
            result.add(
                RagChunk(
                    chunkId = chunkId,
                    sourceType = SOURCE_TYPE_CONVERSATION,
                    sourceId = sessionId,
                    sessionId = sessionId,
                    workerId = null,
                    documentId = null,
                    title = title.ifBlank { "제목 없음" },
                    text = chunkText,
                    textPreview = chunkText.toPreview(),
                    tokenEstimate = estimateTokens(chunkText),
                    createdAt = now,
                    updatedAt = now,
                    embeddingStatus = EmbeddingStatus.NONE,
                    embeddingId = null,
                    metadata = mapOf(
                        "messageStartIndex" to messageStartIndex.toString(),
                        "messageEndIndex" to (endIndexExclusive - 1).coerceAtLeast(messageStartIndex).toString(),
                        "messageCount" to buffer.size.toString()
                    )
                )
            )
            buffer.clear()
            bufferTokenEstimate = 0
            messageStartIndex = endIndexExclusive
        }

        messages.forEachIndexed { index, message ->
            val messageTokenEstimate = estimateTokens(message.content)
            val wouldExceedTokenLimit = buffer.isNotEmpty() && bufferTokenEstimate + messageTokenEstimate > TARGET_TOKEN_ESTIMATE
            val wouldExceedMessageLimit = buffer.size >= MAX_MESSAGES_PER_CHUNK

            if (wouldExceedTokenLimit || wouldExceedMessageLimit) {
                flushBuffer(index)
            }

            buffer.add(message)
            bufferTokenEstimate += messageTokenEstimate

            val isGoodBoundary = buffer.size >= MIN_MESSAGES_PER_CHUNK &&
                    bufferTokenEstimate >= MIN_TOKEN_ESTIMATE &&
                    message.role.equals("ASSISTANT", ignoreCase = true)
            if (isGoodBoundary) {
                flushBuffer(index + 1)
            }
        }

        flushBuffer(messages.size)
        return result
    }

    private fun saveChunks(sessionId: String, chunks: List<RagChunk>): Int {
        val chunkDir = getConversationChunkDir(sessionId)
        chunkDir.mkdirs()

        var savedCount = 0
        chunks.forEach { chunk ->
            val chunkFile = File(chunkDir, chunkFileNameFromChunkId(chunk.chunkId))
            val saved = runCatching {
                chunkFile.writeText(chunk.toJsonObject().toString(2))
            }.isSuccess
            if (saved) savedCount += 1
        }
        return savedCount
    }

    private fun removeExistingConversationChunks(sessionId: String) {
        val newRuleChunkDir = getConversationChunkDir(sessionId)
        if (newRuleChunkDir.exists()) {
            newRuleChunkDir.deleteRecursively()
        }

        val legacyChunkDir = File(ragStorageManager.getChunksDir(), "conversation_${safeId(sessionId)}")
        if (legacyChunkDir.exists() && legacyChunkDir.absolutePath != newRuleChunkDir.absolutePath) {
            legacyChunkDir.deleteRecursively()
        }
    }

    private fun updateIndexMetadata(
        sessionId: String,
        title: String,
        messagesFile: File,
        chunks: List<RagChunk>
    ) {
        val current = ragIndexStore.ensureIndexMetadata()
        val now = System.currentTimeMillis()

        val filteredSources = current.sources.filterNot { source ->
            source.sourceType == SOURCE_TYPE_CONVERSATION && source.sourceId == sessionId
        }
        val filteredChunks = current.chunks.filterNot { chunk ->
            chunk.sourceType == SOURCE_TYPE_CONVERSATION && chunk.sourceId == sessionId
        }

        val newSourceEntries = if (chunks.isEmpty()) {
            emptyList()
        } else {
            listOf(
                RagIndexSourceEntry(
                    sourceType = SOURCE_TYPE_CONVERSATION,
                    sourceId = sessionId,
                    title = title.ifBlank { "제목 없음" },
                    filePath = messagesFile.relativeToMahaRootPath(),
                    chunkCount = chunks.size,
                    indexedAt = now,
                    status = "INDEXED"
                )
            )
        }

        val newChunkEntries = chunks.map { chunk ->
            RagIndexChunkEntry(
                chunkId = chunk.chunkId,
                sourceType = chunk.sourceType,
                sourceId = chunk.sourceId,
                filePath = File(getConversationChunkDir(sessionId), chunkFileNameFromChunkId(chunk.chunkId)).relativeToMahaRootPath(),
                textPreview = chunk.textPreview,
                embeddingStatus = EmbeddingStatus.NONE,
                updatedAt = now
            )
        }

        val updatedSources = filteredSources + newSourceEntries
        val updatedChunks = filteredChunks + newChunkEntries

        ragIndexStore.saveIndexMetadata(
            current.copy(
                updatedAt = now,
                status = if (updatedChunks.isEmpty()) "EMPTY" else "READY",
                chunkCount = updatedChunks.size,
                sourceCount = updatedSources.size,
                sources = updatedSources,
                chunks = updatedChunks
            )
        )
    }

    private fun getConversationChunkDir(sessionId: String): File {
        return File(ragStorageManager.getChunksDir(), safeId(sessionId))
    }

    private fun chunkFileNameFromChunkId(chunkId: String): String {
        val chunkNumber = chunkId.substringAfterLast("_", missingDelimiterValue = "001")
        return "chunk_$chunkNumber.json"
    }

    private fun RagChunk.toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("chunkId", chunkId)
            put("sourceType", sourceType)
            put("sourceId", sourceId)
            put("sessionId", sessionId ?: JSONObject.NULL)
            put("workerId", workerId ?: JSONObject.NULL)
            put("documentId", documentId ?: JSONObject.NULL)
            put("title", title)
            put("text", text)
            put("textPreview", textPreview)
            put("tokenEstimate", tokenEstimate)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
            put("embeddingStatus", embeddingStatus.name)
            put("embeddingId", embeddingId ?: JSONObject.NULL)
            put("metadata", JSONObject().apply {
                metadata.forEach { (key, value) -> put(key, value) }
            })
        }
    }

    private fun File.relativeToMahaRootPath(): String {
        val mahaRoot = File(context.getExternalFilesDir(null) ?: context.filesDir, "MAHA")
        return runCatching { relativeTo(mahaRoot).path }.getOrElse { path }
    }

    private fun String.toPreview(maxLength: Int = 180): String {
        return replace(Regex("\\s+"), " ").trim().let { normalized ->
            if (normalized.length <= maxLength) normalized else normalized.take(maxLength).trimEnd() + "…"
        }
    }

    private fun estimateTokens(text: String): Int {
        return (text.length / 4).coerceAtLeast(1)
    }

    private fun safeId(value: String): String {
        return value.replace(Regex("[^A-Za-z0-9_-]"), "_")
    }

    private data class ConversationChunkMessage(
        val role: String,
        val content: String
    )

    companion object {
        private const val SESSION_FILE_NAME = "session.json"
        private const val MESSAGES_FILE_NAME = "messages.jsonl"
        private const val SOURCE_TYPE_CONVERSATION = "conversation"
        private const val MIN_MESSAGES_PER_CHUNK = 3
        private const val MAX_MESSAGES_PER_CHUNK = 10
        private const val MIN_TOKEN_ESTIMATE = 500
        private const val TARGET_TOKEN_ESTIMATE = 800
    }
}

data class ConversationChunkIndexResult(
    val createdChunkCount: Int,
    val processedMessageCount: Int,
    val failedCount: Int,
    val message: String
)
