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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ConversationRoomScreen(
    session: ConversationSession,
    inputText: String,
    searchEnabled: Boolean,
    webSearchEnabled: Boolean,
    webSearchFallbackEnabled: Boolean,
    modeLabel: String,
    isRunning: Boolean,
    onInputTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onToggleSearch: () -> Unit,
    onToggleWebSearch: () -> Unit,
    onToggleWebSearchFallback: () -> Unit,
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
                webSearchEnabled = webSearchEnabled,
                webSearchFallbackEnabled = webSearchFallbackEnabled,
                modeLabel = modeLabel,
                isRunning = isRunning,
                onInputTextChange = onInputTextChange,
                onSend = onSend,
                onToggleSearch = onToggleSearch,
                onToggleWebSearch = onToggleWebSearch,
                onToggleWebSearchFallback = onToggleWebSearchFallback,
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

                        val baseDisplayBlocks = if (message.role == ConversationRole.ASSISTANT) {
                            message.blocks + createHighlightTestBlocks(
                                triggerText = triggerText,
                                createdAt = message.createdAt
                            )
                        } else {
                            message.blocks
                        }

                        val displayBlocks = if (message.role == ConversationRole.ASSISTANT) {
                            expandAssistantStructuredBlocks(baseDisplayBlocks)
                        } else {
                            baseDisplayBlocks
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
        ConversationAssistantTextRenderer(text = block.content)
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

        "ERROR_BLOCK" -> StructuredOutputBlock(
            title = block.title.ifBlank { blockTypeName.removeSuffix("_BLOCK") },
            label = blockTypeName.removeSuffix("_BLOCK").lowercase(),
            content = formatErrorBlockDisplayText(block.content),
            initiallyCollapsed = true,
            isWarning = true
        ) {
            WrappedPlainContent(text = formatErrorBlockDisplayText(block.content))
        }

        "TRACE_BLOCK", "MEMORY_BLOCK" -> StructuredOutputBlock(
            title = block.title.ifBlank { blockTypeName.removeSuffix("_BLOCK") },
            label = blockTypeName.removeSuffix("_BLOCK").lowercase(),
            content = block.content,
            initiallyCollapsed = true,
            isWarning = false
        ) {
            WrappedPlainContent(text = block.content)
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
    val segments = remember(text) { parseConversationDisplaySegments(text) }
    val hasMarkdownTableSegment = segments.any { segment ->
        segment.type == ConversationOutputBlockType.TABLE_BLOCK &&
                parseAssistantMarkdownTableRows(segment.content).isNotEmpty()
    }

    if (!hasMarkdownTableSegment) {
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
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        segments.forEach { segment ->
            when (segment.type) {
                ConversationOutputBlockType.TABLE_BLOCK -> {
                    val rows = parseAssistantMarkdownTableRows(segment.content)
                    if (rows.isNotEmpty()) {
                        AssistantMarkdownTableContent(rows = rows)
                    }
                }

                else -> {
                    if (segment.content.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                text = segment.content,
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
                }
            }
        }
    }
}

@Composable
private fun AssistantMarkdownTableContent(
    rows: List<List<String>>
) {
    val horizontalScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalScrollState)
            .background(conversationUnifiedCardColor().copy(alpha = 0.28f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        rows.forEachIndexed { rowIndex, cells ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                cells.forEachIndexed { cellIndex, cell ->
                    SelectionContainer {
                        Text(
                            text = normalizeAssistantMarkdownTableCell(cell),
                            modifier = Modifier
                                .widthIn(
                                    min = if (cellIndex == 0) 104.dp else 160.dp,
                                    max = if (cellIndex == 0) 180.dp else 280.dp
                                )
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            style = if (rowIndex == 0) {
                                MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            } else {
                                MaterialTheme.typography.bodySmall
                            },
                            color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (rowIndex == 0 || cellIndex == 0) 0.95f else 0.86f
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun parseAssistantMarkdownTableRows(
    text: String
): List<List<String>> {
    return text
        .lines()
        .map { line -> line.trim() }
        .filter { line -> line.isNotBlank() }
        .filterNot { line -> isAssistantMarkdownTableSeparator(line) }
        .map { line ->
            line
                .trim('|')
                .split('|')
                .map { cell -> normalizeAssistantMarkdownTableCell(cell) }
                .filter { cell -> cell.isNotBlank() }
        }
        .filter { cells -> cells.size >= 2 }
}

private fun isAssistantMarkdownTableSeparator(
    line: String
): Boolean {
    val normalized = line.trim().trim('|').trim()
    if (normalized.isBlank()) {
        return false
    }
    return normalized
        .split('|')
        .map { cell -> cell.trim() }
        .all { cell -> cell.matches(Regex(":?-{3,}:?")) }
}

private fun normalizeAssistantMarkdownTableCell(
    value: String
): String {
    return value
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("\\$\\\\rightarrow\\$"), "→")
        .replace(Regex("\\*\\*(.*?)\\*\\*")) { match -> match.groupValues[1] }
        .replace(Regex("__(.*?)__")) { match -> match.groupValues[1] }
        .trim()
}

@Composable
private fun ConversationRunSummaryPanelReadable(
    run: ConversationRun,
    traceBlocks: List<ConversationOutputBlock> = emptyList()
) {
    val clipboardManager = LocalClipboardManager.current
    var isCollapsed by rememberSaveable(run.runId) { mutableStateOf(true) }
    val rawTraceText = traceBlocks
        .mapNotNull { block -> block.content.takeIf { it.isNotBlank() } }
        .joinToString(separator = "\n\n")
    val ragInfo = parseRagRunInfo(rawTraceText)
    val webSearchGroundingInfo = parseWebSearchGroundingInfo(rawTraceText)
    val webSearchSourcesInfo = parseWebSearchSourcesInfo(rawTraceText)
    val providerResponseSummaryText = extractTraceSection(rawTraceText, "[PROVIDER_RESPONSE_SUMMARY]")
    val fallbackProviderResponseSummaryText = extractTraceSection(rawTraceText, "[FALLBACK_PROVIDER_RESPONSE_SUMMARY]")
    val executionTraceText = cleanExecutionTraceText(rawTraceText)
    val answerOutcomeLabel = buildAnswerOutcomeLabel(run.status)
    val hasError = hasRunError(run, rawTraceText)

    val providerDetailWorkerIndex = findProviderDetailWorkerIndex(run)
    val workerProviderDetails = run.workerResults.mapIndexed { index, worker ->
        buildWorkerProviderDetailText(
            worker = worker,
            index = index,
            providerDetailWorkerIndex = providerDetailWorkerIndex,
            providerResponseSummaryText = providerResponseSummaryText,
            fallbackProviderResponseSummaryText = fallbackProviderResponseSummaryText
        )
    }

    val summaryCopyText = buildRunSummaryCopyText(run)
    val ragCopyText = buildRagRunCopyText(ragInfo)
    val webSearchGroundingCopyText = buildWebSearchGroundingCopyText(webSearchGroundingInfo)
    val webSearchSourcesCopyText = buildWebSearchSourcesCopyText(webSearchSourcesInfo)
    val traceCopyText = executionTraceText.ifBlank { "실행과정 없음" }
    val workerCopyTexts = run.workerResults.mapIndexed { index, worker ->
        buildWorkerCopyText(index, worker, workerProviderDetails.getOrNull(index).orEmpty())
    }

    val copyText = buildString {
        appendLine(summaryCopyText)

        if (ragInfo.present) {
            appendLine()
            appendLine(ragCopyText)
        }

        if (webSearchGroundingInfo.present) {
            appendLine()
            appendLine(webSearchGroundingCopyText)
        }

        if (webSearchSourcesInfo.shouldDisplay) {
            appendLine()
            appendLine(webSearchSourcesCopyText)
        }

        if (executionTraceText.isNotBlank()) {
            appendLine()
            appendLine("[실행과정]")
            appendLine(executionTraceText)
        }

        if (ragInfo.contextText.isNotBlank()) {
            appendLine()
            appendLine("[참조 컨텍스트]")
            appendLine(ragInfo.contextText)
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
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "실행 정보",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (hasError) {
                        Text(
                            text = "오류발생",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
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
                    headerStatusLabel = answerOutcomeLabel,
                    copyText = summaryCopyText,
                    initiallyCollapsed = false
                ) {
                    RunSummarySection(run = run)
                }

                if (ragInfo.present) {
                    RunFlatSection(
                        title = "RAG",
                        copyText = ragCopyText,
                        initiallyCollapsed = true
                    ) {
                        RunRagSection(ragInfo = ragInfo)
                    }
                }

                if (webSearchGroundingInfo.present) {
                    RunFlatSection(
                        title = "Web Search Grounding",
                        copyText = webSearchGroundingCopyText,
                        initiallyCollapsed = true
                    ) {
                        RunWebSearchGroundingSection(info = webSearchGroundingInfo)
                    }
                }

                if (webSearchSourcesInfo.shouldDisplay) {
                    RunCollapsibleFlatTextSection(
                        title = "Web Search 참조 출처 보기",
                        copyText = webSearchSourcesCopyText,
                        text = webSearchSourcesCopyText
                    )
                }

                if (executionTraceText.isNotBlank()) {
                    RunFlatSection(
                        title = "실행과정",
                        headerStatusLabel = answerOutcomeLabel,
                        copyText = traceCopyText,
                        initiallyCollapsed = true
                    ) {
                        RunTraceSection(traceText = executionTraceText)
                    }
                }

                run.workerResults.forEachIndexed { index, worker ->
                    val workerTitle = worker.workerName.ifBlank { "워커 ${index + 1}" }
                    RunFlatSection(
                        title = workerTitle,
                        headerStatusLabel = buildWorkerOutcomeLabel(worker.status),
                        copyText = workerCopyTexts[index],
                        initiallyCollapsed = true
                    ) {
                        RunWorkerResultSection(
                            index = index,
                            worker = worker,
                            providerDetailText = workerProviderDetails.getOrNull(index).orEmpty()
                        )
                    }
                }

                if (ragInfo.contextText.isNotBlank()) {
                    RunCollapsibleFlatTextSection(
                        title = "참조 컨텍스트 보기",
                        copyText = ragInfo.contextText,
                        text = ragInfo.contextText
                    )
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
        if (run.workerResults.isNotEmpty()) {
            appendLine()
            appendLine("[Worker 요약]")
            run.workerResults.forEachIndexed { index, worker ->
                appendLine("${index + 1}. ${worker.workerName.ifBlank { "워커 ${index + 1}" }} · ${buildWorkerRequiredSummary(worker)}")
            }
        }
    }.trim()
}

private fun buildWorkerCopyText(
    index: Int,
    worker: ConversationWorkerResult,
    providerDetailText: String = ""
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
        appendLine("errorType: ${formatWorkerErrorLabel(worker, providerDetailText)}")
        appendLine("summary: ${worker.outputSummary}")
        appendLine("rawOutput: ${worker.rawOutput}")
        if (providerDetailText.isNotBlank()) {
            appendLine()
            appendLine("[Provider 응답 상세]")
            appendLine(providerDetailText)
        }
    }.trim()
}

private fun hasRunError(run: ConversationRun, traceText: String): Boolean {
    val lines = traceText.lines().map { line -> line.trim() }
    val traceErrorType = findTraceValue(lines, "errorType")
    val finalAnswerSource = findTraceValue(lines, "finalAnswerSource")
    return isFailedStatus(run.status) ||
            run.workerResults.any { worker ->
                isFailedStatus(worker.status) || worker.errorType.isNotBlank()
            } ||
            (traceErrorType.isNotBlank() && !traceErrorType.equals("NONE", ignoreCase = true)) ||
            finalAnswerSource.equals("ERROR", ignoreCase = true)
}

private fun buildAnswerOutcomeLabel(status: Any?): String {
    return "답변: ${statusToKorean(status)}"
}

private fun buildWorkerOutcomeLabel(status: Any?): String {
    return "작업: ${statusToKorean(status)}"
}

private fun statusToKorean(status: Any?): String {
    val statusText = status?.toString().orEmpty()
    val normalized = statusText.trim().uppercase()
    return when {
        normalized.contains("SUCCESS") || normalized == "PASSED" || normalized == "COMPLETED" -> "성공"
        normalized.contains("FAIL") || normalized.contains("ERROR") -> "실패"
        normalized.contains("SKIP") -> "건너뜀"
        normalized.contains("RUN") -> "실행중"
        normalized.isBlank() -> "알 수 없음"
        else -> statusText
    }
}

private fun isFailedStatus(status: Any?): Boolean {
    val normalized = status?.toString().orEmpty().trim().uppercase()
    return normalized.contains("FAIL") || normalized.contains("ERROR")
}

private fun isSkippedStatus(status: Any?): Boolean {
    return status?.toString().orEmpty().trim().uppercase().contains("SKIP")
}

private fun buildWorkerRequiredSummary(worker: ConversationWorkerResult): String {
    return "상태: ${statusToKorean(worker.status)} · 지연시간: ${worker.latencySec}s · 재시도: ${worker.retryCount}"
}

private fun findProviderDetailWorkerIndex(run: ConversationRun): Int {
    val failedIndex = run.workerResults.indexOfFirst { worker ->
        !isSkippedStatus(worker.status) && (isFailedStatus(worker.status) || worker.errorType.isNotBlank())
    }
    if (failedIndex >= 0) return failedIndex

    val activeIndex = run.workerResults.indexOfFirst { worker ->
        !isSkippedStatus(worker.status) && worker.providerName.isNotBlank()
    }
    return activeIndex
}

private fun buildWorkerProviderDetailText(
    worker: ConversationWorkerResult,
    index: Int,
    providerDetailWorkerIndex: Int,
    providerResponseSummaryText: String,
    fallbackProviderResponseSummaryText: String
): String {
    val ownProviderSummary = extractTraceSection(worker.rawOutput, "[PROVIDER_RESPONSE_SUMMARY]")
    val ownFallbackSummary = extractTraceSection(worker.rawOutput, "[FALLBACK_PROVIDER_RESPONSE_SUMMARY]")
    if (ownProviderSummary.isNotBlank() || ownFallbackSummary.isNotBlank()) {
        return buildString {
            if (ownProviderSummary.isNotBlank()) {
                appendLine(ownProviderSummary)
            }
            if (ownFallbackSummary.isNotBlank()) {
                if (isNotBlank()) appendLine()
                appendLine("[FALLBACK_PROVIDER_RESPONSE_SUMMARY]")
                appendLine(ownFallbackSummary)
            }
        }.trim()
    }

    if (index != providerDetailWorkerIndex) return ""

    return buildString {
        if (providerResponseSummaryText.isNotBlank()) {
            appendLine(providerResponseSummaryText)
        }
        if (fallbackProviderResponseSummaryText.isNotBlank()) {
            if (isNotBlank()) appendLine()
            appendLine("[FALLBACK_PROVIDER_RESPONSE_SUMMARY]")
            appendLine(fallbackProviderResponseSummaryText)
        }
    }.trim()
}

private fun formatWorkerErrorLabel(
    worker: ConversationWorkerResult,
    providerDetailText: String
): String {
    val errorType = worker.errorType.ifBlank {
        findTraceValue(providerDetailText.lines().map { line -> line.trim() }, "errorType")
    }
    if (errorType.isBlank()) return "없음"
    return formatErrorTypeWithHttp(errorType, providerDetailText)
}

private fun formatErrorTypeWithHttp(
    errorType: String,
    detailText: String
): String {
    val normalized = errorType.trim()
    val explicitHttp = extractHttpStatus(detailText)
    val httpStatus = explicitHttp ?: defaultHttpStatusForErrorType(normalized)
    return if (httpStatus.isNullOrBlank()) {
        normalized
    } else {
        "$normalized (HTTP $httpStatus)"
    }
}

private fun extractHttpStatus(text: String): String? {
    val directHttp = Regex("""(?i)HTTP\s*(\d{3})""").find(text)?.groupValues?.getOrNull(1)
    if (!directHttp.isNullOrBlank()) return directHttp

    val lines = text.lines().map { line -> line.trim() }
    val keys = listOf("httpStatus", "httpStatusCode", "statusCode", "providerErrorCode", "errorCode")
    keys.forEach { key ->
        val value = findTraceValue(lines, key)
        val numeric = Regex("""\d{3}""").find(value)?.value
        if (!numeric.isNullOrBlank()) return numeric
    }
    return null
}

private fun defaultHttpStatusForErrorType(errorType: String): String? {
    return when (errorType.trim().uppercase()) {
        "RATE_LIMIT" -> "429"
        "AUTH_ERROR", "AUTH_REQUIRED", "API_KEY_MISSING" -> "401"
        "SERVER_ERROR" -> "500"
        "TIMEOUT" -> "408"
        else -> null
    }
}

private fun formatErrorBlockDisplayText(text: String): String {
    val cleaned = text.lines()
        .filterNot { line -> line.trim().equals("오류 정보 없음", ignoreCase = true) }
        .joinToString("\n")
        .trim()
    return cleaned.ifBlank { "오류 상세 정보가 비어 있습니다. 실행정보의 Worker 블록을 확인하세요." }
}


@Composable
private fun RunWebSearchGroundingSection(info: WebSearchGroundingDisplayInfo) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "요청: ${info.requestedLabel} · 실행: ${info.executionLabel}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = when (info.finalAnswerSource) {
                "GROUNDING" -> MaterialTheme.colorScheme.primary
                "FALLBACK_GENERAL" -> MaterialTheme.colorScheme.tertiary
                "ERROR" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            }
        )

        Text(
            text = "최종 답변 출처: ${info.finalAnswerSourceLabel}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f)
        )

        Text(
            text = "Grounding 사용: ${info.groundingUsed} · 출처 수: ${info.citationCount} · 검색 쿼리 수: ${info.searchQueryCount}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
        )

        Text(
            text = "Fallback 허용: ${info.fallbackAllowed} · 실행: ${info.fallbackAttempted} · 성공: ${info.fallbackSucceeded}",
            style = MaterialTheme.typography.bodySmall,
            color = if (info.fallbackAttempted) {
                if (info.fallbackSucceeded) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
            }
        )

        Text(
            text = "Grounding 오류: ${normalizeNoneValue(info.groundingErrorType)} · Fallback 오류: ${normalizeNoneValue(info.fallbackErrorType)}",
            style = MaterialTheme.typography.bodySmall,
            color = if (info.groundingErrorType.equals("NONE", ignoreCase = true) && info.fallbackErrorType.equals("NONE", ignoreCase = true)) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            } else {
                MaterialTheme.colorScheme.error
            }
        )
    }
}

@Composable
private fun RunRagSection(ragInfo: RagRunDisplayInfo) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "상태: ${if (ragInfo.enabled) "ON" else "OFF"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (ragInfo.query.isNotBlank()) {
            Text(
                text = "검색어: ${ragInfo.query}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
            )
        }

        Text(
            text = "검색 결과: ${ragInfo.resultCount}개 · 사용 chunk: ${ragInfo.usedChunkCount}개",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
        )

        Text(
            text = "fallback: ${ragInfo.fallback} · fallbackReason: ${ragInfo.fallbackReason ?: "없음"}",
            style = MaterialTheme.typography.bodySmall,
            color = if (ragInfo.fallback) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
            }
        )

        Text(
            text = "maxContextChars: ${ragInfo.maxContextChars}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
        )
    }
}

@Composable
private fun RunCollapsibleFlatTextSection(
    title: String,
    copyText: String,
    text: String
) {
    val clipboardManager = LocalClipboardManager.current
    var isCollapsed by rememberSaveable(title, text) { mutableStateOf(true) }

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

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        )

        if (!isCollapsed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SelectionContainer {
                    Text(
                        text = text,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.18f
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                        softWrap = true
                    )
                }
            }
        }
    }
}

