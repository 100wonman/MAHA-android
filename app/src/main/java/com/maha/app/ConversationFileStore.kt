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
        saveSessionInternal(
            session = session,
            isFavorite = readSessionFavorite(session.sessionId)
        )
    }

    fun updateSession(
        session: ConversationSession,
        isFavorite: Boolean? = null
    ) {
        saveSessionInternal(
            session = session,
            isFavorite = isFavorite ?: readSessionFavorite(session.sessionId)
        )
    }

    fun isSessionFavorite(sessionId: String): Boolean {
        return readSessionFavorite(sessionId)
    }

    fun deleteSession(sessionId: String): Boolean {
        storageManager.ensureDirectories()

        return if (storageManager.isSafReady()) {
            deleteSessionFromSaf(sessionId)
        } else {
            deleteSessionFromFile(sessionId)
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

    fun getAppSpecificSessionCount(): Int {
        val conversationsDir = storageManager.getConversationsDir()
        if (!conversationsDir.exists()) return 0

        return conversationsDir
            .listFiles()
            ?.count { sessionDir ->
                sessionDir.isDirectory &&
                        sessionDir.name.startsWith(SESSION_DIR_PREFIX) &&
                        File(sessionDir, SESSION_FILE_NAME).exists()
            }
            ?: 0
    }

    fun migrateAppSpecificSessionsToSaf(): ConversationMigrationResult {
        if (!storageManager.isSafReady()) {
            return ConversationMigrationResult(
                copiedCount = 0,
                skippedCount = 0,
                failedCount = 1
            )
        }

        val appSpecificConversationsDir = storageManager.getConversationsDir()
        if (!appSpecificConversationsDir.exists()) {
            return ConversationMigrationResult(
                copiedCount = 0,
                skippedCount = 0,
                failedCount = 0
            )
        }

        val safConversationsDir = storageManager.getSafConversationsDocument()
            ?: return ConversationMigrationResult(
                copiedCount = 0,
                skippedCount = 0,
                failedCount = 1
            )

        var copiedCount = 0
        var skippedCount = 0
        var failedCount = 0

        appSpecificConversationsDir
            .listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(SESSION_DIR_PREFIX) }
            ?.forEach { sourceSessionDir ->
                val sessionFile = File(sourceSessionDir, SESSION_FILE_NAME)
                if (!sessionFile.exists()) {
                    failedCount += 1
                    return@forEach
                }

                val sessionId = readSessionIdFromFile(sessionFile)
                    ?: sourceSessionDir.name.removePrefix(SESSION_DIR_PREFIX)

                if (sessionId.isBlank()) {
                    failedCount += 1
                    return@forEach
                }

                val sessionTitle = runCatching {
                    JSONObject(sessionFile.readText()).optString("title", "새 대화")
                }.getOrDefault("새 대화")

                val existingTargetDir = findSafSessionDir(sessionId)
                if (existingTargetDir != null) {
                    skippedCount += 1
                    return@forEach
                }

                val targetDirName = buildSessionDirectoryName(sessionTitle, sessionId)
                val targetSessionDir = safConversationsDir.createDirectory(targetDirName)
                if (targetSessionDir == null) {
                    failedCount += 1
                    return@forEach
                }

                val sessionCopied = copyFileToSafDirectory(
                    sourceFile = sessionFile,
                    targetDirectory = targetSessionDir,
                    targetFileName = SESSION_FILE_NAME
                )

                val messagesFile = File(sourceSessionDir, MESSAGES_FILE_NAME)
                val messagesCopied = if (messagesFile.exists()) {
                    copyFileToSafDirectory(
                        sourceFile = messagesFile,
                        targetDirectory = targetSessionDir,
                        targetFileName = MESSAGES_FILE_NAME
                    )
                } else {
                    true
                }

                if (sessionCopied && messagesCopied) {
                    copiedCount += 1
                } else {
                    failedCount += 1
                }
            }

        return ConversationMigrationResult(
            copiedCount = copiedCount,
            skippedCount = skippedCount,
            failedCount = failedCount
        )
    }

    private fun saveSessionInternal(
        session: ConversationSession,
        isFavorite: Boolean
    ) {
        storageManager.ensureDirectories()

        if (storageManager.isSafReady()) {
            saveSessionToSaf(session, isFavorite)
        } else {
            saveSessionToFile(session, isFavorite)
        }
    }

    private fun saveSessionToFile(
        session: ConversationSession,
        isFavorite: Boolean
    ) {
        val sessionDir = getReadableFileSessionDir(session)
        sessionDir.mkdirs()

        File(sessionDir, SESSION_FILE_NAME).writeText(
            sessionToJson(session, isFavorite).toString(2)
        )
    }

    private fun appendMessageToFile(sessionId: String, message: ConversationMessage) {
        val sessionDir = findFileSessionDir(sessionId) ?: getLegacyFileSessionDir(sessionId)
        sessionDir.mkdirs()

        File(sessionDir, MESSAGES_FILE_NAME).appendText(messageToJson(message).toString() + "\n")
    }

    private fun deleteSessionFromFile(sessionId: String): Boolean {
        val sessionDirs = findFileSessionDirs(sessionId)
        if (sessionDirs.isEmpty()) return true

        return sessionDirs.all { sessionDir ->
            if (sessionDir.exists()) {
                sessionDir.deleteRecursively()
            } else {
                true
            }
        }
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

            val session = jsonToSession(
                json = json,
                messages = loadFileMessages(sessionDir)
            )

            ensureReadableFileSessionDir(session, sessionDir)
            session
        }.getOrNull()
    }

    private fun loadFileMessages(sessionDir: File): List<ConversationMessage> {
        val messageFile = File(sessionDir, MESSAGES_FILE_NAME)
        if (!messageFile.exists()) return emptyList()

        return messageFile.readLines().mapNotNull { line ->
            line.trim().takeIf { it.isNotBlank() }?.let { trimmedLine ->
                runCatching { jsonToMessage(JSONObject(trimmedLine)) }.getOrNull()
            }
        }
    }

    private fun saveSessionToSaf(
        session: ConversationSession,
        isFavorite: Boolean
    ) {
        val sessionDir = getReadableSafSessionDir(session) ?: return
        val sessionFile = getOrCreateExactFile(sessionDir, SESSION_FILE_NAME) ?: return
        writeDocumentText(sessionFile, sessionToJson(session, isFavorite).toString(2))
    }

    private fun appendMessageToSaf(sessionId: String, message: ConversationMessage) {
        val sessionDir = findSafSessionDir(sessionId) ?: getLegacySafSessionDir(sessionId) ?: return
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

    private fun deleteSessionFromSaf(sessionId: String): Boolean {
        val sessionDirs = findSafSessionDirs(sessionId)
        if (sessionDirs.isEmpty()) return true

        sessionDirs.forEach { sessionDir ->
            deleteDocumentTree(sessionDir)
        }
        return true
    }

    private fun deleteDocumentTree(documentFile: DocumentFile) {
        if (documentFile.isDirectory) {
            documentFile.listFiles().forEach { child ->
                deleteDocumentTree(child)
            }
        }
        documentFile.delete()
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

            val session = jsonToSession(
                json = json,
                messages = loadSafMessages(sessionDir)
            )

            ensureReadableSafSessionDir(session, sessionDir)
            session
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

    private fun sessionToJson(
        session: ConversationSession,
        isFavorite: Boolean
    ): JSONObject {
        return JSONObject().apply {
            put("sessionId", session.sessionId)
            put("title", session.title)
            put("createdAt", readSessionCreatedAt(session.sessionId) ?: session.updatedAt)
            put("updatedAt", session.updatedAt)
            put("messageCount", session.messages.size)
            put("lastMessageSummary", session.lastMessageSummary)
            put("memorySummary", session.memorySummary)
            put("isFavorite", isFavorite)
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

    private fun readSessionFavorite(sessionId: String): Boolean {
        return readSessionJson(sessionId)?.optBoolean("isFavorite", false) ?: false
    }

    private fun readSessionCreatedAt(sessionId: String): String? {
        return readSessionJson(sessionId)?.optString("createdAt")?.takeIf { it.isNotBlank() }
    }

    private fun readSessionJson(sessionId: String): JSONObject? {
        return if (storageManager.isSafReady()) {
            val sessionDir = findSafSessionDir(sessionId) ?: return null
            val sessionFile = findExactFile(sessionDir, SESSION_FILE_NAME) ?: return null
            runCatching { JSONObject(readDocumentText(sessionFile)) }.getOrNull()
        } else {
            val sessionDir = findFileSessionDir(sessionId) ?: return null
            val sessionFile = File(sessionDir, SESSION_FILE_NAME)
            if (!sessionFile.exists()) return null
            runCatching { JSONObject(sessionFile.readText()) }.getOrNull()
        }
    }

    private fun readSessionIdFromFile(sessionFile: File): String? {
        return runCatching {
            JSONObject(sessionFile.readText()).optString("sessionId").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun copyFileToSafDirectory(
        sourceFile: File,
        targetDirectory: DocumentFile,
        targetFileName: String
    ): Boolean {
        return runCatching {
            val targetFile = getOrCreateExactFile(targetDirectory, targetFileName) ?: return false
            writeDocumentText(targetFile, sourceFile.readText())
            true
        }.getOrDefault(false)
    }

    private fun ensureReadableFileSessionDir(
        session: ConversationSession,
        sourceSessionDir: File
    ) {
        val targetSessionDir = getReadableFileSessionDir(session)
        if (sourceSessionDir.canonicalPath == targetSessionDir.canonicalPath) return

        targetSessionDir.mkdirs()

        val sourceSessionFile = File(sourceSessionDir, SESSION_FILE_NAME)
        val targetSessionFile = File(targetSessionDir, SESSION_FILE_NAME)
        if (sourceSessionFile.exists()) {
            runCatching { sourceSessionFile.copyTo(targetSessionFile, overwrite = true) }
        }

        val sourceMessagesFile = File(sourceSessionDir, MESSAGES_FILE_NAME)
        val targetMessagesFile = File(targetSessionDir, MESSAGES_FILE_NAME)
        if (sourceMessagesFile.exists() && !targetMessagesFile.exists()) {
            runCatching { sourceMessagesFile.copyTo(targetMessagesFile, overwrite = false) }
        }
    }

    private fun ensureReadableSafSessionDir(
        session: ConversationSession,
        sourceSessionDir: DocumentFile
    ) {
        val targetSessionDir = getReadableSafSessionDir(session) ?: return
        if (sourceSessionDir.uri == targetSessionDir.uri) return

        val sourceSessionFile = findExactFile(sourceSessionDir, SESSION_FILE_NAME)
        if (sourceSessionFile != null) {
            val targetSessionFile = getOrCreateExactFile(targetSessionDir, SESSION_FILE_NAME)
            if (targetSessionFile != null) {
                writeDocumentText(targetSessionFile, readDocumentText(sourceSessionFile))
            }
        }

        val sourceMessagesFile = findExactFile(sourceSessionDir, MESSAGES_FILE_NAME)
        val targetMessagesFile = findExactFile(targetSessionDir, MESSAGES_FILE_NAME)
        if (sourceMessagesFile != null && targetMessagesFile == null) {
            val createdMessagesFile = getOrCreateExactFile(targetSessionDir, MESSAGES_FILE_NAME)
            if (createdMessagesFile != null) {
                writeDocumentText(createdMessagesFile, readDocumentText(sourceMessagesFile))
            }
        }
    }

    private fun getReadableFileSessionDir(session: ConversationSession): File {
        return File(storageManager.getConversationsDir(), buildSessionDirectoryName(session.title, session.sessionId))
    }

    private fun getLegacyFileSessionDir(sessionId: String): File {
        return File(storageManager.getConversationsDir(), "$SESSION_DIR_PREFIX$sessionId")
    }

    private fun findFileSessionDir(sessionId: String): File? {
        return findFileSessionDirs(sessionId).firstOrNull()
    }

    private fun findFileSessionDirs(sessionId: String): List<File> {
        val conversationsDir = storageManager.getConversationsDir()
        if (!conversationsDir.exists()) return emptyList()

        return conversationsDir
            .listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(SESSION_DIR_PREFIX) }
            ?.filter { sessionDir ->
                val sessionFile = File(sessionDir, SESSION_FILE_NAME)
                if (sessionFile.exists()) {
                    readSessionIdFromFile(sessionFile) == sessionId
                } else {
                    sessionDir.name == "$SESSION_DIR_PREFIX$sessionId" ||
                            sessionDir.name.endsWith("_${shortSessionId(sessionId)}")
                }
            }
            ?: emptyList()
    }

    private fun getReadableSafSessionDir(session: ConversationSession): DocumentFile? {
        val conversationsDir = storageManager.getSafConversationsDocument() ?: return null
        val directoryName = buildSessionDirectoryName(session.title, session.sessionId)

        return conversationsDir.listFiles()
            .firstOrNull { it.isDirectory && it.name == directoryName }
            ?: conversationsDir.createDirectory(directoryName)
    }

    private fun getLegacySafSessionDir(sessionId: String): DocumentFile? {
        val conversationsDir = storageManager.getSafConversationsDocument() ?: return null
        val directoryName = "$SESSION_DIR_PREFIX$sessionId"

        return conversationsDir.listFiles()
            .firstOrNull { it.isDirectory && it.name == directoryName }
            ?: conversationsDir.createDirectory(directoryName)
    }

    private fun findSafSessionDir(sessionId: String): DocumentFile? {
        return findSafSessionDirs(sessionId).firstOrNull()
    }

    private fun findSafSessionDirs(sessionId: String): List<DocumentFile> {
        val conversationsDir = storageManager.getSafConversationsDocument() ?: return emptyList()

        return conversationsDir.listFiles()
            .filter { it.isDirectory && it.name?.startsWith(SESSION_DIR_PREFIX) == true }
            .filter { sessionDir ->
                val sessionFile = findExactFile(sessionDir, SESSION_FILE_NAME)
                if (sessionFile != null) {
                    runCatching { JSONObject(readDocumentText(sessionFile)).optString("sessionId") }.getOrNull() == sessionId
                } else {
                    val directoryName = sessionDir.name.orEmpty()
                    directoryName == "$SESSION_DIR_PREFIX$sessionId" ||
                            directoryName.endsWith("_${shortSessionId(sessionId)}")
                }
            }
    }

    private fun buildSessionDirectoryName(title: String, sessionId: String): String {
        return "$SESSION_DIR_PREFIX${safeTitle(title)}_${shortSessionId(sessionId)}"
    }

    private fun safeTitle(title: String): String {
        val cleanedTitle = title
            .trim()
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^0-9A-Za-z가-힣ㄱ-ㅎㅏ-ㅣ_\\-]"), "")
            .trim('_', '-')

        return cleanedTitle
            .take(MAX_SAFE_TITLE_LENGTH)
            .ifBlank { "untitled" }
    }

    private fun shortSessionId(sessionId: String): String {
        return sessionId
            .filter { it.isLetterOrDigit() }
            .take(SHORT_ID_LENGTH)
            .ifBlank { sessionId.take(SHORT_ID_LENGTH).ifBlank { "unknown" } }
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
        private const val MAX_SAFE_TITLE_LENGTH = 20
        private const val SHORT_ID_LENGTH = 6
    }
}


data class ConversationMigrationResult(
    val copiedCount: Int,
    val skippedCount: Int,
    val failedCount: Int
)
