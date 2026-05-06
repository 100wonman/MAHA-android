package com.maha.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationOutputBlockCard(
    block: ConversationOutputBlock,
    role: ConversationRole = ConversationRole.ASSISTANT,
    sentAt: String = "",
    canEdit: Boolean = false,
    onEditRequest: (() -> Unit)? = null,
    onUnsupportedEditRequest: (() -> Unit)? = null
) {
    val isUserBlock = role == ConversationRole.USER
    val shouldUseCard = isUserBlock || shouldRenderAsStructuredBlock(block)
    val isLongMessage = shouldUseMessagePreview(block.content)
    val blockContainerColor = if (isUserBlock) {
        ConversationMessageTokens.userBackground
    } else {
        ConversationMessageTokens.assistantBackground
    }
    val blockTextColor = MaterialTheme.colorScheme.onSurface
    val clipboardManager = LocalClipboardManager.current

    var isExpanded by rememberSaveable("${block.blockId}_expanded") {
        mutableStateOf(
            when {
                isLongMessage -> false
                block.collapsed -> false
                else -> true
            }
        )
    }
    var isMenuOpen by remember { mutableStateOf(false) }

    val showPreview = isLongMessage && !isExpanded

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val userMaxWidth = maxWidth * 0.70f
            val outerModifier = Modifier.fillMaxWidth()
            val messageModifier = if (isUserBlock) {
                Modifier
                    .align(Alignment.CenterStart)
                    .widthIn(max = userMaxWidth)
            } else {
                Modifier.fillMaxWidth()
            }

            Box(
                modifier = outerModifier,
                contentAlignment = Alignment.CenterStart
            ) {
                if (shouldUseCard) {
                    ConversationMessageCard(
                        modifier = messageModifier.combinedClickable(
                            onClick = {
                                if (isLongMessage || block.collapsed) {
                                    isExpanded = !isExpanded
                                }
                            },
                            onLongClick = {
                                isMenuOpen = true
                            }
                        ),
                        role = if (isUserBlock) {
                            ConversationMessageVisualRole.USER
                        } else {
                            ConversationMessageVisualRole.ASSISTANT
                        }
                    ) {
                        StructuredBlockHeader(
                            block = block,
                            showTitle = !isUserBlock,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(block.content))
                            }
                        )

                        ConversationBlockContent(
                            content = block.content,
                            blockType = block.type,
                            isUserBlock = isUserBlock,
                            showPreview = showPreview,
                            blockContainerColor = blockContainerColor,
                            blockTextColor = blockTextColor,
                            fillWidth = true
                        )
                    }
                } else {
                    Box(
                        modifier = messageModifier.combinedClickable(
                            onClick = {
                                if (isLongMessage) {
                                    isExpanded = !isExpanded
                                }
                            },
                            onLongClick = {
                                isMenuOpen = true
                            }
                        ),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        ConversationBlockContent(
                            content = block.content,
                            blockType = block.type,
                            isUserBlock = isUserBlock,
                            showPreview = showPreview,
                            blockContainerColor = MaterialTheme.colorScheme.background,
                            blockTextColor = blockTextColor,
                            fillWidth = !isUserBlock
                        )
                    }
                }
            }
        }
    }

    if (isMenuOpen) {
        ConversationBlockActionDialog(
            sentAt = sentAt,
            onDismiss = {
                isMenuOpen = false
            },
            onCopy = {
                clipboardManager.setText(AnnotatedString(block.content))
                isMenuOpen = false
            },
            onTextSelect = {
                isMenuOpen = false
            },
            canEdit = canEdit,
            onEdit = {
                isMenuOpen = false
                if (canEdit) {
                    onEditRequest?.invoke()
                } else {
                    onUnsupportedEditRequest?.invoke()
                }
            },
            onShare = {
                isMenuOpen = false
            }
        )
    }
}

@Composable
private fun StructuredBlockHeader(
    block: ConversationOutputBlock,
    showTitle: Boolean,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showTitle) {
            Text(
                text = buildConversationBlockHeader(block),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
                modifier = Modifier.weight(1f)
            )
        } else {
            Box(modifier = Modifier.weight(1f))
        }

        ConversationIconActionButton(
            onClick = onCopy,
            modifier = Modifier
                .height(28.dp)
                .widthIn(min = 28.dp)
        ) {
            Text(
                text = "⧉",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = ConversationColors.actionText
            )
        }
    }
}

