package com.maha.app

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ConversationFileStore(
    private val context: Context,
    private val storageManager: MahaStorageManager
) {
    fun saveSession(session: ConversationSession) {
        storageManager.ensureDirectories()

        if (storageManager.isSafReady()) {
            saveSessionToSaf(session)
        } else {
            saveSessionToFile(session)
        }
    }

    fun appendMessage(sessionId: String, message: ConversationMessage) {
        storageManager.ensureDirectories()

        if (storageManager.isSafReady()) {
            appendMessageToSaf(sessionId, message)
        } else {
            appendMessageToFile(sessionId, message)
        }
    }

    fun loadSessions(): List<ConversationSession> {
        storageManager.ensureDirectories()

        return if (storageManager.isSafReady()) {
            loadSessionsFromSaf()
        } else {
            loadSessionsFromFile()
        }
    }

    private fun saveSessionToFile(session: ConversationSession) {
        val sessionDir = getFileSessionDir(session.sessionId)
        sessionDir.mkdirs()

        File(sessionDir, SESSION_FILE_NAME).writeText(sessionToJson(session).toString(2))
    }

    private fun appendMessageToFile(sessionId: String, message: ConversationMessage) {
        val sessionDir = getFileSessionDir(sessionId)
        sessionDir.mkdirs()

        File(sessionDir, MESSAGES_FILE_NAME).appendText(messageToJson(message).toString() + "\n")
    }

    private fun loadSessionsFromFile(): List<ConversationSession> {
        val conversationsDir = storageManager.getConversationsDir()
        if (!conversationsDir.exists()) return emptyList()

        return conversationsDir
            .listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(SESSION_DIR_PREFIX) }
            ?.mapNotNull { sessionDir -> loadFileSession(sessionDir) }
            ?.distinctBy { it.sessionId }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }

    private fun loadFileSession(sessionDir: File): ConversationSession? {
        val sessionFile = File(sessionDir, SESSION_FILE_NAME)
        if (!sessionFile.exists()) return null

        return runCatching {
            val json = JSONObject(sessionFile.readText())
            val sessionId = json.optString("sessionId")
            if (sessionId.isBlank()) return null

            jsonToSession(
                json = json,
                messages = loadFileMessages(sessionId)
            )
        }.getOrNull()
    }

    private fun loadFileMessages(sessionId: String): List<ConversationMessage> {
        val messageFile = File(getFileSessionDir(sessionId), MESSAGES_FILE_NAME)
        if (!messageFile.exists()) return emptyList()

        return messageFile.readLines().mapNotNull { line ->
            line.trim().takeIf { it.isNotBlank() }?.let { trimmedLine ->
                runCatching { jsonToMessage(JSONObject(trimmedLine)) }.getOrNull()
            }
        }
    }

    private fun saveSessionToSaf(session: ConversationSession) {
        val sessionDir = getSafSessionDir(session.sessionId) ?: return
        val sessionFile = getOrCreateExactFile(sessionDir, SESSION_FILE_NAME) ?: return
        writeDocumentText(sessionFile, sessionToJson(session).toString(2))
    }

    private fun appendMessageToSaf(sessionId: String, message: ConversationMessage) {
        val sessionDir = getSafSessionDir(sessionId) ?: return
        val messageFile = getOrCreateExactFile(sessionDir, MESSAGES_FILE_NAME) ?: return

        val currentText = readDocumentText(messageFile).trimEnd()
        val newLine = messageToJson(message).toString()
        val updatedText = if (currentText.isBlank()) {
            "$newLine\n"
        } else {
            "$currentText\n$newLine\n"
        }

        writeDocumentText(messageFile, updatedText)
    }

    private fun loadSessionsFromSaf(): List<ConversationSession> {
        val conversationsDir = storageManager.getSafConversationsDocument() ?: return emptyList()

        return conversationsDir.listFiles()
            .filter { it.isDirectory && it.name?.startsWith(SESSION_DIR_PREFIX) == true }
            .mapNotNull { sessionDir -> loadSafSession(sessionDir) }
            .distinctBy { it.sessionId }
            .sortedByDescending { it.updatedAt }
    }

    private fun loadSafSession(sessionDir: DocumentFile): ConversationSession? {
        val sessionFile = findExactFile(sessionDir, SESSION_FILE_NAME) ?: return null

        return runCatching {
            val jsonText = readDocumentText(sessionFile)
            val json = JSONObject(jsonText)
            val sessionId = json.optString("sessionId")
            if (sessionId.isBlank()) return null

            jsonToSession(
                json = json,
                messages = loadSafMessages(sessionDir)
            )
        }.getOrNull()
    }

    private fun loadSafMessages(sessionDir: DocumentFile): List<ConversationMessage> {
        val messageFile = findExactFile(sessionDir, MESSAGES_FILE_NAME) ?: return emptyList()

        return readDocumentText(messageFile)
            .lineSequence()
            .mapNotNull { line ->
                line.trim().takeIf { it.isNotBlank() }?.let { trimmedLine ->
                    runCatching { jsonToMessage(JSONObject(trimmedLine)) }.getOrNull()
                }
            }
            .toList()
    }

    private fun sessionToJson(session: ConversationSession): JSONObject {
        return JSONObject().apply {
            put("sessionId", session.sessionId)
            put("title", session.title)
            put("createdAt", session.updatedAt)
            put("updatedAt", session.updatedAt)
            put("messageCount", session.messages.size)
            put("lastMessageSummary", session.lastMessageSummary)
            put("memorySummary", session.memorySummary)
        }
    }

    private fun jsonToSession(
        json: JSONObject,
        messages: List<ConversationMessage>
    ): ConversationSession {
        return ConversationSession(
            sessionId = json.optString("sessionId"),
            title = json.optString("title", "새 대화"),
            lastMessageSummary = json.optString("lastMessageSummary", "아직 메시지가 없습니다."),
            updatedAt = json.optString("updatedAt", ""),
            messages = messages,
            memorySummary = json.optString("memorySummary", "")
        )
    }

    private fun messageToJson(message: ConversationMessage): JSONObject {
        return JSONObject().apply {
            put("messageId", message.messageId)
            put("sessionId", message.sessionId)
            put("role", message.role.name)
            put("createdAt", message.createdAt)
            put("linkedRunId", message.linkedRunId)
            put(
                "blocks",
                JSONArray().apply {
                    message.blocks.forEach { block ->
                        put(
                            JSONObject().apply {
                                put("blockId", block.blockId)
                                put("type", block.type.name)
                                put("title", block.title)
                                put("content", block.content)
                                put("collapsed", block.collapsed)
                            }
                        )
                    }
                }
            )
        }
    }

    private fun jsonToMessage(json: JSONObject): ConversationMessage {
        val blocksJson = json.optJSONArray("blocks") ?: JSONArray()
        val blocks = mutableListOf<ConversationOutputBlock>()

        for (index in 0 until blocksJson.length()) {
            val blockJson = blocksJson.optJSONObject(index) ?: continue
            blocks.add(
                ConversationOutputBlock(
                    blockId = blockJson.optString("blockId"),
                    type = runCatching {
                        ConversationOutputBlockType.valueOf(blockJson.optString("type"))
                    }.getOrDefault(ConversationOutputBlockType.TEXT_BLOCK),
                    title = blockJson.optString("title"),
                    content = blockJson.optString("content"),
                    collapsed = blockJson.optBoolean("collapsed", false)
                )
            )
        }

        return ConversationMessage(
            messageId = json.optString("messageId"),
            sessionId = json.optString("sessionId"),
            role = runCatching {
                ConversationRole.valueOf(json.optString("role"))
            }.getOrDefault(ConversationRole.USER),
            createdAt = json.optString("createdAt"),
            blocks = blocks,
            linkedRunId = json.optString("linkedRunId")
        )
    }

    private fun getFileSessionDir(sessionId: String): File {
        return File(storageManager.getConversationsDir(), "$SESSION_DIR_PREFIX$sessionId")
    }

    private fun getSafSessionDir(sessionId: String): DocumentFile? {
        val conversationsDir = storageManager.getSafConversationsDocument() ?: return null
        val directoryName = "$SESSION_DIR_PREFIX$sessionId"

        return conversationsDir.listFiles()
            .firstOrNull { it.isDirectory && it.name == directoryName }
            ?: conversationsDir.createDirectory(directoryName)
    }

    private fun getOrCreateExactFile(directory: DocumentFile, fileName: String): DocumentFile? {
        val existingFile = findExactFile(directory, fileName)
        if (existingFile != null) return existingFile

        val createdFile = directory.createFile(SAF_PLAIN_FILE_MIME_TYPE, fileName) ?: return null
        if (createdFile.name == fileName) return createdFile

        createdFile.renameTo(fileName)
        return findExactFile(directory, fileName) ?: createdFile
    }

    private fun findExactFile(directory: DocumentFile, fileName: String): DocumentFile? {
        return directory.listFiles()
            .firstOrNull { !it.isDirectory && it.name == fileName }
    }

    private fun writeDocumentText(
        documentFile: DocumentFile,
        text: String
    ) {
        context.contentResolver.openOutputStream(documentFile.uri, "rwt")?.use { outputStream ->
            outputStream.write(text.toByteArray(Charsets.UTF_8))
        }
    }

    private fun readDocumentText(documentFile: DocumentFile): String {
        return context.contentResolver.openInputStream(documentFile.uri)?.use { inputStream ->
            inputStream.bufferedReader(Charsets.UTF_8).readText()
        }.orEmpty()
    }

    companion object {
        private const val SESSION_DIR_PREFIX = "session_"
        private const val SESSION_FILE_NAME = "session.json"
        private const val MESSAGES_FILE_NAME = "messages.jsonl"
        private const val SAF_PLAIN_FILE_MIME_TYPE = "application/octet-stream"
    }
}