@Composable
private fun RunFlatSection(
    title: String,
    copyText: String,
    initiallyCollapsed: Boolean,
    headerStatusLabel: String? = null,
    content: @Composable () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var isCollapsed by rememberSaveable(title, copyText) { mutableStateOf(initiallyCollapsed) }

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
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!headerStatusLabel.isNullOrBlank()) {
                    Text(
                        text = headerStatusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (headerStatusLabel.contains("실패") || headerStatusLabel.contains("오류")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                        }
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        )

        if (!isCollapsed) {
            content()
        }
    }
}

@Composable
private fun RunSummarySection(run: ConversationRun) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "상태: ${statusToKorean(run.status)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "총 시간: ${run.totalLatencySec}s · Worker: ${run.workerResults.size} · 재시도: ${run.totalRetryCount}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
        )

        if (run.workerResults.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            )

            run.workerResults.forEachIndexed { index, worker ->
                Text(
                    text = "${worker.workerName.ifBlank { "워커 ${index + 1}" }} · ${buildWorkerRequiredSummary(worker)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFailedStatus(worker.status) || worker.errorType.isNotBlank()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
                    }
                )
            }
        }
    }
}

@Composable
private fun RunTraceSection(traceText: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WrappedPlainContent(text = traceText)
    }
}

