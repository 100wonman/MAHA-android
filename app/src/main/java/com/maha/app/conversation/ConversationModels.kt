package com.maha.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ConversationRole {
    USER,
    ASSISTANT,
    SYSTEM,
    WORKER
}

enum class ConversationOutputBlockType {
    TEXT_BLOCK,
    MARKDOWN_BLOCK,
    CODE_BLOCK,
    TABLE_BLOCK,
    JSON_BLOCK,
    ERROR_BLOCK,
    TRACE_BLOCK,
    MEMORY_BLOCK
}

enum class ConversationRunStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    PARTIAL_FAILED,
    FAILED
}

data class ConversationSession(
    val sessionId: String,
    val title: String,
    val lastMessageSummary: String,
    val updatedAt: String,
    val messages: List<ConversationMessage>,
    val memorySummary: String = "",
    val latestRun: ConversationRun? = null
)

data class ConversationMessage(
    val messageId: String,
    val sessionId: String,
    val role: ConversationRole,
    val createdAt: String,
    val blocks: List<ConversationOutputBlock>,
    val linkedRunId: String? = null
)

data class ConversationOutputBlock(
    val blockId: String,
    val type: ConversationOutputBlockType,
    val title: String,
    val content: String,
    val language: String = "",
    val collapsed: Boolean,
    val copyable: Boolean = true,
    val metadata: Map<String, String> = emptyMap()
)

data class ConversationRun(
    val runId: String,
    val sessionId: String,
    val userInput: String,
    val orchestratorPlan: String,
    val status: ConversationRunStatus,
    val startedAt: String,
    val finishedAt: String,
    val totalLatencySec: Double,
    val totalRetryCount: Int,
    val workerResults: List<ConversationWorkerResult>
)

data class ConversationWorkerResult(
    val workerName: String,
    val providerName: String,
    val modelName: String,
    val status: String,
    val latencySec: Double,
    val retryCount: Int,
    val tokensPerSecond: Double?,
    val errorType: String,
    val outputSummary: String,
    val rawOutput: String
)

