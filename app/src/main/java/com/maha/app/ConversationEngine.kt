package com.maha.app

class ConversationEngine(
    private val promptBuilder: ConversationPromptBuilder = ConversationPromptBuilder(),
    private val apiKeyProvider: (providerName: String) -> String? = { null },
    private val googleGeminiProviderAdapter: GoogleGeminiProviderAdapter = GoogleGeminiProviderAdapter()
) {
    suspend fun execute(request: ConversationRequest): ConversationResponse {
        val startedAt = System.currentTimeMillis()

        return try {
            val prompt = promptBuilder.build(request)
            val providerName = resolveProviderName(request.selectedProvider)
            val modelName = resolveModelName(request.selectedModel)

            if (isGoogleProvider(providerName)) {
                val apiKey = apiKeyProvider(providerName)
                if (apiKey.isNullOrBlank()) {
                    return createApiKeyMissingResponse(
                        request = request,
                        providerName = providerName,
                        modelName = modelName,
                        startedAt = startedAt,
                        promptLength = prompt.length
                    )
                }

                val providerResult = googleGeminiProviderAdapter.callGemini(
                    prompt = prompt,
                    modelName = modelName,
                    apiKey = apiKey
                )

                return if (providerResult.success) {
                    createProviderSuccessResponse(
                        request = request,
                        rawText = providerResult.rawText,
                        providerName = providerResult.providerName,
                        modelName = modelName,
                        latencySec = providerResult.latencySec,
                        promptLength = prompt.length
                    )
                } else {
                    createProviderFailureResponse(
                        request = request,
                        providerName = providerName,
                        modelName = modelName,
                        latencySec = providerResult.latencySec,
                        promptLength = prompt.length,
                        errorType = providerResult.errorType ?: "UNKNOWN",
                        errorMessage = providerResult.errorMessage ?: "Gemini API 호출에 실패했습니다.",
                        responseSummary = providerResult.responseSummary
                    )
                }
            }

            if (requiresApiKey(providerName) && apiKeyProvider(providerName).isNullOrBlank()) {
                return createApiKeyMissingResponse(
                    request = request,
                    providerName = providerName,
                    modelName = modelName,
                    startedAt = startedAt,
                    promptLength = prompt.length
                )
            }

            createDummySuccessResponse(
                request = request,
                providerName = providerName,
                modelName = modelName,
                startedAt = startedAt,
                promptLength = prompt.length
            )
        } catch (throwable: Throwable) {
            createFailureResponse(
                request = request,
                error = throwable,
                startedAt = startedAt
            )
        }
    }

    private fun createProviderSuccessResponse(
        request: ConversationRequest,
        rawText: String,
        providerName: String,
        modelName: String,
        latencySec: Double,
        promptLength: Int
    ): ConversationResponse {
        val now = System.currentTimeMillis()
        val traceText = buildTraceText(
            ragContext = request.ragContext,
            providerName = providerName,
            modelName = modelName,
            promptLength = promptLength,
            responseLength = rawText.length,
            actualApiCall = true,
            errorType = null
        )
        val baseRun = createDummyConversationRun(request.sessionId)

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
                        providerName = providerName,
                        modelName = modelName,
                        latencySec = latencySec,
                        retryCount = 0,
                        errorType = "",
                        outputSummary = "Google Gemini 실제 호출 완료",
                        rawOutput = buildString {
                            appendLine(rawText.take(800))
                            appendLine()
                            appendLine("[GOOGLE_GEMINI_CALL]")
                            appendLine("providerName: $providerName")
                            appendLine("modelName: $modelName")
                            appendLine("promptLength: $promptLength")
                            appendLine("responseLength: ${rawText.length}")
                            appendLine("latencySec: $latencySec")
                            appendLine("actualApiCall: true")
                            appendLine()
                            appendLine("[RAG]")
                            append(buildRagTraceText(request.ragContext))
                        }.trim()
                    )
                } else {
                    worker
                }
            }
        )

        return ConversationResponse(
            responseId = "conversation_response_$now",
            requestId = request.requestId,
            status = "SUCCESS",
            blocks = listOf(
                ConversationOutputBlock(
                    blockId = "block_assistant_$now",
                    type = ConversationOutputBlockType.TEXT_BLOCK,
                    title = "Google Gemini 응답",
                    content = rawText,
                    collapsed = false
                ),
                ConversationOutputBlock(
                    blockId = "block_trace_$now",
                    type = ConversationOutputBlockType.TRACE_BLOCK,
                    title = "실행 과정",
                    content = traceText,
                    collapsed = true
                )
            ),
            rawText = rawText,
            providerName = providerName,
            modelName = modelName,
            latencySec = latencySec,
            errorType = null,
            runInfo = runInfo,
            createdAt = now
        )
    }

    private fun createProviderFailureResponse(
        request: ConversationRequest,
        providerName: String,
        modelName: String,
        latencySec: Double,
        promptLength: Int,
        errorType: String,
        errorMessage: String,
        responseSummary: String? = null
    ): ConversationResponse {
        val now = System.currentTimeMillis()
        val safeErrorMessage = errorMessage.ifBlank { "Provider 호출에 실패했습니다." }
        val traceText = buildTraceText(
            ragContext = request.ragContext,
            providerName = providerName,
            modelName = modelName,
            promptLength = promptLength,
            responseLength = 0,
            actualApiCall = true,
            errorType = errorType,
            providerResponseSummary = responseSummary
        )
        val baseRun = createDummyConversationRun(request.sessionId)

        val runInfo = baseRun.copy(
            runId = request.requestId,
            userInput = request.userInput,
            status = ConversationRunStatus.FAILED,
            totalLatencySec = latencySec,
            totalRetryCount = 0,
            workerResults = baseRun.workerResults.mapIndexed { index, worker ->
                when (index) {
                    0 -> worker.copy(
                        status = "FAILED",
                        providerName = providerName,
                        modelName = modelName,
                        latencySec = latencySec,
                        retryCount = 0,
                        errorType = errorType,
                        outputSummary = "Google Gemini 호출 실패: $errorType",
                        rawOutput = buildString {
                            appendLine(safeErrorMessage.take(800))
                            appendLine()
                            appendLine("[GOOGLE_GEMINI_CALL]")
                            appendLine("providerName: $providerName")
                            appendLine("modelName: $modelName")
                            appendLine("promptLength: $promptLength")
                            appendLine("latencySec: $latencySec")
                            appendLine("actualApiCall: true")
                            appendLine("errorType: $errorType")
                            if (!responseSummary.isNullOrBlank()) {
                                appendLine()
                                appendLine("[PROVIDER_RESPONSE_SUMMARY]")
                                appendLine(responseSummary.take(1200))
                            }
                            appendLine()
                            appendLine("[RAG]")
                            append(buildRagTraceText(request.ragContext))
                        }.trim()
                    )

                    else -> worker.copy(
                        status = "SKIPPED",
                        providerName = providerName,
                        modelName = modelName,
                        latencySec = 0.0,
                        retryCount = 0,
                        errorType = errorType,
                        outputSummary = "Main Worker 실패로 실행 건너뜀",
                        rawOutput = "SKIPPED"
                    )
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
                    title = "Gemini 호출 실패",
                    content = safeErrorMessage,
                    collapsed = false
                ),
                ConversationOutputBlock(
                    blockId = "block_trace_$now",
                    type = ConversationOutputBlockType.TRACE_BLOCK,
                    title = "실행 과정",
                    content = traceText,
                    collapsed = true
                )
            ),
            rawText = safeErrorMessage,
            providerName = providerName,
            modelName = modelName,
            latencySec = latencySec,
            errorType = errorType,
            runInfo = runInfo,
            createdAt = now
        )
    }

    private fun createDummySuccessResponse(
        request: ConversationRequest,
        providerName: String,
        modelName: String,
        startedAt: Long,
        promptLength: Int
    ): ConversationResponse {
        val now = System.currentTimeMillis()
        val latencySec = ((now - startedAt).coerceAtLeast(1) / 1000.0)
            .coerceAtLeast(1.1)
        val rawText = "더미 실행 결과입니다. 실제 API 호출은 아직 연결하지 않았습니다."
        val traceText = buildTraceText(
            ragContext = request.ragContext,
            providerName = providerName,
            modelName = modelName,
            promptLength = promptLength,
            responseLength = rawText.length,
            actualApiCall = false,
            errorType = null
        )
        val baseRun = createDummyConversationRun(request.sessionId)

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
                        providerName = providerName,
                        modelName = modelName,
                        latencySec = latencySec,
                        retryCount = 0,
                        errorType = "",
                        outputSummary = "ConversationEngine provider dry-run 더미 응답 생성 완료",
                        rawOutput = buildString {
                            appendLine(rawText)
                            appendLine()
                            appendLine("[PROVIDER_DRY_RUN]")
                            appendLine("providerName: $providerName")
                            appendLine("modelName: $modelName")
                            appendLine("promptLength: $promptLength")
                            appendLine("responseLength: ${rawText.length}")
                            appendLine("actualApiCall: false")
                            appendLine()
                            appendLine("[RAG]")
                            append(buildRagTraceText(request.ragContext))
                        }.trim()
                    )
                } else {
                    worker
                }
            }
        )

        return ConversationResponse(
            responseId = "conversation_response_$now",
            requestId = request.requestId,
            status = "SUCCESS",
            blocks = listOf(
                ConversationOutputBlock(
                    blockId = "block_assistant_$now",
                    type = ConversationOutputBlockType.TEXT_BLOCK,
                    title = "더미 응답",
                    content = rawText,
                    collapsed = false
                ),
                ConversationOutputBlock(
                    blockId = "block_trace_$now",
                    type = ConversationOutputBlockType.TRACE_BLOCK,
                    title = "실행 과정",
                    content = traceText,
                    collapsed = true
                )
            ),
            rawText = rawText,
            providerName = providerName,
            modelName = modelName,
            latencySec = latencySec,
            errorType = null,
            runInfo = runInfo,
            createdAt = now
        )
    }

    private fun createApiKeyMissingResponse(
        request: ConversationRequest,
        providerName: String,
        modelName: String,
        startedAt: Long,
        promptLength: Int
    ): ConversationResponse {
        val now = System.currentTimeMillis()
        val latencySec = ((now - startedAt).coerceAtLeast(1) / 1000.0)
        val errorText = "API Key가 설정되지 않았습니다. 설정에서 API Key를 입력하세요."
        val traceText = buildTraceText(
            ragContext = request.ragContext,
            providerName = providerName,
            modelName = modelName,
            promptLength = promptLength,
            responseLength = 0,
            actualApiCall = false,
            errorType = "API_KEY_MISSING"
        )
        val baseRun = createDummyConversationRun(request.sessionId)

        val runInfo = baseRun.copy(
            runId = request.requestId,
            userInput = request.userInput,
            status = ConversationRunStatus.FAILED,
            totalLatencySec = latencySec,
            totalRetryCount = 0,
            workerResults = baseRun.workerResults.mapIndexed { index, worker ->
                when (index) {
                    0 -> worker.copy(
                        status = "FAILED",
                        providerName = providerName,
                        modelName = modelName,
                        latencySec = latencySec,
                        retryCount = 0,
                        errorType = "API_KEY_MISSING",
                        outputSummary = "API Key 없음으로 provider 호출 실패",
                        rawOutput = buildString {
                            appendLine(errorText)
                            appendLine()
                            appendLine("[PROVIDER]")
                            appendLine("providerName: $providerName")
                            appendLine("modelName: $modelName")
                            appendLine("promptLength: $promptLength")
                            appendLine("actualApiCall: false")
                            appendLine("errorType: API_KEY_MISSING")
                            appendLine()
                            appendLine("[RAG]")
                            append(buildRagTraceText(request.ragContext))
                        }.trim()
                    )

                    else -> worker.copy(
                        status = "SKIPPED",
                        providerName = providerName,
                        modelName = modelName,
                        latencySec = 0.0,
                        retryCount = 0,
                        errorType = "API_KEY_MISSING",
                        outputSummary = "API Key 없음으로 실행 건너뜀",
                        rawOutput = "SKIPPED"
                    )
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
                    title = "API Key 필요",
                    content = errorText,
                    collapsed = false
                ),
                ConversationOutputBlock(
                    blockId = "block_trace_$now",
                    type = ConversationOutputBlockType.TRACE_BLOCK,
                    title = "실행 과정",
                    content = traceText,
                    collapsed = true
                )
            ),
            rawText = errorText,
            providerName = providerName,
            modelName = modelName,
            latencySec = latencySec,
            errorType = "API_KEY_MISSING",
            runInfo = runInfo,
            createdAt = now
        )
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
                        providerName = resolveProviderName(request.selectedProvider),
                        modelName = resolveModelName(request.selectedModel),
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
            providerName = resolveProviderName(request.selectedProvider),
            modelName = resolveModelName(request.selectedModel),
            latencySec = latencySec,
            errorType = "UNKNOWN",
            runInfo = runInfo,
            createdAt = now
        )
    }

    private fun buildTraceText(
        ragContext: RagContext?,
        providerName: String,
        modelName: String,
        promptLength: Int,
        responseLength: Int,
        actualApiCall: Boolean,
        errorType: String?,
        providerResponseSummary: String? = null
    ): String {
        return buildString {
            appendLine("입력 수신 → PromptBuilder 실행 → Provider 확인 → 응답 생성 → 화면 갱신")
            appendLine("providerName: $providerName")
            appendLine("modelName: $modelName")
            appendLine("promptLength: $promptLength")
            appendLine("responseLength: $responseLength")
            appendLine("actualApiCall: $actualApiCall")
            if (errorType != null) {
                appendLine("errorType: $errorType")
            }
            if (!providerResponseSummary.isNullOrBlank()) {
                appendLine("[PROVIDER_RESPONSE_SUMMARY]")
                appendLine(providerResponseSummary.take(1200))
            }
            append(buildRagTraceText(ragContext))
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

    private fun resolveProviderName(selectedProvider: String?): String {
        return selectedProvider?.trim()?.takeIf { it.isNotBlank() } ?: "DUMMY"
    }

    private fun resolveModelName(selectedModel: String?): String {
        return selectedModel?.trim()?.takeIf { it.isNotBlank() } ?: "conversation-engine-dummy"
    }

    private fun requiresApiKey(providerName: String): Boolean {
        return !providerName.equals("DUMMY", ignoreCase = true)
    }

    private fun isGoogleProvider(providerName: String): Boolean {
        return providerName.equals("GOOGLE", ignoreCase = true) ||
                providerName.contains("google", ignoreCase = true) ||
                providerName.contains("gemini", ignoreCase = true)
    }
}