@Composable
private fun ConversationBlockContent(
    content: String,
    blockType: ConversationOutputBlockType,
    isUserBlock: Boolean,
    showPreview: Boolean,
    blockContainerColor: Color,
    blockTextColor: Color,
    fillWidth: Boolean
) {
    Box(
        modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier
    ) {
        val tableRows = remember(content, blockType) {
            if (blockType == ConversationOutputBlockType.TABLE_BLOCK) {
                parseConversationTableRows(content)
            } else {
                emptyList()
            }
        }
        val inlineSegments = remember(content, blockType) {
            if (blockType == ConversationOutputBlockType.TEXT_BLOCK || blockType == ConversationOutputBlockType.MARKDOWN_BLOCK) {
                parseConversationDisplaySegments(content)
            } else {
                emptyList()
            }
        }
        val hasInlineTableSegment = inlineSegments.any { segment ->
            segment.type == ConversationOutputBlockType.TABLE_BLOCK && parseConversationTableRows(segment.content).isNotEmpty()
        }
        val shouldRenderInlineStructuredContent = !showPreview && hasInlineTableSegment

        if (!isUserBlock && tableRows.isNotEmpty()) {
            ConversationTableBlock(
                rows = if (showPreview) tableRows.take(5) else tableRows,
                textColor = blockTextColor
            )
        } else if (!isUserBlock && shouldRenderInlineStructuredContent) {
            ConversationInlineStructuredTextBlock(
                segments = inlineSegments,
                textColor = blockTextColor,
                fillWidth = fillWidth
            )
        } else {
            Text(
                text = normalizeConversationDisplayText(content),
                style = MaterialTheme.typography.bodySmall,
                color = blockTextColor,
                maxLines = if (showPreview) 5 else Int.MAX_VALUE,
                textAlign = TextAlign.Start,
                modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier
            )
        }

        if (showPreview) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                blockContainerColor.copy(alpha = 0.00f),
                                blockContainerColor.copy(alpha = 0.86f),
                                blockContainerColor
                            )
                        )
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = "⌄",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = blockTextColor,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ConversationInlineStructuredTextBlock(
    segments: List<ConversationDisplaySegment>,
    textColor: Color,
    fillWidth: Boolean
) {
    Column(
        modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        segments.forEach { segment ->
            if (segment.type == ConversationOutputBlockType.TABLE_BLOCK) {
                val rows = parseConversationTableRows(segment.content)
                if (rows.isNotEmpty()) {
                    ConversationTableBlock(
                        rows = rows,
                        textColor = textColor
                    )
                }
            } else if (segment.content.isNotBlank()) {
                Text(
                    text = normalizeConversationDisplayText(segment.content),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                    textAlign = TextAlign.Start,
                    modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier
                )
            }
        }
    }
}

@Composable
private fun ConversationTableBlock(
    rows: List<ConversationTableRow>,
    textColor: Color
) {
    val header = rows.firstOrNull()
    val bodyRows = rows.drop(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = ConversationBorders.subtleBorder,
                shape = RoundedCornerShape(ConversationShapes.structuredBlockRadius)
            )
    ) {
        if (header != null) {
            ConversationTableRowView(
                row = header,
                textColor = textColor,
                isHeader = true
            )
        }

        bodyRows.forEachIndexed { index, row ->
            ConversationTableDivider()
            ConversationTableRowView(
                row = row,
                textColor = textColor,
                isHeader = false
            )
        }
    }
}

@Composable
private fun ConversationTableRowView(
    row: ConversationTableRow,
    textColor: Color,
    isHeader: Boolean
) {
    if (row.cells.size == 2) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val keyColumnWidth = when {
                maxWidth < 360.dp -> 116.dp
                maxWidth < 520.dp -> 136.dp
                else -> maxWidth * 0.34f
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.Top
            ) {
                ConversationTableCellText(
                    text = row.cells[0],
                    textColor = textColor,
                    emphasize = true,
                    modifier = Modifier.width(keyColumnWidth)
                )

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(ConversationBorders.subtleBorder.copy(alpha = 0.56f))
                )

                ConversationTableCellText(
                    text = row.cells[1],
                    textColor = textColor,
                    emphasize = isHeader,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.Top
    ) {
        row.cells.forEachIndexed { index, cell ->
            ConversationTableCellText(
                text = cell,
                textColor = textColor,
                emphasize = isHeader || index == 0,
                modifier = Modifier.weight(1f)
            )

            if (index < row.cells.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(ConversationBorders.subtleBorder.copy(alpha = 0.56f))
                )
            }
        }
    }
}

@Composable
private fun ConversationTableCellText(
    text: String,
    textColor: Color,
    emphasize: Boolean,
    modifier: Modifier
) {
    Text(
        text = normalizeConversationDisplayText(text),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
        color = if (emphasize) {
            textColor.copy(alpha = 0.92f)
        } else {
            textColor.copy(alpha = 0.84f)
        },
        modifier = modifier.padding(horizontal = 8.dp, vertical = 8.dp)
    )
}

@Composable
private fun ConversationTableDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ConversationBorders.subtleBorder.copy(alpha = 0.72f))
    )
}

private data class ConversationTableRow(
    val cells: List<String>
)

