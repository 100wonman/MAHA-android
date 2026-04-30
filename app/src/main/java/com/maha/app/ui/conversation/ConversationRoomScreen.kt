package com.maha.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ConversationRoomScreen(
    session: ConversationSession,
    inputText: String,
    searchEnabled: Boolean,
    modeLabel: String,
    isRunning: Boolean,
    onInputTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onToggleSearch: () -> Unit,
    onModeChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGlobalSettings: () -> Unit,
    onEditMessage: (String, String) -> Unit,
    onAssistantEditUnsupported: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(session.messages.size) {
        if (session.messages.isNotEmpty()) {
            listState.animateScrollToItem(session.messages.size)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            ConversationInputPanel(
                inputText = inputText,
                searchEnabled = searchEnabled,
                modeLabel = modeLabel,
                isRunning = isRunning,
                onInputTextChange = onInputTextChange,
                onSend = onSend,
                onToggleSearch = onToggleSearch,
                onModeChange = onModeChange,
                onOpenSettings = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConversationHamburgerButton(
                    onClick = onOpenGlobalSettings
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    if (session.messages.isEmpty()) {
                        EmptyConversationCard()
                    }
                }

                itemsIndexed(session.messages) { index, message ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val triggerText = if (message.role == ConversationRole.ASSISTANT && index > 0) {
                            session.messages[index - 1].blocks.joinToString("\n") { block -> block.content }
                        } else {
                            ""
                        }

                        val displayBlocks = if (message.role == ConversationRole.ASSISTANT) {
                            message.blocks + createHighlightTestBlocks(
                                triggerText = triggerText,
                                createdAt = message.createdAt
                            )
                        } else {
                            message.blocks
                        }

                        val traceBlocks = if (message.role == ConversationRole.ASSISTANT) {
                            displayBlocks.filter { block -> block.type.name == "TRACE_BLOCK" }
                        } else {
                            emptyList()
                        }

                        if (message.role == ConversationRole.ASSISTANT) {
                            ConversationRunSummaryPanelReadable(
                                run = displayRunForConversation(session.sessionId, session.latestRun),
                                traceBlocks = traceBlocks
                            )
                        }

                        displayBlocks
                            .filterNot { block ->
                                message.role == ConversationRole.ASSISTANT && block.type.name == "TRACE_BLOCK"
                            }
                            .forEach { block ->
                                ConversationOutputBlockRenderer(
                                    block = block,
                                    role = message.role,
                                    createdAt = message.createdAt,
                                    canEdit = message.role == ConversationRole.USER,
                                    onEditRequest = {
                                        onEditMessage(message.messageId, block.content)
                                    },
                                    onUnsupportedEditRequest = onAssistantEditUnsupported
                                )
                            }
                    }
                }
            }
        }
    }
}

private fun createHighlightTestBlocks(
    triggerText: String,
    createdAt: String
): List<ConversationOutputBlock> {
    val blocks = mutableListOf<ConversationOutputBlock>()
    val baseId = createdAt.ifBlank { "highlight_test" }
        .replace(Regex("[^A-Za-z0-9_가-힣]"), "_")

    if (triggerText.contains("코드테스트", ignoreCase = true)) {
        blocks.add(
            ConversationOutputBlock(
                blockId = "block_code_test_$baseId",
                type = ConversationOutputBlockType.CODE_BLOCK,
                title = "kotlin",
                content = """fun test() {
    val name = "GPT"
    val count = 3
    if (count > 0) {
        println(name)
    }
}""".trimIndent(),
                collapsed = false
            )
        )
    }

    if (triggerText.contains("json테스트", ignoreCase = true)) {
        blocks.add(
            ConversationOutputBlock(
                blockId = "block_json_test_$baseId",
                type = ConversationOutputBlockType.JSON_BLOCK,
                title = "json",
                content = """{
  "name": "GPT",
  "count": 3,
  "active": true,
  "tags": ["ai", "test"]
}""".trimIndent(),
                collapsed = false
            )
        )
    }

    return blocks
}

