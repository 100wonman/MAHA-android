package com.maha.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun ConversationAssistantTextRenderer(
    text: String,
    modifier: Modifier = Modifier
) {
    val displaySegments = remember(text) { parseConversationDisplaySegments(text) }
    val renderSegments = if (displaySegments.isEmpty()) {
        listOf(
            ConversationDisplaySegment(
                type = ConversationOutputBlockType.TEXT_BLOCK,
                title = "",
                content = text
            )
        )
    } else {
        displaySegments
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        renderSegments.forEach { segment ->
            when (segment.type) {
                ConversationOutputBlockType.TABLE_BLOCK -> {
                    val rows = parseConversationAssistantMarkdownTableRows(segment.content)
                    if (rows.isNotEmpty()) {
                        ConversationAssistantMarkdownTable(rows = rows)
                    }
                }

                ConversationOutputBlockType.TEXT_BLOCK,
                ConversationOutputBlockType.MARKDOWN_BLOCK -> {
                    ConversationAssistantTextSegments(text = segment.content)
                }

                else -> {
                    if (segment.content.isNotBlank()) {
                        ConversationAssistantPlainParagraph(text = segment.content)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationAssistantTextSegments(
    text: String
) {
    val sections = remember(text) { splitConversationAssistantTextSections(text) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        sections.forEach { section ->
            when (section) {
                is AssistantTextSection.Heading -> ConversationAssistantHeading(
                    text = section.text,
                    level = section.level
                )

                is AssistantTextSection.Quote -> ConversationAssistantQuote(text = section.text)
                is AssistantTextSection.Paragraph -> ConversationAssistantPlainParagraph(text = section.text)
            }
        }
    }
}

@Composable
private fun ConversationAssistantPlainParagraph(
    text: String
) {
    if (text.isBlank()) return

    SelectionContainer {
        Text(
            text = buildConversationAssistantAnnotatedText(text),
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
private fun ConversationAssistantHeading(
    text: String,
    level: Int
) {
    if (text.isBlank()) return

    val style = when (level) {
        1 -> MaterialTheme.typography.headlineSmall
        2 -> MaterialTheme.typography.titleLarge
        3 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }

    SelectionContainer {
        Text(
            text = buildConversationAssistantAnnotatedText(text),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            style = style.copy(
                fontWeight = FontWeight.Bold,
                lineHeight = style.lineHeight * 1.18f
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ConversationAssistantQuote(
    text: String
) {
    if (text.isBlank()) return

    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val backgroundColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .background(borderColor, RoundedCornerShape(999.dp))
                .padding(vertical = 1.dp)
        )

        SelectionContainer {
            Text(
                text = buildConversationAssistantAnnotatedText(text),
                modifier = Modifier
                    .weight(1f)
                    .background(backgroundColor, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.22f
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
            )
        }
    }
}

@Composable
private fun ConversationAssistantMarkdownTable(
    rows: List<List<String>>
) {
    val normalizedRows = remember(rows) { normalizeConversationAssistantTableRows(rows) }
    if (normalizedRows.isEmpty()) return

    val horizontalScrollState = rememberScrollState()
    val columnWidths = remember(normalizedRows) {
        conversationAssistantTableColumnWidths(normalizedRows.firstOrNull()?.size ?: 0)
    }

    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)
    val tableBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
    val headerBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.090f)
    val oddRowBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.032f)
    val evenRowBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.018f)
    val tableBackground = conversationUnifiedCardColor().copy(alpha = 0.18f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (normalizedRows.firstOrNull().orEmpty().size > 2) {
            Text(
                text = "표 · 좌우로 밀어서 전체 열 보기",
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(horizontalScrollState)
                .background(tableBackground, RoundedCornerShape(12.dp))
                .border(1.dp, tableBorderColor, RoundedCornerShape(12.dp))
                .padding(1.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            normalizedRows.forEachIndexed { rowIndex, cells ->
                val rowBackground = when {
                    rowIndex == 0 -> headerBackground
                    rowIndex % 2 == 0 -> evenRowBackground
                    else -> oddRowBackground
                }

                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.Top
                ) {
                    cells.forEachIndexed { cellIndex, cell ->
                        ConversationAssistantTableCell(
                            text = cell,
                            isHeader = rowIndex == 0,
                            isFirstColumn = cellIndex == 0,
                            width = columnWidths.getOrElse(cellIndex) { 190.dp },
                            borderColor = borderColor,
                            backgroundColor = rowBackground
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationAssistantTableCell(
    text: String,
    isHeader: Boolean,
    isFirstColumn: Boolean,
    width: Dp,
    borderColor: Color,
    backgroundColor: Color
) {
    val textStyle = when {
        isHeader -> MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
        isFirstColumn -> MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            lineHeight = MaterialTheme.typography.labelMedium.lineHeight * 1.14f
        )
        else -> MaterialTheme.typography.bodySmall.copy(
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.20f
        )
    }

    val textAlpha = when {
        isHeader -> 0.98f
        isFirstColumn -> 0.92f
        else -> 0.86f
    }

    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .border(0.6.dp, borderColor)
            .background(backgroundColor)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        contentAlignment = Alignment.TopStart
    ) {
        SelectionContainer {
            Text(
                text = buildConversationAssistantAnnotatedText(
                    normalizeConversationAssistantDisplayText(text)
                ),
                style = textStyle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha)
            )
        }
    }
}

private fun normalizeConversationAssistantTableRows(
    rows: List<List<String>>
): List<List<String>> {
    val columnCount = rows.maxOfOrNull { row -> row.size } ?: 0
    if (columnCount < 2) return emptyList()

    return rows
        .map { row ->
            row.map { cell -> normalizeConversationAssistantDisplayText(cell) }
                .let { cells ->
                    if (cells.size >= columnCount) {
                        cells
                    } else {
                        cells + List(columnCount - cells.size) { "" }
                    }
                }
        }
        .filter { row -> row.any { cell -> cell.isNotBlank() } }
}

private fun conversationAssistantTableColumnWidths(
    columnCount: Int
): List<Dp> {
    return when {
        columnCount <= 0 -> emptyList()
        columnCount == 1 -> listOf(320.dp)
        columnCount == 2 -> listOf(124.dp, 320.dp)
        columnCount == 3 -> listOf(112.dp, 230.dp, 230.dp)
        columnCount == 4 -> listOf(112.dp, 220.dp, 220.dp, 180.dp)
        else -> List(columnCount) { index ->
            if (index == 0) 112.dp else 190.dp
        }
    }
}

private sealed interface AssistantTextSection {
    data class Heading(val level: Int, val text: String) : AssistantTextSection
    data class Quote(val text: String) : AssistantTextSection
    data class Paragraph(val text: String) : AssistantTextSection
}

private fun splitConversationAssistantTextSections(
    text: String
): List<AssistantTextSection> {
    val result = mutableListOf<AssistantTextSection>()
    val paragraphBuffer = mutableListOf<String>()
    val quoteBuffer = mutableListOf<String>()

    fun flushParagraph() {
        val content = paragraphBuffer.joinToString("\n").trim()
        if (content.isNotBlank()) {
            result.add(AssistantTextSection.Paragraph(content))
        }
        paragraphBuffer.clear()
    }

    fun flushQuote() {
        val content = quoteBuffer.joinToString("\n").trim()
        if (content.isNotBlank()) {
            result.add(AssistantTextSection.Quote(content))
        }
        quoteBuffer.clear()
    }

    text.lines().forEach { rawLine ->
        val line = rawLine.trimEnd()
        val headingMatch = Regex("^(#{1,6})\\s+(.+)$").matchEntire(line.trim())
        val quoteMatch = Regex("^>\\s?(.*)$").matchEntire(line.trim())

        when {
            headingMatch != null -> {
                flushParagraph()
                flushQuote()
                result.add(
                    AssistantTextSection.Heading(
                        level = headingMatch.groupValues[1].length,
                        text = headingMatch.groupValues[2].trim()
                    )
                )
            }

            quoteMatch != null -> {
                flushParagraph()
                quoteBuffer.add(quoteMatch.groupValues[1].trim())
            }

            line.isBlank() -> {
                flushParagraph()
                flushQuote()
            }

            else -> {
                flushQuote()
                paragraphBuffer.add(line)
            }
        }
    }

    flushParagraph()
    flushQuote()
    return result
}

private fun parseConversationAssistantMarkdownTableRows(
    text: String
): List<List<String>> {
    return text
        .lines()
        .map { line -> line.trim() }
        .filter { line -> line.isNotBlank() }
        .filterNot { line -> isConversationAssistantMarkdownTableSeparator(line) }
        .map { line ->
            line
                .trim('|')
                .split('|')
                .map { cell -> normalizeConversationAssistantDisplayText(cell) }
                .filter { cell -> cell.isNotBlank() }
        }
        .filter { cells -> cells.size >= 2 }
}

private fun isConversationAssistantMarkdownTableSeparator(
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

private fun buildConversationAssistantAnnotatedText(
    text: String
): AnnotatedString {
    val normalized = normalizeConversationAssistantDisplayText(text)
    val boldPattern = Regex("(\\*\\*|__)(.+?)\\1")
    return buildAnnotatedString {
        var cursor = 0
        boldPattern.findAll(normalized).forEach { match ->
            if (match.range.first > cursor) {
                append(normalized.substring(cursor, match.range.first))
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[2])
            }
            cursor = match.range.last + 1
        }
        if (cursor < normalized.length) {
            append(normalized.substring(cursor))
        }
    }
}

private fun normalizeConversationAssistantDisplayText(
    value: String
): String {
    return value
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("\\$\\\\rightarrow\\$"), "→")
        .replace(Regex("\\\\rightarrow"), "→")
        .trim()
}