private fun parseConversationTableRows(
    content: String
): List<ConversationTableRow> {
    val lines = content
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }

    if (lines.isEmpty()) {
        return emptyList()
    }

    val parsedRows = when {
        lines.any { it.contains("|") } -> parsePipeTableRows(lines)
        lines.any { it.contains("\t") } -> parseDelimitedTableRows(lines) { line ->
            line.split("\t")
        }
        else -> parseDelimitedTableRows(lines) { line ->
            Regex("\\s{2,}").split(line, limit = 2)
        }
    }

    val normalizedRows = parsedRows
        .map { cells ->
            cells
                .map { normalizeConversationDisplayText(it) }
                .filter { it.isNotBlank() }
        }
        .filter { it.size >= 2 }

    if (normalizedRows.size < 2) {
        return emptyList()
    }

    return normalizedRows.map { cells -> ConversationTableRow(cells) }
}

private fun parsePipeTableRows(
    lines: List<String>
): List<List<String>> {
    return lines
        .filterNot { isMarkdownTableSeparator(it) }
        .map { line ->
            line
                .trim()
                .trim('|')
                .split("|")
                .map { it.trim() }
        }
}

private fun parseDelimitedTableRows(
    lines: List<String>,
    splitter: (String) -> List<String>
): List<List<String>> {
    return lines.map { line -> splitter(line).map { it.trim() } }
}

private fun isMarkdownTableSeparator(
    line: String
): Boolean {
    val normalized = line.trim().trim('|').trim()
    if (normalized.isBlank()) {
        return false
    }
    return normalized
        .split('|')
        .map { it.trim() }
        .all { cell -> cell.matches(Regex(":?-{3,}:?")) }
}

private fun normalizeConversationDisplayText(
    value: String
): String {
    return value
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("\\*\\*(.*?)\\*\\*")) { match -> match.groupValues[1] }
        .replace(Regex("__(.*?)__")) { match -> match.groupValues[1] }
        .trim()
}

@Composable
private fun ConversationBlockActionDialog(
    sentAt: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onTextSelect: () -> Unit,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onShare: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss
    ) {
        ConversationDialogSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            title = sentAt.ifBlank { "알 수 없음" }
        ) {
            ConversationDialogActionText(
                text = "복사",
                onClick = onCopy
            )

            ConversationDialogActionText(
                text = "텍스트 선택",
                onClick = onTextSelect
            )

            ConversationDialogActionText(
                text = if (canEdit) "메시지 편집" else "메시지 편집 (추후 지원)",
                onClick = onEdit
            )

            ConversationDialogActionText(
                text = "공유",
                onClick = onShare
            )
        }
    }
}

@Composable
internal fun ConversationDialogActionText(
    text: String,
    onClick: () -> Unit
) {
    ConversationTextActionButton(
        text = text,
        onClick = onClick
    )
}


@Composable
internal fun conversationUnifiedCardColor(): Color {
    return ConversationSurfaces.cardBackground
}

@Composable
internal fun conversationUnifiedCardShape() = RoundedCornerShape(ConversationShapes.cardRadius)

internal fun buildConversationBlockTypeLabel(
    block: ConversationOutputBlock
): String {
    return when (block.type) {
        ConversationOutputBlockType.TEXT_BLOCK -> "TEXT"
        ConversationOutputBlockType.MARKDOWN_BLOCK -> "MD"
        ConversationOutputBlockType.CODE_BLOCK -> {
            if (block.language.isBlank()) {
                "CODE"
            } else {
                "CODE · ${block.language}"
            }
        }

        ConversationOutputBlockType.TABLE_BLOCK -> "TABLE"
        ConversationOutputBlockType.JSON_BLOCK -> "JSON"
        ConversationOutputBlockType.ERROR_BLOCK -> "ERROR"
        ConversationOutputBlockType.TRACE_BLOCK -> "TRACE"
        ConversationOutputBlockType.MEMORY_BLOCK -> "MEMORY"
    }
}

private fun buildConversationBlockHeader(
    block: ConversationOutputBlock
): String {
    val typeLabel = buildConversationBlockTypeLabel(block)

    return if (block.title.isBlank()) {
        typeLabel
    } else {
        "$typeLabel · ${block.title}"
    }
}

private fun shouldRenderAsStructuredBlock(
    block: ConversationOutputBlock
): Boolean {
    return when (block.type) {
        ConversationOutputBlockType.CODE_BLOCK,
        ConversationOutputBlockType.JSON_BLOCK,
        ConversationOutputBlockType.TABLE_BLOCK,
        ConversationOutputBlockType.ERROR_BLOCK,
        ConversationOutputBlockType.TRACE_BLOCK,
        ConversationOutputBlockType.MEMORY_BLOCK -> true

        ConversationOutputBlockType.TEXT_BLOCK -> isStructuredText(block.content)
        ConversationOutputBlockType.MARKDOWN_BLOCK -> isStructuredText(block.content)
    }
}

private fun isStructuredText(
    content: String
): Boolean {
    val trimmed = content.trim()
    return trimmed.contains("\n") ||
            trimmed.startsWith("-") ||
            trimmed.startsWith("*") ||
            trimmed.startsWith("#") ||
            trimmed.startsWith("{") ||
            trimmed.startsWith("[")
}

private fun shouldUseMessagePreview(
    content: String
): Boolean {
    return content.lines().size > 5 || content.length > 180
}