@Composable
private fun EmptyConversationCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = conversationUnifiedCardShape(),
        colors = CardDefaults.cardColors(
            containerColor = conversationUnifiedCardColor()
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "아직 메시지가 없습니다.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "아래 입력창에 내용을 입력하면 이곳에 대화가 표시됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
        }
    }
}

@Composable
private fun ConversationOutputBlockRenderer(
    block: ConversationOutputBlock,
    role: ConversationRole,
    createdAt: String,
    canEdit: Boolean,
    onEditRequest: () -> Unit,
    onUnsupportedEditRequest: () -> Unit
) {
    val blockTypeName = block.type.name
    val isTextLike = blockTypeName == "TEXT_BLOCK" || blockTypeName == "MARKDOWN_BLOCK"

    if (role == ConversationRole.USER) {
        UserMessageBlock(
            text = block.content,
            createdAt = createdAt,
            canEdit = canEdit,
            onEditRequest = onEditRequest,
            onUnsupportedEditRequest = onUnsupportedEditRequest
        )
        return
    }

    if (isTextLike) {
        AssistantPlainTextBlock(text = block.content)
        return
    }

    when (blockTypeName) {
        "CODE_BLOCK" -> StructuredOutputBlock(
            title = block.title.ifBlank { "Code" },
            label = detectCodeLabel(block.title, block.content),
            content = block.content,
            initiallyCollapsed = false
        ) {
            CodeContent(
                text = block.content,
                isJson = detectCodeLabel(block.title, block.content) == "json"
            )
        }

        "JSON_BLOCK" -> StructuredOutputBlock(
            title = block.title.ifBlank { "JSON" },
            label = "json",
            content = block.content,
            initiallyCollapsed = false
        ) {
            CodeContent(
                text = prettyJsonText(block.content),
                isJson = true
            )
        }

        "TABLE_BLOCK" -> StructuredOutputBlock(
            title = block.title.ifBlank { "Table" },
            label = "table",
            content = block.content,
            initiallyCollapsed = false
        ) {
            TableContent(text = block.content)
        }

        "ERROR_BLOCK", "TRACE_BLOCK", "MEMORY_BLOCK" -> StructuredOutputBlock(
            title = block.title.ifBlank { blockTypeName.removeSuffix("_BLOCK") },
            label = blockTypeName.removeSuffix("_BLOCK").lowercase(),
            content = block.content,
            initiallyCollapsed = true,
            isWarning = blockTypeName == "ERROR_BLOCK"
        ) {
            CodeContent(text = block.content)
        }

        else -> StructuredOutputBlock(
            title = block.title.ifBlank { blockTypeName.removeSuffix("_BLOCK") },
            label = blockTypeName.removeSuffix("_BLOCK").lowercase(),
            content = block.content,
            initiallyCollapsed = block.collapsed
        ) {
            CodeContent(text = block.content)
        }
    }
}

