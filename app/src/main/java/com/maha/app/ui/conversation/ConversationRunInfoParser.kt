package com.maha.app

internal data class RagRunDisplayInfo(
    val present: Boolean,
    val enabled: Boolean,
    val query: String,
    val resultCount: Int,
    val usedChunkCount: Int,
    val maxContextChars: Int,
    val fallback: Boolean,
    val fallbackReason: String?,
    val contextText: String
)

internal data class WebSearchSourceDisplayInfo(
    val index: Int,
    val title: String,
    val url: String,
    val snippet: String
)

internal data class WebSearchGroundingDisplayInfo(
    val present: Boolean,
    val requested: Boolean,
    val groundingExecuted: Boolean,
    val groundingUsed: Boolean,
    val citationCount: Int,
    val searchQueryCount: Int,
    val fallbackAllowed: Boolean,
    val fallbackAttempted: Boolean,
    val fallbackSucceeded: Boolean,
    val groundingErrorType: String,
    val fallbackErrorType: String,
    val finalAnswerSource: String
) {
    val requestedLabel: String
        get() = if (requested) "ON" else "OFF"

    val executionLabel: String
        get() = when {
            !requested -> "미요청"
            !groundingExecuted -> "미실행"
            finalAnswerSource == "GROUNDING" -> "성공"
            else -> "실패"
        }

    val finalAnswerSourceLabel: String
        get() = when (finalAnswerSource) {
            "GROUNDING" -> "Web Search grounding"
            "FALLBACK_GENERAL" -> "일반 Gemini fallback"
            "ERROR" -> "오류"
            else -> finalAnswerSource.ifBlank { "알 수 없음" }
        }
}

internal data class WebSearchSourcesDisplayInfo(
    val present: Boolean,
    val requested: Boolean,
    val groundingExecuted: Boolean,
    val groundingUsed: Boolean,
    val citationCount: Int,
    val searchQueryCount: Int,
    val sources: List<WebSearchSourceDisplayInfo>
) {
    val shouldDisplay: Boolean
        get() = present && requested && (sources.isNotEmpty() || groundingExecuted)
}

internal fun parseRagRunInfo(traceText: String): RagRunDisplayInfo {
    val contextText = extractBetweenMarkers(
        text = traceText,
        startMarker = "[RAG_CONTEXT_BEGIN]",
        endMarker = "[RAG_CONTEXT_END]"
    )
    val withoutContext = removeBetweenMarkers(
        text = traceText,
        startMarker = "[RAG_CONTEXT_BEGIN]",
        endMarker = "[RAG_CONTEXT_END]"
    )
    val lines = withoutContext.lines().map { line -> line.trim() }
    val ragLine = lines.firstOrNull { line -> line.startsWith("RAG:", ignoreCase = true) }
    val present = ragLine != null
    val enabled = ragLine?.substringAfter(":", "")?.trim()?.equals("ON", ignoreCase = true) == true

    return RagRunDisplayInfo(
        present = present,
        enabled = enabled,
        query = findTraceValue(lines, "query"),
        resultCount = findTraceValue(lines, "resultCount").toIntOrNull() ?: 0,
        usedChunkCount = findTraceValue(lines, "usedChunkCount").toIntOrNull() ?: 0,
        maxContextChars = findTraceValue(lines, "maxContextChars").toIntOrNull() ?: 0,
        fallback = findTraceValue(lines, "fallback").equals("true", ignoreCase = true),
        fallbackReason = findTraceValue(lines, "fallbackReason").ifBlank { null },
        contextText = contextText.trim()
    )
}

internal fun findTraceValue(
    lines: List<String>,
    key: String
): String {
    val colonPrefix = "$key:"
    val equalsPrefix = "$key="
    val line = lines.firstOrNull { value ->
        value.startsWith(colonPrefix, ignoreCase = true) ||
                value.startsWith(equalsPrefix, ignoreCase = true)
    } ?: return ""

    return when {
        line.startsWith(colonPrefix, ignoreCase = true) -> line.substringAfter(":").trim()
        line.startsWith(equalsPrefix, ignoreCase = true) -> line.substringAfter("=").trim()
        else -> ""
    }
}

