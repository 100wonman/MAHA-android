package com.maha.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class ConversationViewModel : ViewModel() {
    val conversationSessions = mutableStateListOf<ConversationSession>()

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

    init {
        if (conversationSessions.isEmpty()) {
            conversationSessions.addAll(createDummyConversationSessions())
        }
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

        return sessionId
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

        val assistantMessage = ConversationMessage(
            messageId = "message_assistant_$currentTimeMillis",
            sessionId = targetSession.sessionId,
            role = ConversationRole.ASSISTANT,
            createdAt = nowText,
            blocks = listOf(
                ConversationOutputBlock(
                    blockId = "block_assistant_$currentTimeMillis",
                    type = ConversationOutputBlockType.TEXT_BLOCK,
                    title = "더미 응답",
                    content = "더미 실행 결과입니다. 실제 API 호출은 아직 연결하지 않았습니다.",
                    collapsed = false
                ),
                ConversationOutputBlock(
                    blockId = "block_trace_$currentTimeMillis",
                    type = ConversationOutputBlockType.TRACE_BLOCK,
                    title = "실행 과정",
                    content = "입력 수신 → 더미 응답 생성 → 화면 갱신",
                    collapsed = true
                )
            ),
            linkedRunId = runId
        )

        val latestRun = createDummyConversationRun(targetSession.sessionId).copy(
            runId = runId,
            userInput = textToSend,
            totalLatencySec = 1.1,
            totalRetryCount = 0,
            workerResults = listOf(
                ConversationWorkerResult(
                    workerName = "Dummy Conversation Worker",
                    providerName = "DUMMY",
                    modelName = "dummy",
                    status = "COMPLETED",
                    latencySec = 1.1,
                    retryCount = 0,
                    tokensPerSecond = null,
                    errorType = "",
                    outputSummary = "더미 응답 생성 완료",
                    rawOutput = "Dummy response"
                )
            )
        )

        conversationSessions[targetIndex] = targetSession.copy(
            lastMessageSummary = textToSend.take(60),
            updatedAt = nowText,
            messages = targetSession.messages + userMessage + assistantMessage,
            latestRun = latestRun
        )

        inputText = ""
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

        conversationSessions[targetSessionIndex] = targetSession.copy(
            lastMessageSummary = editedText.take(60),
            updatedAt = getCurrentTimeText(),
            messages = updatedMessages
        )
    }
}
