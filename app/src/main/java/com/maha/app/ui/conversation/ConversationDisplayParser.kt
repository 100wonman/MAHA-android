package com.maha.app

import org.json.JSONArray
import org.json.JSONObject

private data class StructuredAnswerSegment(
    val type: ConversationOutputBlockType,
    val title: String,
    val content: String
)

internal fun expandAssistantStructuredBlocks(
    blocks: List<ConversationOutputBlock>
): List<ConversationOutputBlock> {
    return blocks.flatMap { block ->
        when (block.type.name) {
            "TEXT_BLOCK", "MARKDOWN_BLOCK" -> splitAssistantTextLikeBlock(block)
            else -> listOf(block)
        }
    }
}

private fun splitAssistantTextLikeBlock(
    block: ConversationOutputBlock
): List<ConversationOutputBlock> {
    val segments = parseStructuredAnswerSegments(block.content)
    if (segments.isEmpty()) return listOf(block)

    val onlyTextSegment = segments.size == 1 &&
            segments.first().type.name in setOf("TEXT_BLOCK", "MARKDOWN_BLOCK") &&
            segments.first().content.trim() == block.content.trim()
    if (onlyTextSegment) return listOf(block)

    return segments.mapIndexed { index, segment ->
        ConversationOutputBlock(
            blockId = "${block.blockId}_structured_$index",
            type = segment.type,
            title = segment.title,
            content = segment.content,
            collapsed = false
        )
    }
}

private fun parseStructuredAnswerSegments(text: String): List<StructuredAnswerSegment> {
    if (text.isBlank()) return emptyList()

    val segments = mutableListOf<StructuredAnswerSegment>()
    val fencedPattern = Regex("""(?s)```([A-Za-z0-9_+.#-]*)[ \t]*(?:\r?\n)(.*?)(?:\r?\n)?```""")
    var cursor = 0

    fencedPattern.findAll(text).forEach { match ->
        val before = text.substring(cursor, match.range.first)
        segments.addAll(parsePlainStructuredSegments(before))

        val language = match.groupValues.getOrNull(1).orEmpty().trim()
        val codeContent = match.groupValues.getOrNull(2).orEmpty().trim('\n', '\r')
        if (codeContent.isNotBlank()) {
            val languageLower = language.lowercase()
            val blockType = if (languageLower == "json" || (language.isBlank() && isValidJsonText(codeContent))) {
                ConversationOutputBlockType.JSON_BLOCK
            } else {
                ConversationOutputBlockType.CODE_BLOCK
            }
            val title = when (blockType.name) {
                "JSON_BLOCK" -> "json"
                else -> language.ifBlank { "code" }
            }
            segments.add(
                StructuredAnswerSegment(
                    type = blockType,
                    title = title,
                    content = codeContent
                )
            )
        }
        cursor = match.range.last + 1
    }

    if (cursor < text.length) {
        segments.addAll(parsePlainStructuredSegments(text.substring(cursor)))
    }

    return mergeAdjacentTextSegments(segments)
}

private fun parsePlainStructuredSegments(text: String): List<StructuredAnswerSegment> {
    if (text.isBlank()) return emptyList()

    val tableSplitSegments = splitMarkdownTableSegments(text)
    val result = mutableListOf<StructuredAnswerSegment>()

    tableSplitSegments.forEach { segment ->
        if (segment.type.name == "TEXT_BLOCK") {
            result.addAll(splitJsonSegments(segment.content))
        } else {
            result.add(segment)
        }
    }

    return result
}

private fun splitMarkdownTableSegments(text: String): List<StructuredAnswerSegment> {
    val lines = text.lines()
    val result = mutableListOf<StructuredAnswerSegment>()
    val textBuffer = mutableListOf<String>()
    var index = 0

    fun flushTextBuffer() {
        val content = textBuffer.joinToString("\n").trim()
        if (content.isNotBlank()) {
            result.add(
                StructuredAnswerSegment(
                    type = ConversationOutputBlockType.TEXT_BLOCK,
                    title = "",
                    content = content
                )
            )
        }
        textBuffer.clear()
    }

    while (index < lines.size) {
        if (isMarkdownTableStart(lines, index)) {
            flushTextBuffer()
            val tableLines = mutableListOf<String>()
            var cursor = index
            while (cursor < lines.size && lines[cursor].contains("|") && lines[cursor].trim().isNotBlank()) {
                tableLines.add(lines[cursor].trim())
                cursor += 1
            }
            val tableText = tableLines.joinToString("\n").trim()
            if (tableText.isNotBlank()) {
                result.add(
                    StructuredAnswerSegment(
                        type = ConversationOutputBlockType.TABLE_BLOCK,
                        title = "Table",
                        content = tableText
                    )
                )
            }
            index = cursor
        } else {
            textBuffer.add(lines[index])
            index += 1
        }
    }

    flushTextBuffer()
    return result
}