internal fun parseWebSearchGroundingInfo(traceText: String): WebSearchGroundingDisplayInfo {
    val sectionText = extractTraceSection(traceText, "[WEB_SEARCH_GROUNDING]")
    if (sectionText.isBlank()) {
        return WebSearchGroundingDisplayInfo(
            present = false,
            requested = false,
            groundingExecuted = false,
            groundingUsed = false,
            citationCount = 0,
            searchQueryCount = 0,
            fallbackAllowed = false,
            fallbackAttempted = false,
            fallbackSucceeded = false,
            groundingErrorType = "NONE",
            fallbackErrorType = "NONE",
            finalAnswerSource = "ERROR"
        )
    }

    val lines = sectionText.lines().map { line -> line.trim() }
    val finalAnswerSource = findTraceValue(lines, "finalAnswerSource").ifBlank {
        if (findTraceValue(lines, "groundingUsed").equals("true", ignoreCase = true)) "GROUNDING" else "ERROR"
    }

    return WebSearchGroundingDisplayInfo(
        present = true,
        requested = findTraceValue(lines, "requested").equals("true", ignoreCase = true),
        groundingExecuted = findTraceValue(lines, "groundingExecuted").equals("true", ignoreCase = true),
        groundingUsed = findTraceValue(lines, "groundingUsed").equals("true", ignoreCase = true),
        citationCount = findTraceValue(lines, "citationCount").toIntOrNull() ?: 0,
        searchQueryCount = findTraceValue(lines, "searchQueryCount").toIntOrNull() ?: 0,
        fallbackAllowed = findTraceValue(lines, "fallbackAllowed").equals("true", ignoreCase = true),
        fallbackAttempted = findTraceValue(lines, "fallbackAttempted").equals("true", ignoreCase = true),
        fallbackSucceeded = findTraceValue(lines, "fallbackSucceeded").equals("true", ignoreCase = true),
        groundingErrorType = findTraceValue(lines, "groundingErrorType").ifBlank { "NONE" },
        fallbackErrorType = findTraceValue(lines, "fallbackErrorType").ifBlank { "NONE" },
        finalAnswerSource = finalAnswerSource
    )
}

internal fun parseWebSearchSourcesInfo(traceText: String): WebSearchSourcesDisplayInfo {
    val sectionText = extractTraceSection(traceText, "[WEB_SEARCH_GROUNDING]")
    if (sectionText.isBlank()) {
        return WebSearchSourcesDisplayInfo(
            present = false,
            requested = false,
            groundingExecuted = false,
            groundingUsed = false,
            citationCount = 0,
            searchQueryCount = 0,
            sources = emptyList()
        )
    }

    val lines = sectionText.lines().map { line -> line.trim() }
    val sources = mutableListOf<WebSearchSourceDisplayInfo>()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        val match = Regex("""^(\d+)\.\s+title=(.*)$""").find(line)
        if (match != null) {
            val sourceIndex = match.groupValues[1].toIntOrNull() ?: (sources.size + 1)
            val title = normalizeSourceField(match.groupValues[2], "제목 없음")
            var url = "URL 없음"
            var snippet = ""
            var cursor = index + 1

            while (cursor < lines.size) {
                val nextLine = lines[cursor]
                if (Regex("""^\d+\.\s+title=.*$""").matches(nextLine)) {
                    break
                }
                if (nextLine.startsWith("url=", ignoreCase = true)) {
                    url = normalizeSourceField(nextLine.substringAfter("="), "URL 없음")
                } else if (nextLine.startsWith("snippet=", ignoreCase = true)) {
                    snippet = normalizeSourceField(nextLine.substringAfter("="), "")
                }
                cursor += 1
            }

            sources.add(
                WebSearchSourceDisplayInfo(
                    index = sourceIndex,
                    title = title,
                    url = url,
                    snippet = snippet
                )
            )
            index = cursor
        } else {
            index += 1
        }
    }

    return WebSearchSourcesDisplayInfo(
        present = true,
        requested = findTraceValue(lines, "requested").equals("true", ignoreCase = true),
        groundingExecuted = findTraceValue(lines, "groundingExecuted").equals("true", ignoreCase = true),
        groundingUsed = findTraceValue(lines, "groundingUsed").equals("true", ignoreCase = true),
        citationCount = findTraceValue(lines, "citationCount").toIntOrNull() ?: sources.size,
        searchQueryCount = findTraceValue(lines, "searchQueryCount").toIntOrNull() ?: 0,
        sources = sources.take(10)
    )
}

