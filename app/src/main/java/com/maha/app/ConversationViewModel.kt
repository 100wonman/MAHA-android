package com.maha.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class ConversationViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val storageManager = MahaStorageManager(application.applicationContext)
    private val ragStorageManager = RagStorageManager(application.applicationContext)
    private val ragIndexStore = RagIndexStore(ragStorageManager)
    private val ragKeywordSearchEngine = RagKeywordSearchEngine(
        ragStorageManager = ragStorageManager,
        ragIndexStore = ragIndexStore
    )
    private val ragContextBuilder = RagContextBuilder(ragKeywordSearchEngine)
    private val conversationEngine = ConversationEngine(
        apiKeyProvider = { providerName ->
            resolveProviderApiKey(providerName)
        }
    )
    private val conversationFileStore = ConversationFileStore(
        context = application.applicationContext,
        storageManager = storageManager
    )
    private val providerSettingsStore = ProviderSettingsStore(application.applicationContext)

    val conversationSessions = mutableStateListOf<ConversationSession>()
    val favoriteSessionIds = mutableStateListOf<String>()

    var selectedConversationSessionId by mutableStateOf<String?>(null)
        private set

    var inputText by mutableStateOf("")
        private set

    var modeLabel by mutableStateOf("일반")
        private set

    var searchEnabled by mutableStateOf(false)
        private set

    var quickSettingsExpanded by mutableStateOf(false)
        private set

    var sessionSearchQuery by mutableStateOf("")
        private set

    var storageStatusText by mutableStateOf(storageManager.getStorageStatusText())
        private set

    var storageLocationText by mutableStateOf(storageManager.getStorageLocationText())
        private set

    var appSpecificSessionCount by mutableStateOf(0)
        private set

    var lastMigrationResultText by mutableStateOf("")
        private set

    val canMigrateAppSpecificSessions: Boolean
        get() = storageManager.isSafReady() && appSpecificSessionCount > 0

    init {
        storageManager.ensureDirectories()
        ragStorageManager.ensureRagDirectories()
        ragIndexStore.ensureIndexMetadata()
        providerSettingsStore.ensureSettingsFiles()
        refreshStorageState()
        loadInitialSessions()
    }

    fun connectSafStorage(uri: Uri) {
        storageManager.saveSafRootUri(uri)
        storageManager.ensureDirectories()
        refreshStorageState()

        conversationSessions.clear()
        favoriteSessionIds.clear()
        loadInitialSessions()
        selectedConversationSessionId = null
    }

    fun useFallbackStorage() {
        storageManager.clearSafRootUri()
        storageManager.ensureDirectories()
        refreshStorageState()

        conversationSessions.clear()
        favoriteSessionIds.clear()
        loadInitialSessions()
        selectedConversationSessionId = null
    }

    fun migrateAppSpecificSessionsToSaf(): ConversationMigrationResult {
        val result = conversationFileStore.migrateAppSpecificSessionsToSaf()

        lastMigrationResultText = "복사 ${result.copiedCount}개 / 건너뜀 ${result.skippedCount}개 / 실패 ${result.failedCount}개"
        refreshStorageState()

        if (storageManager.isSafReady()) {
            conversationSessions.clear()
            favoriteSessionIds.clear()
            conversationSessions.addAll(conversationFileStore.loadSessions())
            reloadFavoriteSessionIds()
            selectedConversationSessionId = null
        }

        return result
    }

    fun reloadSessionsFromStorage() {
        refreshStorageState()
        conversationSessions.clear()
        favoriteSessionIds.clear()
        loadInitialSessions()
        selectedConversationSessionId = null
    }

    fun selectSession(sessionId: String) {
        if (conversationSessions.any { it.sessionId == sessionId }) {
            selectedConversationSessionId = sessionId
        }
    }

    fun clearSelectedSession() {
        selectedConversationSessionId = null
    }

    fun createNewSession(): String {
        val sessionId = "conversation_${System.currentTimeMillis()}"

        val newSession = ConversationSession(
            sessionId = sessionId,
            title = "새 대화",
            lastMessageSummary = "아직 메시지가 없습니다.",
            updatedAt = getCurrentTimeText(),
            messages = emptyList(),
            memorySummary = ""
        )

        conversationSessions.add(0, newSession)
        selectedConversationSessionId = sessionId
        conversationFileStore.updateSession(newSession, isFavorite = false)

        return sessionId
    }

    fun renameSession(sessionId: String, newTitle: String) {
        val trimmedTitle = newTitle.trim()
        if (trimmedTitle.isBlank()) return

        val targetIndex = conversationSessions.indexOfFirst { it.sessionId == sessionId }
        if (targetIndex == -1) return

        val currentSession = conversationSessions[targetIndex]
        val updatedSession = currentSession.copy(
            title = trimmedTitle,
            updatedAt = getCurrentTimeText()
        )

        conversationSessions[targetIndex] = updatedSession
        conversationFileStore.updateSession(
            session = updatedSession,
            isFavorite = favoriteSessionIds.contains(sessionId)
        )
    }

    fun toggleFavorite(sessionId: String) {
        val targetSession = conversationSessions.firstOrNull { it.sessionId == sessionId } ?: return
        val willBeFavorite = !favoriteSessionIds.contains(sessionId)

        if (willBeFavorite) {
            favoriteSessionIds.add(sessionId)
        } else {
            favoriteSessionIds.remove(sessionId)
        }

        conversationFileStore.updateSession(
            session = targetSession,
            isFavorite = willBeFavorite
        )
    }

    fun deleteSession(sessionId: String) {
        conversationFileStore.deleteSession(sessionId)
        conversationSessions.removeAll { it.sessionId == sessionId }
        favoriteSessionIds.remove(sessionId)

        if (selectedConversationSessionId == sessionId) {
            selectedConversationSessionId = null
        }

        refreshStorageState()
    }

    fun updateInputText(value: String) {
        inputText = value
    }

    fun updateModeLabel(value: String) {
        modeLabel = value
    }

    fun updateSearchEnabled(value: Boolean) {
        searchEnabled = value
    }

    fun toggleSearchEnabled() {
        searchEnabled = !searchEnabled
    }

    fun updateQuickSettingsExpanded(value: Boolean) {
        quickSettingsExpanded = value
    }

    fun updateSessionSearchQuery(value: String) {
        sessionSearchQuery = value
    }

    fun sendMessage() {
        val textToSend = inputText.trim()
        if (textToSend.isBlank()) return

        val targetSessionId = selectedConversationSessionId ?: return
        val targetIndex = conversationSessions.indexOfFirst {
            it.sessionId == targetSessionId
        }

        if (targetIndex == -1) return

        val targetSession = conversationSessions[targetIndex]
        val nowText = getCurrentTimeText()
        val currentTimeMillis = System.currentTimeMillis()
        val runId = "conversation_run_$currentTimeMillis"
        val ragContext = if (searchEnabled) {
            ragContextBuilder.build(
                query = textToSend,
                enabled = true
            )
        } else {
            null
        }

        val userMessage = ConversationMessage(
            messageId = "message_user_$currentTimeMillis",
            sessionId = targetSession.sessionId,
            role = ConversationRole.USER,
            createdAt = nowText,
            blocks = listOf(
                ConversationOutputBlock(
                    blockId = "block_user_$currentTimeMillis",
                    type = ConversationOutputBlockType.TEXT_BLOCK,
                    title = "사용자 입력",
                    content = textToSend,
                    collapsed = false
                )
            ),
            linkedRunId = runId
        )

        val request = ConversationRequest(
            requestId = runId,
            sessionId = targetSession.sessionId,
            userInput = textToSend,
            selectedMode = modeLabel,
            searchEnabled = searchEnabled,
            ragContext = ragContext,
            recentMessages = targetSession.messages.takeLast(10),
            systemInstruction = null,
            selectedProvider = resolveSelectedProviderName(),
            selectedModel = resolveSelectedModelName(),
            createdAt = currentTimeMillis
        )

        val userOnlySession = targetSession.copy(
            lastMessageSummary = textToSend.take(60),
            updatedAt = nowText,
            messages = targetSession.messages + userMessage
        )

        conversationSessions[targetIndex] = userOnlySession
        conversationFileStore.appendMessage(targetSession.sessionId, userMessage)
        conversationFileStore.updateSession(
            session = userOnlySession,
            isFavorite = favoriteSessionIds.contains(targetSession.sessionId)
        )

        inputText = ""

        viewModelScope.launch {
            val response = conversationEngine.execute(request)
            val assistantNowText = getCurrentTimeText()
            val assistantMessage = ConversationMessage(
                messageId = "message_assistant_${System.currentTimeMillis()}",
                sessionId = targetSession.sessionId,
                role = ConversationRole.ASSISTANT,
                createdAt = assistantNowText,
                blocks = response.blocks,
                linkedRunId = runId
            )

            val latestIndex = conversationSessions.indexOfFirst {
                it.sessionId == targetSession.sessionId
            }

            if (latestIndex == -1) return@launch

            val latestSession = conversationSessions[latestIndex]
            val updatedSession = latestSession.copy(
                lastMessageSummary = textToSend.take(60),
                updatedAt = assistantNowText,
                messages = latestSession.messages + assistantMessage,
                latestRun = response.runInfo
            )

            conversationSessions[latestIndex] = updatedSession
            conversationFileStore.appendMessage(targetSession.sessionId, assistantMessage)
            conversationFileStore.updateSession(
                session = updatedSession,
                isFavorite = favoriteSessionIds.contains(targetSession.sessionId)
            )
        }
    }

    fun updateUserMessage(
        messageId: String,
        newText: String
    ) {
        val editedText = newText.trim()
        if (editedText.isBlank()) return

        val targetSessionId = selectedConversationSessionId ?: return
        val targetSessionIndex = conversationSessions.indexOfFirst {
            it.sessionId == targetSessionId
        }

        if (targetSessionIndex == -1) return

        val targetSession = conversationSessions[targetSessionIndex]
        val updatedMessages = targetSession.messages.map { message ->
            if (message.messageId == messageId && message.role == ConversationRole.USER) {
                message.copy(
                    blocks = message.blocks.map { block ->
                        if (
                            block.type == ConversationOutputBlockType.TEXT_BLOCK ||
                            block.type == ConversationOutputBlockType.MARKDOWN_BLOCK
                        ) {
                            block.copy(content = editedText)
                        } else {
                            block
                        }
                    }
                )
            } else {
                message
            }
        }

        val updatedSession = targetSession.copy(
            lastMessageSummary = editedText.take(60),
            updatedAt = getCurrentTimeText(),
            messages = updatedMessages
        )

        conversationSessions[targetSessionIndex] = updatedSession
        conversationFileStore.updateSession(
            session = updatedSession,
            isFavorite = favoriteSessionIds.contains(targetSessionId)
        )
    }


    private fun resolveSelectedProviderName(): String {
        val defaultModel = resolveDefaultConversationModelProfile() ?: return "MODEL_MISSING"

        val providerForModel = providerSettingsStore.loadProviderProfiles()
            .firstOrNull { it.providerId == defaultModel.providerId && it.isEnabled }

        return providerForModel?.providerId ?: "MODEL_MISSING"
    }

    private fun resolveSelectedModelName(): String {
        val defaultModel = resolveDefaultConversationModelProfile()
        if (defaultModel != null && defaultModel.rawModelName.isNotBlank()) {
            return defaultModel.rawModelName
        }

        return "MODEL_MISSING"
    }

    private fun resolveDefaultConversationModelProfile(): ConversationModelProfile? {
        return runCatching {
            providerSettingsStore.loadModelProfiles()
                .firstOrNull { model -> model.enabled && model.isDefaultForConversation }
        }.getOrNull()
    }

    private fun resolveProviderApiKey(providerId: String): String? {
        return providerSettingsStore.loadProviderApiKey(providerId)
    }

    private fun loadInitialSessions() {
        val loadedSessions = conversationFileStore.loadSessions()

        if (loadedSessions.isNotEmpty()) {
            conversationSessions.clear()
            conversationSessions.addAll(loadedSessions)
            reloadFavoriteSessionIds()
            return
        }

        if (conversationSessions.isEmpty()) {
            val dummySessions = createDummyConversationSessions()
            conversationSessions.addAll(dummySessions)
            dummySessions.forEach { session ->
                conversationFileStore.updateSession(session, isFavorite = false)
                session.messages.forEach { message ->
                    conversationFileStore.appendMessage(session.sessionId, message)
                }
            }
            favoriteSessionIds.clear()
        }
    }

    private fun reloadFavoriteSessionIds() {
        favoriteSessionIds.clear()
        conversationSessions.forEach { session ->
            if (conversationFileStore.isSessionFavorite(session.sessionId)) {
                favoriteSessionIds.add(session.sessionId)
            }
        }
    }

    private fun refreshStorageState() {
        storageStatusText = storageManager.getStorageStatusText()
        storageLocationText = storageManager.getStorageLocationText()
        appSpecificSessionCount = conversationFileStore.getAppSpecificSessionCount()
    }
}