internal fun createDummyConversationSessions(): List<ConversationSession> {
    val firstSessionId = "conversation_001"
    val secondSessionId = "conversation_002"

    return listOf(
        ConversationSession(
            sessionId = firstSessionId,
            title = "MAHA 대화모드 설계",
            lastMessageSummary = "OutputBlock 기반 대화방 UI 초안을 정리했습니다.",
            updatedAt = "2026-04-27 10:30:00",
            memorySummary = "대화모드는 작업모드와 분리하고, 블록 기반 출력 구조를 사용한다.",
            messages = listOf(
                ConversationMessage(
                    messageId = "message_001_user",
                    sessionId = firstSessionId,
                    role = ConversationRole.USER,
                    createdAt = "2026-04-27 10:28:00",
                    blocks = listOf(
                        ConversationOutputBlock(
                            blockId = "block_001_user_text",
                            type = ConversationOutputBlockType.TEXT_BLOCK,
                            title = "사용자 입력",
                            content = "대화모드 UI 초안을 설명해줘.",
                            collapsed = false
                        )
                    )
                ),
                ConversationMessage(
                    messageId = "message_002_assistant",
                    sessionId = firstSessionId,
                    role = ConversationRole.ASSISTANT,
                    createdAt = "2026-04-27 10:30:00",
                    linkedRunId = "conversation_run_001",
                    blocks = listOf(
                        ConversationOutputBlock(
                            blockId = "block_002_summary",
                            type = ConversationOutputBlockType.TEXT_BLOCK,
                            title = "요약",
                            content = "대화모드는 세션 목록, 대화방, OutputBlock, 접이식 실행 정보 패널로 구성됩니다.",
                            collapsed = false
                        ),
                        ConversationOutputBlock(
                            blockId = "block_003_markdown",
                            type = ConversationOutputBlockType.MARKDOWN_BLOCK,
                            title = "설계 메모",
                            content = "- 상단은 세션명과 상태만 표시\n- 중앙은 메시지 리스트\n- 하단은 입력창과 실행 버튼 중심",
                            collapsed = false
                        ),
                        ConversationOutputBlock(
                            blockId = "block_004_code",
                            type = ConversationOutputBlockType.CODE_BLOCK,
                            title = "예시 코드 블록",
                            content = "data class ConversationSession(\n    val sessionId: String,\n    val title: String\n)",
                            language = "kotlin",
                            collapsed = false
                        ),
                        ConversationOutputBlock(
                            blockId = "block_005_trace",
                            type = ConversationOutputBlockType.TRACE_BLOCK,
                            title = "실행 과정",
                            content = "Orchestrator → Main Worker → Synthesis Worker 순서로 처리됨",
                            collapsed = true
                        ),
                        ConversationOutputBlock(
                            blockId = "block_006_memory",
                            type = ConversationOutputBlockType.MEMORY_BLOCK,
                            title = "저장 후보 기억",
                            content = "사용자는 대화모드와 작업모드를 분리하기를 원한다.",
                            collapsed = true
                        )
                    )
                )
            )
        ),
        ConversationSession(
            sessionId = secondSessionId,
            title = "로컬 기억 저장소 구상",
            lastMessageSummary = "RAG와 메모리 저장은 후속 단계로 분리했습니다.",
            updatedAt = "2026-04-27 11:05:00",
            memorySummary = "1차 구현에서는 실제 RAG, 검색, 검증, 메모리 저장을 제외한다.",
            messages = listOf(
                ConversationMessage(
                    messageId = "message_003_user",
                    sessionId = secondSessionId,
                    role = ConversationRole.USER,
                    createdAt = "2026-04-27 11:03:00",
                    blocks = listOf(
                        ConversationOutputBlock(
                            blockId = "block_007_user_text",
                            type = ConversationOutputBlockType.TEXT_BLOCK,
                            title = "사용자 입력",
                            content = "로컬 기억 저장소는 이번에 구현해?",
                            collapsed = false
                        )
                    )
                ),
                ConversationMessage(
                    messageId = "message_004_assistant",
                    sessionId = secondSessionId,
                    role = ConversationRole.ASSISTANT,
                    createdAt = "2026-04-27 11:05:00",
                    linkedRunId = "conversation_run_002",
                    blocks = listOf(
                        ConversationOutputBlock(
                            blockId = "block_008_text",
                            type = ConversationOutputBlockType.TEXT_BLOCK,
                            title = "답변",
                            content = "이번 단계에서는 구현하지 않습니다. 먼저 더미 데이터 기반 UI와 UX를 확인합니다.",
                            collapsed = false
                        ),
                        ConversationOutputBlock(
                            blockId = "block_009_json",
                            type = ConversationOutputBlockType.JSON_BLOCK,
                            title = "JSON 예시",
                            content = "{\n  \"ragEnabled\": false,\n  \"memoryWriteEnabled\": false,\n  \"mode\": \"dummy-ui\"\n}",
                            language = "json",
                            collapsed = false
                        ),
                        ConversationOutputBlock(
                            blockId = "block_010_error",
                            type = ConversationOutputBlockType.ERROR_BLOCK,
                            title = "제외 항목",
                            content = "실제 API 호출, 검색 Worker, 검증 Worker, VectorDB는 이번 단계에서 제외합니다.",
                            collapsed = false
                        )
                    )
                )
            )
        )
    )
}

internal fun createDummyConversationRun(
    sessionId: String
): ConversationRun {
    return ConversationRun(
        runId = "conversation_run_$sessionId",
        sessionId = sessionId,
        userInput = "더미 대화 입력",
        orchestratorPlan = "더미 UI 확인용 실행 계획",
        status = ConversationRunStatus.COMPLETED,
        startedAt = "2026-04-27 10:28:00",
        finishedAt = "2026-04-27 10:30:00",
        totalLatencySec = 3.2,
        totalRetryCount = 1,
        workerResults = listOf(
            ConversationWorkerResult(
                workerName = "Orchestrator",
                providerName = "DUMMY",
                modelName = "dummy",
                status = "COMPLETED",
                latencySec = 0.8,
                retryCount = 0,
                tokensPerSecond = null,
                errorType = "",
                outputSummary = "사용자 의도 분석 완료",
                rawOutput = "Dummy orchestrator output"
            ),
            ConversationWorkerResult(
                workerName = "Main Worker",
                providerName = "DUMMY",
                modelName = "dummy",
                status = "COMPLETED",
                latencySec = 1.6,
                retryCount = 1,
                tokensPerSecond = 18.4,
                errorType = "",
                outputSummary = "본문 답변 생성 완료",
                rawOutput = "Dummy main worker output"
            ),
            ConversationWorkerResult(
                workerName = "Synthesis Worker",
                providerName = "DUMMY",
                modelName = "dummy",
                status = "COMPLETED",
                latencySec = 0.8,
                retryCount = 0,
                tokensPerSecond = 22.1,
                errorType = "",
                outputSummary = "최종 답변 정리 완료",
                rawOutput = "Dummy synthesis output"
            )
        )
    )
}
