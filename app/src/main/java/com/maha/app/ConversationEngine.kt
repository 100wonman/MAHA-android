package com.maha.app

class ConversationEngine {
    fun execute(request: ConversationRequest): ConversationResponse {
        val startedAt = System.currentTimeMillis()

        return try {
            val rawText = "더미 실행 결과입니다. 실제 API 호출은 아직 연결하지 않았습니다."
            val traceText = buildTraceText(request.ragContext)
            val baseRun = createDummyConversationRun(request.sessionId)
            val latencySec = ((System.currentTimeMillis() - startedAt).coerceAtLeast(1) / 1000.0)
                .coerceAtLeast(1.1)

            val runInfo = baseRun.copy(
                runId = request.requestId,
                userInput = request.userInput,
                status = ConversationRunStatus.COMPLETED,
                totalLatencySec = latencySec,
                totalRetryCount = 0,
                workerResults = baseRun.workerResults.mapIndexed { index, worker ->
                    if (index == 0) {
                        worker.copy(
                            status = "COMPLETED",
                            providerName = "DUMMY",
                            modelName = "conversation-engine-dummy",
                            latencySec = latencySec,
                            retryCount = 0,
                            errorType = "",
                            outputSummary = "ConversationEngine 더미 응답 생성 완료",
                            rawOutput = buildString {
                                appendLine(rawText)
                                appendLine()
                                appendLine("[RAG]")
                                append(traceText)
                            }.trim()
                        )
                    } else {
                        worker
                    }
                }
            )

            ConversationResponse(
                responseId = "conversation_response_${System.currentTimeMillis()}",
                requestId = request.requestId,
                status = "SUCCESS",
                blocks = listOf(
                    ConversationOutputBlock(
                        blockId = "block_assistant_${System.currentTimeMillis()}",
                        type = ConversationOutputBlockType.TEXT_BLOCK,
                        title = "더미 응답",
                        content = rawText,
                        collapsed = false
                    ),
                    ConversationOutputBlock(
                        blockId = "block_trace_${System.currentTimeMillis()}",
                        type = ConversationOutputBlockType.TRACE_BLOCK,
                        title = "실행 과정",
                        content = traceText,
                        collapsed = true
                    )
                ),
                rawText = rawText,
                providerName = "DUMMY",
                modelName = "conversation-engine-dummy",
                latencySec = latencySec,
                errorType = null,
                runInfo = runInfo,
                createdAt = System.currentTimeMillis()
            )
        } catch (throwable: Throwable) {
            createFailureResponse(
                request = request,
                error = throwable,
                startedAt = startedAt
            )
        }
    }

    private fun createFailureResponse(
        request: ConversationRequest,
        error: Throwable,
        startedAt: Long
    ): ConversationResponse {
        val now = System.currentTimeMillis()
        val latencySec = ((now - startedAt).coerceAtLeast(1) / 1000.0)
        val errorMessage = error.message ?: "알 수 없는 오류"
        val baseRun = createDummyConversationRun(request.sessionId)
        val runInfo = baseRun.copy(
            runId = request.requestId,
            userInput = request.userInput,
            status = ConversationRunStatus.FAILED,
            totalLatencySec = latencySec,
            totalRetryCount = 0,
            workerResults = baseRun.workerResults.mapIndexed { index, worker ->
                if (index == 0) {
                    worker.copy(
                        status = "FAILED",
                        providerName = "DUMMY",
                        modelName = "conversation-engine-dummy",
                        latencySec = latencySec,
                        retryCount = 0,
                        errorType = "UNKNOWN",
                        outputSummary = "ConversationEngine 실패",
                        rawOutput = errorMessage
                    )
                } else {
                    worker
                }
            }
        )

        return ConversationResponse(
            responseId = "conversation_response_$now",
            requestId = request.requestId,
            status = "FAILED",
            blocks = listOf(
                ConversationOutputBlock(
                    blockId = "block_error_$now",
                    type = ConversationOutputBlockType.ERROR_BLOCK,
                    title = "응답 생성 실패",
                    content = errorMessage,
                    collapsed = false
                )
            ),
            rawText = errorMessage,
            providerName = "DUMMY",
            modelName = "conversation-engine-dummy",
            latencySec = latencySec,
            errorType = "UNKNOWN",
            runInfo = runInfo,
            createdAt = now
        )
    }

    private fun buildTraceText(ragContext: RagContext?): String {
        val ragTrace = buildRagTraceText(ragContext)
        return buildString {
            appendLine("입력 수신 → ConversationEngine 실행 → 더미 응답 생성 → 화면 갱신")
            append(ragTrace)
        }.trim()
    }

    private fun buildRagTraceText(ragContext: RagContext?): String {
        if (ragContext == null || !ragContext.enabled) {
            return "RAG: OFF"
        }

        return buildString {
            appendLine("RAG: ON")
            appendLine("query: ${ragContext.query}")
            appendLine("resultCount: ${ragContext.results.size}")
            appendLine("usedChunkCount: ${ragContext.results.size}")
            appendLine("totalTokenEstimate: ${ragContext.totalTokenEstimate}")
            appendLine("maxResults: ${ragContext.maxResults}")
            appendLine("maxContextChars: ${ragContext.maxContextChars}")
            appendLine("fallback: ${ragContext.fallback}")
            if (ragContext.fallbackReason != null) {
                appendLine("fallbackReason: ${ragContext.fallbackReason}")
            }
            if (ragContext.results.isNotEmpty()) {
                appendLine("results:")
                ragContext.results.forEachIndexed { index, result ->
                    appendLine("${index + 1}. ${result.title} / score=${result.score} / ${result.filePath}")
                }
            }
            if (ragContext.contextText.isNotBlank()) {
                appendLine("[RAG_CONTEXT_BEGIN]")
                appendLine(ragContext.contextText)
                appendLine("[RAG_CONTEXT_END]")
            }
        }.trim()
    }
}