@Composable
private fun RunWorkerResultSection(
    index: Int,
    worker: ConversationWorkerResult,
    providerDetailText: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
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
            text = buildWorkerRequiredSummary(worker),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
        )

        val errorLabel = formatWorkerErrorLabel(worker, providerDetailText)
        if (errorLabel != "없음") {
            Text(
                text = "오류 유형: $errorLabel",
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

        if (providerDetailText.isNotBlank()) {
            RunCollapsibleFlatTextSection(
                title = "Provider 응답 상세",
                copyText = providerDetailText,
                text = providerDetailText
            )
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
private fun WrappedPlainContent(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 2.dp, vertical = 4.dp)
    ) {
        SelectionContainer {
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall.copy(
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.25f
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                softWrap = true
            )
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
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.38f,
                    letterSpacing = 0.18.sp
                ),
                softWrap = false
            )
        }
    }
}

@Composable
private fun highlightCodeText(text: String): AnnotatedString {
    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.94f)
    val keywordColor = MaterialTheme.colorScheme.primary
    val declarationColor = Color(0xFFFFD166)
    val functionColor = Color(0xFF80CBC4)
    val stringColor = Color(0xFFA5D6A7)
    val numberColor = Color(0xFFFFB74D)
    val booleanColor = Color(0xFFCE93D8)
    val commentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f)
    val symbolColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    val annotationColor = Color(0xFFFFAB91)
    val typeColor = Color(0xFF90CAF9)

    val keywords = setOf(
        "fun", "val", "var", "if", "else", "return", "for", "while", "when",
        "class", "data", "object", "interface", "sealed", "enum", "private", "public",
        "internal", "protected", "override", "import", "package", "try", "catch", "finally",
        "throw", "suspend", "launch", "remember", "by", "in", "is", "as", "break", "continue",
        "this", "super", "new", "const", "let", "function", "def", "from"
    )
    val declarationKeywords = setOf("fun", "class", "data", "object", "interface", "enum", "val", "var")
    val booleansAndNull = setOf("true", "false", "null", "True", "False", "None")
    val commonTypes = setOf(
        "String", "Int", "Long", "Float", "Double", "Boolean", "List", "Map", "Set",
        "Unit", "File", "Context", "ViewModel", "Composable", "MutableState", "State"
    )

    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                char == '/' && index + 1 < text.length && text[index + 1] == '/' -> {
                    val end = text.indexOf('\n', index).let { if (it == -1) text.length else it }
                    withStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)) {
                        append(text.substring(index, end))
                    }
                    index = end
                }

                char == '/' && index + 1 < text.length && text[index + 1] == '*' -> {
                    val end = text.indexOf("*/", index + 2).let { if (it == -1) text.length else it + 2 }
                    withStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)) {
                        append(text.substring(index, end))
                    }
                    index = end
                }

                char == '@' -> {
                    val start = index
                    index += 1
                    while (index < text.length && (text[index].isLetterOrDigit() || text[index] == '_' || text[index] == '.')) {
                        index += 1
                    }
                    withStyle(SpanStyle(color = annotationColor, fontWeight = FontWeight.SemiBold)) {
                        append(text.substring(start, index))
                    }
                }

                char == '"' || char == '\'' -> {
                    val quote = char
                    val start = index
                    index += 1
                    var escaped = false
                    while (index < text.length) {
                        val current = text[index]
                        if (current == quote && !escaped) {
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
                    while (index < text.length && (text[index].isDigit() || text[index] == '.' || text[index] == '_')) {
                        index += 1
                    }
                    withStyle(SpanStyle(color = numberColor, fontWeight = FontWeight.Medium)) {
                        append(text.substring(start, index))
                    }
                }

                char.isLetter() || char == '_' -> {
                    val start = index
                    while (index < text.length && (text[index].isLetterOrDigit() || text[index] == '_')) {
                        index += 1
                    }
                    val word = text.substring(start, index)
                    var lookAhead = index
                    while (lookAhead < text.length && text[lookAhead].isWhitespace()) lookAhead += 1
                    val looksLikeFunction = lookAhead < text.length && text[lookAhead] == '('
                    when {
                        word in declarationKeywords -> withStyle(
                            SpanStyle(color = declarationColor, fontWeight = FontWeight.Bold)
                        ) { append(word) }

                        word in keywords -> withStyle(
                            SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold)
                        ) { append(word) }

                        word in booleansAndNull -> withStyle(
                            SpanStyle(color = booleanColor, fontWeight = FontWeight.SemiBold)
                        ) { append(word) }

                        word in commonTypes || word.firstOrNull()?.isUpperCase() == true -> withStyle(
                            SpanStyle(color = typeColor, fontWeight = FontWeight.Medium)
                        ) { append(word) }

                        looksLikeFunction -> withStyle(
                            SpanStyle(color = functionColor, fontWeight = FontWeight.SemiBold)
                        ) { append(word) }

                        else -> withStyle(SpanStyle(color = baseColor)) { append(word) }
                    }
                }

                char in "{}[]().,;:<>+-=*/!&|%" -> {
                    withStyle(SpanStyle(color = symbolColor, fontWeight = FontWeight.Medium)) {
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
    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.94f)
    val keyColor = MaterialTheme.colorScheme.primary
    val stringColor = Color(0xFFA5D6A7)
    val numberColor = Color(0xFFFFB74D)
    val boolColor = Color(0xFFCE93D8)
    val nullColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
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
                    withStyle(
                        SpanStyle(
                            color = if (isKey) keyColor else stringColor,
                            fontWeight = if (isKey) FontWeight.Bold else FontWeight.Normal
                        )
                    ) {
                        append(text.substring(start, index.coerceAtMost(text.length)))
                    }
                }

                char.isDigit() || char == '-' -> {
                    val start = index
                    index += 1
                    while (index < text.length && (text[index].isDigit() || text[index] == '.' || text[index] == 'e' || text[index] == 'E' || text[index] == '+' || text[index] == '-')) {
                        index += 1
                    }
                    withStyle(SpanStyle(color = numberColor, fontWeight = FontWeight.Medium)) {
                        append(text.substring(start, index))
                    }
                }

                text.startsWith("true", index) || text.startsWith("false", index) || text.startsWith("null", index) -> {
                    val token = when {
                        text.startsWith("true", index) -> "true"
                        text.startsWith("false", index) -> "false"
                        else -> "null"
                    }
                    val color = if (token == "null") nullColor else boolColor
                    withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) {
                        append(token)
                    }
                    index += token.length
                }

                char in "{}[],:" -> {
                    withStyle(SpanStyle(color = symbolColor, fontWeight = FontWeight.Medium)) {
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
