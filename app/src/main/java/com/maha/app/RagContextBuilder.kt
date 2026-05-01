package com.maha.app

class RagContextBuilder(
    private val searchEngine: RagKeywordSearchEngine
) {
    fun build(
        query: String,
        enabled: Boolean,
        maxResults: Int = DEFAULT_MAX_RESULTS,
        maxContextChars: Int = DEFAULT_MAX_CONTEXT_CHARS,
        maxLoadedChunks: Int = DEFAULT_MAX_LOADED_CHUNKS,
        minScore: Int = DEFAULT_MIN_SCORE
    ): RagContext {
        val trimmedQuery = query.trim()
        val safeMaxResults = maxResults.coerceAtLeast(1)
        val safeMaxContextChars = maxContextChars.coerceAtLeast(0)

        if (!enabled) {
            return RagContext(
                query = trimmedQuery,
                enabled = false,
                results = emptyList(),
                contextText = "",
                totalTokenEstimate = 0,
                maxResults = safeMaxResults,
                maxContextChars = safeMaxContextChars,
                createdAt = System.currentTimeMillis(),
                fallback = false,
                fallbackReason = null
            )
        }

        if (trimmedQuery.isBlank()) {
            return RagContext(
                query = trimmedQuery,
                enabled = true,
                results = emptyList(),
                contextText = "",
                totalTokenEstimate = 0,
                maxResults = safeMaxResults,
                maxContextChars = safeMaxContextChars,
                createdAt = System.currentTimeMillis(),
                fallback = true,
                fallbackReason = "EMPTY_QUERY"
            )
        }

        val searchResults = runCatching {
            searchEngine.search(
                query = trimmedQuery,
                topK = safeMaxResults,
                maxLoadedChunks = maxLoadedChunks.coerceAtLeast(1)
            ).filter { result -> result.score >= minScore }
        }.getOrElse {
            return RagContext(
                query = trimmedQuery,
                enabled = true,
                results = emptyList(),
                contextText = "",
                totalTokenEstimate = 0,
                maxResults = safeMaxResults,
                maxContextChars = safeMaxContextChars,
                createdAt = System.currentTimeMillis(),
                fallback = true,
                fallbackReason = "SEARCH_FAILED"
            )
        }

        if (searchResults.isEmpty()) {
            return RagContext(
                query = trimmedQuery,
                enabled = true,
                results = emptyList(),
                contextText = "",
                totalTokenEstimate = 0,
                maxResults = safeMaxResults,
                maxContextChars = safeMaxContextChars,
                createdAt = System.currentTimeMillis(),
                fallback = true,
                fallbackReason = "NO_RESULT"
            )
        }

        val contextText = buildContextText(
            results = searchResults,
            maxContextChars = safeMaxContextChars
        )

        return RagContext(
            query = trimmedQuery,
            enabled = true,
            results = searchResults,
            contextText = contextText,
            totalTokenEstimate = estimateTokens(contextText),
            maxResults = safeMaxResults,
            maxContextChars = safeMaxContextChars,
            createdAt = System.currentTimeMillis(),
            fallback = false,
            fallbackReason = null
        )
    }

    private fun buildContextText(
        results: List<RagSearchResult>,
        maxContextChars: Int
    ): String {
        if (maxContextChars <= 0) return ""

        val builder = StringBuilder()

        results.forEachIndexed { index, result ->
            val block = buildString {
                appendLine("[RAG_CONTEXT_${index + 1}]")
                appendLine("sourceType: ${result.sourceType}")
                appendLine("title: ${result.title}")
                appendLine("score: ${result.score}")
                appendLine("filePath: ${result.filePath}")
                appendLine("text:")
                appendLine(result.matchedTextSnippet.ifBlank { result.textPreview })
            }.trim()

            val separator = if (builder.isEmpty()) "" else "\n\n"
            val nextText = separator + block
            if (builder.length + nextText.length > maxContextChars) {
                val remaining = maxContextChars - builder.length
                if (remaining > 0) {
                    builder.append(nextText.take(remaining))
                }
                return builder.toString().trim()
            }
            builder.append(nextText)
        }

        return builder.toString().trim()
    }

    private fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        return (text.length / 4).coerceAtLeast(1)
    }

    companion object {
        const val DEFAULT_MAX_RESULTS = 3
        const val DEFAULT_MAX_CONTEXT_CHARS = 4000
        const val DEFAULT_MAX_LOADED_CHUNKS = 50
        const val DEFAULT_MIN_SCORE = 1
    }
}