@Composable
private fun UserMessageBlock(
    text: String,
    createdAt: String,
    canEdit: Boolean,
    onEditRequest: () -> Unit,
    onUnsupportedEditRequest: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.8f),
            shape = conversationUnifiedCardShape(),
            colors = CardDefaults.cardColors(
                containerColor = conversationUnifiedCardColor()
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = createdAt.ifBlank { "입력 시각 없음" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        modifier = Modifier.weight(1f)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (canEdit) {
                                    onEditRequest()
                                } else {
                                    onUnsupportedEditRequest()
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Text(
                                text = "✎",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(text))
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Text(
                                text = "⧉",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                SelectionContainer {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantPlainTextBlock(text: String) {
    SelectionContainer {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyLarge.copy(
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.25f
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ConversationRunSummaryPanelReadable(
    run: ConversationRun,
    traceBlocks: List<ConversationOutputBlock> = emptyList()
) {
    val clipboardManager = LocalClipboardManager.current
    var isCollapsed by rememberSaveable(run.runId) { mutableStateOf(true) }
    val traceText = traceBlocks
        .mapNotNull { block -> block.content.takeIf { it.isNotBlank() } }
        .joinToString(separator = "\n\n")

    val summaryCopyText = buildRunSummaryCopyText(run)
    val traceCopyText = traceText.ifBlank { "실행과정 없음" }
    val workerCopyTexts = run.workerResults.mapIndexed { index, worker ->
        buildWorkerCopyText(index, worker)
    }

    val copyText = buildString {
        appendLine(summaryCopyText)

        if (traceText.isNotBlank()) {
            appendLine()
            appendLine("[실행과정]")
            appendLine(traceText)
        }

        if (workerCopyTexts.isNotEmpty()) {
            appendLine()
            appendLine("[Worker별 실행정보]")
            workerCopyTexts.forEach { workerText ->
                appendLine(workerText)
                appendLine()
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                isCollapsed = !isCollapsed
            },
        shape = conversationUnifiedCardShape(),
        colors = CardDefaults.cardColors(
            containerColor = conversationUnifiedCardColor()
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "실행 정보",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "${run.workerResults.size} worker · ${run.totalLatencySec}s · retry ${run.totalRetryCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(copyText.trim()))
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Text(
                            text = "⧉",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = {
                            isCollapsed = !isCollapsed
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Text(
                            text = if (isCollapsed) "⌄" else "⌃",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (!isCollapsed) {
                RunFlatSection(
                    title = "실행정보 요약",
                    copyText = summaryCopyText
                ) {
                    RunSummarySection(run = run)
                }

                if (traceText.isNotBlank()) {
                    RunFlatSection(
                        title = "실행과정",
                        copyText = traceCopyText
                    ) {
                        RunTraceSection(traceText = traceText)
                    }
                }

                run.workerResults.forEachIndexed { index, worker ->
                    val workerTitle = worker.workerName.ifBlank { "워커 ${index + 1}" }
                    RunFlatSection(
                        title = workerTitle,
                        copyText = workerCopyTexts[index]
                    ) {
                        RunWorkerResultSection(
                            index = index,
                            worker = worker
                        )
                    }
                }
            }
        }
    }
}


private fun displayRunForConversation(
    sessionId: String,
    latestRun: ConversationRun?
): ConversationRun {
    val fallbackRun = createDummyConversationRun(sessionId)
    val run = latestRun ?: fallbackRun
    val hasOnlyDummyWorker = run.workerResults.size == 1 &&
            run.workerResults.firstOrNull()?.workerName?.contains("Dummy", ignoreCase = true) == true

    if (!hasOnlyDummyWorker) {
        return run
    }

    return fallbackRun.copy(
        runId = run.runId,
        userInput = run.userInput,
        status = run.status,
        totalLatencySec = run.totalLatencySec,
        totalRetryCount = run.totalRetryCount
    )
}

private fun buildRunSummaryCopyText(run: ConversationRun): String {
    return buildString {
        appendLine("[실행정보 요약]")
        appendLine("runId: ${run.runId}")
        appendLine("input: ${run.userInput}")
        appendLine("status: ${run.status}")
        appendLine("latencySec: ${run.totalLatencySec}")
        appendLine("workerCount: ${run.workerResults.size}")
        appendLine("retryCount: ${run.totalRetryCount}")
    }.trim()
}

private fun buildWorkerCopyText(
    index: Int,
    worker: ConversationWorkerResult
): String {
    return buildString {
        appendLine("[워커 ${index + 1}]")
        appendLine("worker: ${worker.workerName}")
        appendLine("provider: ${worker.providerName}")
        appendLine("model: ${worker.modelName}")
        appendLine("status: ${worker.status}")
        appendLine("latencySec: ${worker.latencySec}")
        appendLine("retryCount: ${worker.retryCount}")
        appendLine("tokensPerSecond: ${worker.tokensPerSecond ?: "-"}")
        appendLine("errorType: ${worker.errorType}")
        appendLine("summary: ${worker.outputSummary}")
        appendLine("rawOutput: ${worker.rawOutput}")
    }.trim()
}

@Composable
private fun RunFlatSection(
    title: String,
    copyText: String,
    content: @Composable () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(copyText))
                },
                modifier = Modifier.size(34.dp)
            ) {
                Text(
                    text = "⧉",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        )

        content()
    }
}

@Composable
private fun RunSummarySection(run: ConversationRun) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "상태: ${run.status}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "총 시간: ${run.totalLatencySec}s · Worker: ${run.workerResults.size} · 재시도: ${run.totalRetryCount}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
        )
    }
}

@Composable
private fun RunTraceSection(traceText: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SelectionContainer {
            Text(
                text = traceText,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.18f
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
                softWrap = false
            )
        }
    }
}

@Composable
private fun RunWorkerResultSection(
    index: Int,
    worker: ConversationWorkerResult
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "워커 ${index + 1}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
        )

        Text(
            text = "${worker.providerName} · ${worker.modelName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
        )

        Text(
            text = "상태: ${worker.status} · 시간: ${worker.latencySec}s · 재시도: ${worker.retryCount}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
        )

        if (worker.errorType.isNotBlank()) {
            Text(
                text = "오류 유형: ${worker.errorType}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (worker.outputSummary.isNotBlank()) {
            SelectionContainer {
                Text(
                    text = worker.outputSummary,
                    style = MaterialTheme.typography.bodySmall.copy(
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.18f
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f)
                )
            }
        }
    }
}

@Composable
private fun StructuredOutputBlock(
    title: String,
    label: String,
    content: String,
    initiallyCollapsed: Boolean,
    isWarning: Boolean = false,
    body: @Composable () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var isCollapsed by rememberSaveable(title, content) { mutableStateOf(initiallyCollapsed) }
    val containerColor = if (isWarning) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.24f)
    } else {
        conversationUnifiedCardColor()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (initiallyCollapsed) {
                    isCollapsed = !isCollapsed
                }
            },
        shape = conversationUnifiedCardShape(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(content))
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Text(
                            text = "⧉",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = {
                            isCollapsed = !isCollapsed
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Text(
                            text = if (isCollapsed) "⌄" else "⌃",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (!isCollapsed) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    body()
                }
            }
        }
    }
}

@Composable
private fun CodeContent(
    text: String,
    isJson: Boolean = false
) {
    val annotatedText = if (isJson) {
        highlightJsonText(text)
    } else {
        highlightCodeText(text)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 2.dp, vertical = 4.dp)
    ) {
        SelectionContainer {
            Text(
                text = annotatedText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.25f
                ),
                softWrap = false
            )
        }
    }
}