internal fun extractTraceSection(
    text: String,
    marker: String
): String {
    val startIndex = text.indexOf(marker)
    if (startIndex == -1) return ""
    val contentStart = startIndex + marker.length
    val nextSectionIndex = Regex("""(?m)^\[[A-Z_]+]""")
        .find(text, contentStart)
        ?.range
        ?.first
        ?: text.length
    val ragIndex = text.indexOf("\nRAG:", contentStart).let { if (it == -1) text.length else it }
    val endIndex = minOf(nextSectionIndex, ragIndex)
    return text.substring(contentStart, endIndex).trim()
}

private fun removeTraceSection(
    text: String,
    marker: String
): String {
    var result = text
    while (true) {
        val startIndex = result.indexOf(marker)
        if (startIndex == -1) return result.trim()
        val contentStart = startIndex + marker.length
        val nextSectionIndex = Regex("""(?m)^\[[A-Z_]+]""")
            .find(result, contentStart)
            ?.range
            ?.first
            ?: result.length
        result = (result.substring(0, startIndex) + result.substring(nextSectionIndex)).trim()
    }
}

private fun normalizeSourceField(
    value: String,
    fallback: String
): String {
    val normalized = value.trim()
    return when {
        normalized.isBlank() -> fallback
        normalized.equals("UNKNOWN", ignoreCase = true) -> fallback
        else -> normalized
    }
}

internal fun buildWebSearchGroundingCopyText(info: WebSearchGroundingDisplayInfo): String {
    if (!info.present) return "[Web Search Grounding]\n요청: OFF"

    return buildString {
        appendLine("[Web Search Grounding]")
        appendLine("요청: ${info.requestedLabel}")
        appendLine("실행: ${info.executionLabel}")
        appendLine("Grounding 사용: ${info.groundingUsed}")
        appendLine("출처 수: ${info.citationCount}")
        appendLine("검색 쿼리 수: ${info.searchQueryCount}")
        appendLine("Fallback 허용: ${info.fallbackAllowed}")
        appendLine("Fallback 실행: ${info.fallbackAttempted}")
        appendLine("Fallback 성공: ${info.fallbackSucceeded}")
        appendLine("Grounding 오류: ${normalizeNoneValue(info.groundingErrorType)}")
        appendLine("Fallback 오류: ${normalizeNoneValue(info.fallbackErrorType)}")
        appendLine("최종 답변 출처: ${info.finalAnswerSourceLabel}")
    }.trim()
}

internal fun buildProviderSummaryCopyText(
    title: String,
    summaryText: String
): String {
    return buildString {
        appendLine("[$title]")
        appendLine(summaryText.ifBlank { "내용 없음" })
    }.trim()
}

internal fun normalizeNoneValue(value: String): String {
    return if (value.isBlank() || value.equals("NONE", ignoreCase = true)) "없음" else value
}

internal fun buildWebSearchSourcesCopyText(info: WebSearchSourcesDisplayInfo): String {
    if (!info.shouldDisplay) return "[Web Search Sources]\n참조 출처 없음"

    return buildString {
        appendLine("[Web Search Sources]")
        appendLine("citationCount: ${info.citationCount}")
        appendLine("searchQueryCount: ${info.searchQueryCount}")
        appendLine("groundingUsed: ${info.groundingUsed}")

        if (info.sources.isEmpty()) {
            appendLine()
            appendLine("참조 출처 없음")
        } else {
            info.sources.forEachIndexed { displayIndex, source ->
                appendLine()
                appendLine("${displayIndex + 1}. ${source.title.take(160)}")
                appendLine(source.url.take(240))
                if (source.snippet.isNotBlank()) {
                    appendLine(source.snippet.take(500))
                }
            }
        }
    }.trim()
}