private fun splitJsonSegments(text: String): List<StructuredAnswerSegment> {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return emptyList()
    if (isValidJsonText(trimmed)) {
        return listOf(
            StructuredAnswerSegment(
                type = ConversationOutputBlockType.JSON_BLOCK,
                title = "json",
                content = trimmed
            )
        )
    }

    val lines = text.lines()
    val result = mutableListOf<StructuredAnswerSegment>()
    val textBuffer = mutableListOf<String>()
    var index = 0

    fun flushTextBuffer() {
        val content = textBuffer.joinToString("\n").trim()
        if (content.isNotBlank()) {
            result.add(
                StructuredAnswerSegment(
                    type = ConversationOutputBlockType.TEXT_BLOCK,
                    title = "",
                    content = content
                )
            )
        }
        textBuffer.clear()
    }

    while (index < lines.size) {
        val line = lines[index]
        val lineTrimmed = line.trimStart()
        if (lineTrimmed.startsWith("{") || lineTrimmed.startsWith("[")) {
            val jsonLines = mutableListOf<String>()
            var cursor = index
            var candidate = ""
            var foundJson = false

            while (cursor < lines.size) {
                jsonLines.add(lines[cursor])
                candidate = jsonLines.joinToString("\n").trim()
                if (isJsonBracketBalanced(candidate) && isValidJsonText(candidate)) {
                    foundJson = true
                    break
                }
                cursor += 1
            }

            if (foundJson) {
                flushTextBuffer()
                result.add(
                    StructuredAnswerSegment(
                        type = ConversationOutputBlockType.JSON_BLOCK,
                        title = "json",
                        content = candidate
                    )
                )
                index = cursor + 1
            } else {
                textBuffer.add(line)
                index += 1
            }
        } else {
            textBuffer.add(line)
            index += 1
        }
    }

    flushTextBuffer()
    return result
}

private fun mergeAdjacentTextSegments(
    segments: List<StructuredAnswerSegment>
): List<StructuredAnswerSegment> {
    val result = mutableListOf<StructuredAnswerSegment>()
    segments.forEach { segment ->
        if (segment.content.isBlank()) return@forEach
        val previous = result.lastOrNull()
        if (previous != null && previous.type.name == "TEXT_BLOCK" && segment.type.name == "TEXT_BLOCK") {
            result[result.lastIndex] = previous.copy(
                content = listOf(previous.content, segment.content)
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")
                    .trim()
            )
        } else {
            result.add(segment.copy(content = segment.content.trim()))
        }
    }
    return result
}

private fun isMarkdownTableStart(lines: List<String>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    val header = lines[index].trim()
    val separator = lines[index + 1].trim()
    return isPotentialMarkdownTableRow(header) && isMarkdownTableSeparatorRow(separator)
}

private fun isPotentialMarkdownTableRow(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains("|")) return false
    return trimmed.count { it == '|' } >= 2
}

private fun isMarkdownTableSeparatorRow(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains("|")) return false
    val cells = trimmed
        .trim('|')
        .split('|')
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (cells.size < 2) return false
    return cells.all { cell -> cell.matches(Regex(""":?-{3,}:?""")) }
}

private fun isValidJsonText(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return false
    return try {
        when {
            trimmed.startsWith("{") && trimmed.endsWith("}") -> {
                JSONObject(trimmed)
                true
            }
            trimmed.startsWith("[") && trimmed.endsWith("]") -> {
                JSONArray(trimmed)
                true
            }
            else -> false
        }
    } catch (_: Throwable) {
        false
    }
}

private fun isJsonBracketBalanced(text: String): Boolean {
    var curly = 0
    var square = 0
    var inString = false
    var escape = false

    text.forEach { char ->
        when {
            escape -> escape = false
            char == '\\' && inString -> escape = true
            char == '"' -> inString = !inString
            !inString && char == '{' -> curly += 1
            !inString && char == '}' -> curly -= 1
            !inString && char == '[' -> square += 1
            !inString && char == ']' -> square -= 1
        }
        if (curly < 0 || square < 0) return false
    }

    return !inString && curly == 0 && square == 0
}