@Composable
private fun highlightCodeText(text: String): AnnotatedString {
    val baseColor = MaterialTheme.colorScheme.onSurface
    val keywordColor = Color(0xFF82AAFF)
    val stringColor = Color(0xFFC3E88D)
    val numberColor = Color(0xFFF78C6C)
    val commentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f)
    val symbolColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    val keywords = setOf(
        "fun", "val", "var", "if", "else", "return", "for", "while", "when",
        "class", "object", "interface", "data", "sealed", "private", "public",
        "internal", "override", "import", "package", "null", "true", "false",
        "try", "catch", "finally", "suspend", "launch", "remember"
    )

    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                char == '/' && index + 1 < text.length && text[index + 1] == '/' -> {
                    val end = text.indexOf('\n', index).let { if (it == -1) text.length else it }
                    withStyle(SpanStyle(color = commentColor)) {
                        append(text.substring(index, end))
                    }
                    index = end
                }

                char == '"' -> {
                    val start = index
                    index += 1
                    var escaped = false
                    while (index < text.length) {
                        val current = text[index]
                        if (current == '"' && !escaped) {
                            index += 1
                            break
                        }
                        escaped = current == '\\' && !escaped
                        if (current != '\\') escaped = false
                        index += 1
                    }
                    withStyle(SpanStyle(color = stringColor)) {
                        append(text.substring(start, index.coerceAtMost(text.length)))
                    }
                }

                char.isDigit() -> {
                    val start = index
                    while (index < text.length && (text[index].isDigit() || text[index] == '.')) {
                        index += 1
                    }
                    withStyle(SpanStyle(color = numberColor)) {
                        append(text.substring(start, index))
                    }
                }

                char.isLetter() || char == '_' -> {
                    val start = index
                    while (index < text.length && (text[index].isLetterOrDigit() || text[index] == '_')) {
                        index += 1
                    }
                    val word = text.substring(start, index)
                    if (word in keywords) {
                        withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.SemiBold)) {
                            append(word)
                        }
                    } else {
                        withStyle(SpanStyle(color = baseColor)) {
                            append(word)
                        }
                    }
                }

                char in "{}[]().,;:<>+-=*/!&|" -> {
                    withStyle(SpanStyle(color = symbolColor)) {
                        append(char)
                    }
                    index += 1
                }

                else -> {
                    withStyle(SpanStyle(color = baseColor)) {
                        append(char)
                    }
                    index += 1
                }
            }
        }
    }
}