internal fun cleanExecutionTraceText(traceText: String): String {
    val withoutContext = removeBetweenMarkers(
        text = traceText,
        startMarker = "[RAG_CONTEXT_BEGIN]",
        endMarker = "[RAG_CONTEXT_END]"
    )
    val withoutProviderSummary = removeTraceSection(
        text = removeTraceSection(
            text = withoutContext,
            marker = "[PROVIDER_RESPONSE_SUMMARY]"
        ),
        marker = "[FALLBACK_PROVIDER_RESPONSE_SUMMARY]"
    )

    return withoutProviderSummary.lines()
        .filterNot { line ->
            val trimmed = line.trim()
            trimmed.startsWith("RAG:", ignoreCase = true) ||
                    trimmed.startsWith("query:", ignoreCase = true) ||
                    trimmed.startsWith("resultCount:", ignoreCase = true) ||
                    trimmed.startsWith("usedChunkCount:", ignoreCase = true) ||
                    trimmed.startsWith("totalTokenEstimate:", ignoreCase = true) ||
                    trimmed.startsWith("maxResults:", ignoreCase = true) ||
                    trimmed.startsWith("maxContextChars:", ignoreCase = true) ||
                    trimmed.startsWith("fallback:", ignoreCase = true) ||
                    trimmed.startsWith("fallbackReason:", ignoreCase = true) ||
                    trimmed.startsWith("requested:", ignoreCase = true) ||
                    trimmed.startsWith("providerType:", ignoreCase = true) ||
                    trimmed.startsWith("modelWebSearchStatus:", ignoreCase = true) ||
                    trimmed.startsWith("nativeGroundingAvailable:", ignoreCase = true) ||
                    trimmed.startsWith("canAttemptGrounding:", ignoreCase = true) ||
                    trimmed.startsWith("groundingExecuted:", ignoreCase = true) ||
                    trimmed.startsWith("groundingUsed:", ignoreCase = true) ||
                    trimmed.startsWith("citationCount:", ignoreCase = true) ||
                    trimmed.startsWith("searchQueryCount:", ignoreCase = true) ||
                    trimmed.startsWith("modelSupportsWebSearch:", ignoreCase = true) ||
                    trimmed.startsWith("fallbackAllowed:", ignoreCase = true) ||
                    trimmed.startsWith("fallbackAttempted:", ignoreCase = true) ||
                    trimmed.startsWith("fallbackSucceeded:", ignoreCase = true) ||
                    trimmed.startsWith("groundingErrorType:", ignoreCase = true) ||
                    trimmed.startsWith("fallbackErrorType:", ignoreCase = true) ||
                    trimmed.startsWith("finalAnswerSource:", ignoreCase = true) ||
                    trimmed.startsWith("searchQueries:", ignoreCase = true) ||
                    trimmed.equals("results:", ignoreCase = true) ||
                    trimmed.equals("sources:", ignoreCase = true) ||
                    trimmed.startsWith("url=", ignoreCase = true) ||
                    trimmed.startsWith("snippet=", ignoreCase = true) ||
                    Regex("^\\d+\\. .+").matches(trimmed)
        }
        .joinToString("\n")
        .trim()
}

private fun extractBetweenMarkers(
    text: String,
    startMarker: String,
    endMarker: String
): String {
    val startIndex = text.indexOf(startMarker)
    if (startIndex == -1) return ""
    val contentStart = startIndex + startMarker.length
    val endIndex = text.indexOf(endMarker, contentStart)
    if (endIndex == -1) return text.substring(contentStart).trim()
    return text.substring(contentStart, endIndex).trim()
}

private fun removeBetweenMarkers(
    text: String,
    startMarker: String,
    endMarker: String
): String {
    val startIndex = text.indexOf(startMarker)
    if (startIndex == -1) return text
    val contentStart = startIndex + startMarker.length
    val endIndex = text.indexOf(endMarker, contentStart)
    val removeEnd = if (endIndex == -1) text.length else endIndex + endMarker.length
    return (text.substring(0, startIndex) + text.substring(removeEnd)).trim()
}

internal fun buildRagRunCopyText(ragInfo: RagRunDisplayInfo): String {
    return buildString {
        appendLine("[RAG]")
        appendLine("status: ${if (ragInfo.enabled) "ON" else "OFF"}")
        if (ragInfo.query.isNotBlank()) {
            appendLine("query: ${ragInfo.query}")
        }
        appendLine("resultCount: ${ragInfo.resultCount}")
        appendLine("usedChunkCount: ${ragInfo.usedChunkCount}")
        appendLine("maxContextChars: ${ragInfo.maxContextChars}")
        appendLine("fallback: ${ragInfo.fallback}")
        appendLine("fallbackReason: ${ragInfo.fallbackReason ?: "없음"}")
    }.trim()
}