@Composable
private fun highlightJsonText(text: String): AnnotatedString {
    val baseColor = MaterialTheme.colorScheme.onSurface
    val keyColor = Color(0xFF82AAFF)
    val stringColor = Color(0xFFC3E88D)
    val numberColor = Color(0xFFF78C6C)
    val boolNullColor = Color(0xFFC792EA)
    val symbolColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)

    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                char == '"' -> {
                    val start = index
                    index += 1
                    var escaped = false
                    while (index < text.length) {
                        val current = text[index]
                        if (current == '"' && !escaped) {
                            index += 1
                            break
                        }
                        escaped = current == '\\' && !escaped
                        if (current != '\\') escaped = false
                        index += 1
                    }
                    var lookAhead = index
                    while (lookAhead < text.length && text[lookAhead].isWhitespace()) lookAhead += 1
                    val isKey = lookAhead < text.length && text[lookAhead] == ':'
                    withStyle(SpanStyle(color = if (isKey) keyColor else stringColor)) {
                        append(text.substring(start, index.coerceAtMost(text.length)))
                    }
                }

                char.isDigit() || char == '-' -> {
                    val start = index
                    index += 1
                    while (index < text.length && (text[index].isDigit() || text[index] == '.')) {
                        index += 1
                    }
                    withStyle(SpanStyle(color = numberColor)) {
                        append(text.substring(start, index))
                    }
                }

                text.startsWith("true", index) || text.startsWith("false", index) || text.startsWith("null", index) -> {
                    val token = when {
                        text.startsWith("true", index) -> "true"
                        text.startsWith("false", index) -> "false"
                        else -> "null"
                    }
                    withStyle(SpanStyle(color = boolNullColor, fontWeight = FontWeight.SemiBold)) {
                        append(token)
                    }
                    index += token.length
                }

                char in "{}[],:" -> {
                    withStyle(SpanStyle(color = symbolColor)) {
                        append(char)
                    }
                    index += 1
                }

                else -> {
                    withStyle(SpanStyle(color = baseColor)) {
                        append(char)
                    }
                    index += 1
                }
            }
        }
    }
}

@Composable
private fun TableContent(text: String) {
    val rows = parseTableRows(text)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (rows.isEmpty()) {
            SelectionContainer {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = false
                )
            }
            return@Column
        }

        rows.forEachIndexed { rowIndex, cells ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                cells.forEach { cell ->
                    SelectionContainer {
                        Text(
                            text = cell,
                            modifier = Modifier
                                .widthIn(min = 88.dp, max = 260.dp)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            style = if (rowIndex == 0) {
                                MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            } else {
                                MaterialTheme.typography.bodySmall
                            },
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private fun detectCodeLabel(title: String, content: String): String {
    val lowerTitle = title.lowercase()
    return when {
        lowerTitle.contains("kotlin") -> "kotlin"
        lowerTitle.contains("json") -> "json"
        lowerTitle.contains("xml") -> "xml"
        lowerTitle.contains("gradle") -> "gradle"
        content.trimStart().startsWith("{") -> "json"
        else -> "code"
    }
}

private fun prettyJsonText(text: String): String {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return text

    val result = StringBuilder()
    var indent = 0
    var inString = false
    var escape = false

    trimmed.forEach { char ->
        when {
            escape -> {
                result.append(char)
                escape = false
            }

            char == '\\' && inString -> {
                result.append(char)
                escape = true
            }

            char == '"' -> {
                result.append(char)
                inString = !inString
            }

            !inString && (char == '{' || char == '[') -> {
                result.append(char).append('\n')
                indent += 1
                result.append("  ".repeat(indent))
            }

            !inString && (char == '}' || char == ']') -> {
                result.append('\n')
                indent = (indent - 1).coerceAtLeast(0)
                result.append("  ".repeat(indent)).append(char)
            }

            !inString && char == ',' -> {
                result.append(char).append('\n')
                result.append("  ".repeat(indent))
            }

            !inString && char == ':' -> {
                result.append(": ")
            }

            !inString && char.isWhitespace() -> Unit
            else -> result.append(char)
        }
    }

    return result.toString()
}

private fun parseTableRows(text: String): List<List<String>> {
    return text
        .lines()
        .map { line -> line.trim() }
        .filter { line -> line.isNotBlank() }
        .filterNot { line -> line.matches(Regex("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?$")) }
        .map { line ->
            line
                .trim('|')
                .split('|')
                .map { cell -> cell.trim() }
                .filter { cell -> cell.isNotBlank() }
        }
        .filter { cells -> cells.isNotEmpty() }
}

@Composable
private fun ConversationInputPanel(
    inputText: String,
    searchEnabled: Boolean,
    modeLabel: String,
    isRunning: Boolean,
    onInputTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onToggleSearch: () -> Unit,
    onModeChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val modeOptions = listOf("자동", "일반", "코드", "검증")
    var isQuickSettingsExpanded by rememberSaveable { mutableStateOf(false) }
    val trimmedInput = inputText.trim()
    val canSend = trimmedInput.isNotEmpty() && !isRunning

    Card(
        modifier = modifier,
        shape = conversationUnifiedCardShape(),
        colors = CardDefaults.cardColors(
            containerColor = conversationUnifiedCardColor()
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        isQuickSettingsExpanded = !isQuickSettingsExpanded
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "빠른 설정",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                    )

                    Text(
                        text = "모드 $modeLabel · 검색 ${if (searchEnabled) "ON" else "OFF"} · Worker 추후",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        maxLines = 1
                    )
                }

                Text(
                    text = if (isQuickSettingsExpanded) "⌃" else "⌄",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (isQuickSettingsExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "모드 선택",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                        )

                        Text(
                            text = "Worker: 추후 지원",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        modeOptions.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .clickable {
                                        onModeChange(option)
                                    }
                                    .padding(end = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = modeLabel == option,
                                    onClick = {
                                        onModeChange(option)
                                    },
                                    modifier = Modifier.size(28.dp)
                                )

                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "검색 사용",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                            )

                            Text(
                                text = if (searchEnabled) "대화 전송 시 검색 보조 ON" else "대화 전송 시 검색 보조 OFF",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f)
                            )
                        }

                        Switch(
                            checked = searchEnabled,
                            onCheckedChange = {
                                onToggleSearch()
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 132.dp)
                        .padding(vertical = 8.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.18f
                    ),
                    minLines = 1,
                    maxLines = 5,
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (inputText.isBlank()) {
                                Text(
                                    text = "메시지를 입력하세요.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                                )
                            }

                            innerTextField()
                        }
                    }
                )

                IconButton(
                    onClick = {
                        if (canSend) {
                            keyboardController?.hide()
                            onSend()
                        }
                    },
                    enabled = canSend,
                    modifier = Modifier.size(44.dp)
                ) {
                    Text(
                        text = if (isRunning) "…" else "➤",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (canSend) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
                        }
                    )
                }
            }
        }
    }
}
