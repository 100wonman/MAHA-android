package com.maha.app

class ConversationEngine(
    private val promptBuilder: ConversationPromptBuilder = ConversationPromptBuilder(),
    private val apiKeyProvider: (providerName: String) -> String? = { null },
    private val providerProfileProvider: (providerId: String) -> ProviderProfile? = { null },
    private val googleGeminiProviderAdapter: GoogleGeminiProviderAdapter = GoogleGeminiProviderAdapter(),
    private val openAiCompatibleProviderAdapter: OpenAiCompatibleProviderAdapter = OpenAiCompatibleProviderAdapter(),
    private val geminiNativeProviderAdapter: GeminiNativeProviderAdapter = GeminiNativeProviderAdapter()
) {
    suspend fun execute(request: ConversationRequest): ConversationResponse {
        val startedAt = System.currentTimeMillis()

        return try {
            val prompt = promptBuilder.build(request)
            val providerName = resolveProviderName(request.selectedProvider)
            val modelName = resolveModelName(request.selectedModel)
            val providerProfile = if (providerName.isBlank() || providerName.equals("DUMMY", ignoreCase = true)) {
                null
            } else {
                providerProfileProvider(providerName)
            }
            val toolSupportPolicy = providerProfile?.let { ToolSupportResolver.resolve(it.providerType) }

            if (isModelMissing(modelName)) {
                return createModelMissingResponse(
                    request = request,
                    providerName = providerName.ifBlank { "PROVIDER_UNKNOWN" },
                    modelName = modelName,
                    startedAt = startedAt,
                    promptLength = prompt.length,
                    toolSupportPolicy = toolSupportPolicy
                )
            }

            if (providerName.equals("DUMMY", ignoreCase = true)) {
                return createDummySuccessResponse(
                    request = request,
                    providerName = providerName,
                    modelName = modelName,
                    startedAt = startedAt,
                    promptLength = prompt.length
                )
            }

            if (providerName.isBlank() || providerProfile == null) {
                return createProviderSetupFailureResponse(
                    request = request,
                    providerName = providerName.ifBlank { "PROVIDER_MISSING" },
                    modelName = modelName,
                    startedAt = startedAt,
                    promptLength = prompt.length,
                    errorType = "PROVIDER_MISSING",
                    toolSupportPolicy = toolSupportPolicy
                )
            }

            if (request.webSearchEnabled && providerProfile.providerType != ProviderType.GOOGLE) {
                return createProviderSetupFailureResponse(
                    request = request,
                    providerName = providerName,
                    modelName = modelName,
                    startedAt = startedAt,
                    promptLength = prompt.length,
                    errorType = "WEB_SEARCH_NOT_SUPPORTED",
                    toolSupportPolicy = toolSupportPolicy
                )
            }

            if (isGoogleProvider(providerName, providerProfile)) {
                val apiKey = apiKeyProvider(providerName)
                if (apiKey.isNullOrBlank()) {
                    return createApiKeyMissingResponse(
                        request = request,
                        providerName = providerName,
                        modelName = modelName,
                        startedAt = startedAt,
                        promptLength = prompt.length,
                        toolSupportPolicy = toolSupportPolicy
                    )
                }

                if (request.webSearchEnabled) {
                    val webSearchPolicy = WebSearchUsageResolver.resolve(
                        enabledByUser = true,
                        providerType = providerProfile.providerType,
                        modelWebSearchStatus = request.selectedModelWebSearchStatus,
                        nativeGroundingAvailable = true,
                        fallbackAllowed = request.webSearchFallbackEnabled
                    )

                    if (!webSearchPolicy.canAttemptGrounding) {
                        val errorType = when (webSearchPolicy.reason) {
                            WebSearchUsageResolver.REASON_MODEL_CAPABILITY_NOT_ENABLED -> "MODEL_UNSUPPORTED_TOOL"
                            WebSearchUsageResolver.REASON_PROVIDER_NOT_GOOGLE -> "WEB_SEARCH_NOT_SUPPORTED"
                            else -> "WEB_SEARCH_NOT_SUPPORTED"
                        }
                        val setupFailureResult = GeminiNativeResult.failure(
                            latencySec = 0.0,
                            errorType = errorType,
                            errorMessage = buildUserFacingErrorMessage(
                                errorType = errorType,
                                fallbackMessage = "Web Search grounding 조건을 만족하지 못했습니다."
                            ),
                            rawMetadataSummary = "groundingExecuted=false, reason=${webSearchPolicy.reason ?: "UNKNOWN"}"
                        )

                        if (shouldAttemptWebSearchFallback(request, errorType)) {
                            val fallbackResult = googleGeminiProviderAdapter.callGemini(
                                prompt = prompt,
                                modelName = modelName,
                                apiKey = apiKey
                            )

                            return if (fallbackResult.success) {
                                createNativeGroundingFallbackSuccessResponse(
                                    request = request,
                                    rawText = fallbackResult.rawText,
                                    providerName = providerName,
                                    modelName = modelName,
                                    promptLength = prompt.length,
                                    nativeResult = setupFailureResult,
                                    fallbackResult = fallbackResult,
                                    groundingExecuted = false,
                                    toolSupportPolicy = toolSupportPolicy
                                )
                            } else {
                                createNativeGroundingFailureResponse(
                                    request = request,
                                    providerName = providerName,
                                    modelName = modelName,
                                    promptLength = prompt.length,
                                    nativeResult = setupFailureResult,
                                    fallbackAttempted = true,
                                    fallbackSucceeded = false,
                                    fallbackErrorType = fallbackResult.errorType ?: "UNKNOWN",
                                    fallbackErrorMessage = fallbackResult.errorMessage,
                                    groundingExecuted = false,
                                    toolSupportPolicy = toolSupportPolicy
                                )
                            }
                        }

                        return createProviderSetupFailureResponse(
                            request = request,
                            providerName = providerName,
                            modelName = modelName,
                            startedAt = startedAt,
                            promptLength = prompt.length,
                            errorType = errorType,
                            toolSupportPolicy = toolSupportPolicy
                        )
                    }

                    val nativeResult = geminiNativeProviderAdapter.execute(
                        request = GeminiNativeRequest(
                            requestId = request.requestId,
                            modelName = modelName,
                            prompt = prompt,
                            systemInstruction = request.systemInstruction,
                            enableGoogleSearch = true,
                            createdAt = System.currentTimeMillis()
                        ),
                        apiKey = apiKey
                    )

                    return if (nativeResult.success && nativeResult.rawText.isNotBlank()) {
                        createNativeGroundingSuccessResponse(
                            request = request,
                            rawText = nativeResult.rawText,
                            providerName = providerName,
                            modelName = modelName,
                            promptLength = prompt.length,
                            nativeResult = nativeResult,
                            toolSupportPolicy = toolSupportPolicy
                        )
                    } else {
                        val nativeErrorType = nativeResult.errorType ?: "GROUNDING_FAILED"
                        if (shouldAttemptWebSearchFallback(request, nativeErrorType)) {
                            val fallbackResult = googleGeminiProviderAdapter.callGemini(
                                prompt = prompt,
                                modelName = modelName,
                                apiKey = apiKey
                            )

                            if (fallbackResult.success) {
                                createNativeGroundingFallbackSuccessResponse(
                                    request = request,
                                    rawText = fallbackResult.rawText,
                                    providerName = providerName,
                                    modelName = modelName,
                                    promptLength = prompt.length,
                                    nativeResult = nativeResult,
                                    fallbackResult = fallbackResult,
                                    groundingExecuted = true,
                                    toolSupportPolicy = toolSupportPolicy
                                )
                            } else {
                                createNativeGroundingFailureResponse(
                                    request = request,
                                    providerName = providerName,
                                    modelName = modelName,
                                    promptLength = prompt.length,
                                    nativeResult = nativeResult,
                                    fallbackAttempted = true,
                                    fallbackSucceeded = false,
                                    fallbackErrorType = fallbackResult.errorType ?: "UNKNOWN",
                                    fallbackErrorMessage = fallbackResult.errorMessage,
                                    groundingExecuted = true,
                                    toolSupportPolicy = toolSupportPolicy
                                )
                            }
                        } else {
                            createNativeGroundingFailureResponse(
                                request = request,
                                providerName = providerName,
                                modelName = modelName,
                                promptLength = prompt.length,
                                nativeResult = nativeResult,
                                toolSupportPolicy = toolSupportPolicy
                            )
                        }
                    }
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
                        promptLength = prompt.length,
                        providerResponseSummary = providerResult.responseSummary,
                        toolSupportPolicy = toolSupportPolicy
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
                        responseSummary = providerResult.responseSummary,
                        toolSupportPolicy = toolSupportPolicy
                    )
                }
            }

            if (isOpenAiCompatibleProvider(providerProfile)) {
                if (providerProfile.baseUrl.isBlank()) {
                    return createProviderSetupFailureResponse(
                        request = request,
                        providerName = providerName,
                        modelName = modelName,
                        startedAt = startedAt,
                        promptLength = prompt.length,
                        errorType = "BASE_URL_MISSING",
                        toolSupportPolicy = toolSupportPolicy
                    )
                }

                val apiKey = apiKeyProvider(providerProfile.providerId)
                if (isApiKeyRequired(providerProfile.providerType) && apiKey.isNullOrBlank()) {
                    return createApiKeyMissingResponse(
                        request = request,
                        providerName = providerName,
                        modelName = modelName,
                        startedAt = startedAt,
                        promptLength = prompt.length,
                        toolSupportPolicy = toolSupportPolicy
                    )
                }

                val providerResult = openAiCompatibleProviderAdapter.callChatCompletions(
                    prompt = prompt,
                    modelName = modelName,
                    providerProfile = providerProfile,
                    apiKey = apiKey
                )

                return if (providerResult.success) {
                    createProviderSuccessResponse(
                        request = request,
                        rawText = providerResult.rawText,
                        providerName = providerResult.providerName,
                        modelName = providerResult.modelName.ifBlank { modelName },
                        latencySec = providerResult.latencySec,
                        promptLength = prompt.length,
                        providerResponseSummary = providerResult.responseSummary,
                        toolSupportPolicy = toolSupportPolicy
                    )
                } else {
                    createProviderFailureResponse(
                        request = request,
                        providerName = providerResult.providerName.ifBlank { providerName },
                        modelName = providerResult.modelName.ifBlank { modelName },
                        latencySec = providerResult.latencySec,
                        promptLength = prompt.length,
                        errorType = providerResult.errorType ?: "UNKNOWN",
                        errorMessage = providerResult.errorMessage ?: "OpenAI-compatible Provider 호출에 실패했습니다.",
                        responseSummary = providerResult.responseSummary,
                        toolSupportPolicy = toolSupportPolicy
                    )
                }
            }

            createProviderSetupFailureResponse(
                request = request,
                providerName = providerName,
                modelName = modelName,
                startedAt = startedAt,
                promptLength = prompt.length,
                errorType = "PROVIDER_MISSING",
                toolSupportPolicy = toolSupportPolicy
            )
        } catch (throwable: Throwable) {
            createFailureResponse(
                request = request,
                error = throwable,
                startedAt = startedAt
            )
        }
    }

    private fun createNativeGroundingSuccessResponse(
        request: ConversationRequest,
        rawText: String,
        providerName: String,
        modelName: String,
        promptLength: Int,
        nativeResult: GeminiNativeResult,
        toolSupportPolicy: ToolSupportPolicy? = null
    ): ConversationResponse {
        val thinkingSplit = splitThinkingTaggedContent(rawText)
        val displayText = thinkingSplit.finalText.ifBlank { rawText.trim() }

        if (displayText.isBlank()) {
            return createNativeGroundingFailureResponse(
                request = request,
                providerName = providerName,
                modelName = modelName,
                promptLength = promptLength,
                nativeResult = nativeResult.copy(
                    success = false,
                    errorType = "INVALID_RESPONSE",
                    errorMessage = "Gemini native 응답에서 표시 가능한 답변을 찾지 못했습니다."
                ),
                toolSupportPolicy = toolSupportPolicy
            )
        }

        val now = System.currentTimeMillis()
        val providerResponseSummary = buildGeminiNativeProviderResponseSummary(nativeResult, displayText.length)
        val thinkingSummaryTrace = buildGeminiNativeThinkingSummaryTrace(nativeResult, thinkingSplit)
        val webSearchGroundingTrace = buildNativeWebSearchGroundingTraceText(
            request = request,
            providerType = ProviderType.GOOGLE,
            nativeResult = nativeResult,
            errorType = null
        )
        val traceText = buildNativeTraceText(
            request = request,
            providerName = providerName,
            modelName = modelName,
            promptLength = promptLength,
            responseLength = displayText.length,
            status = "COMPLETED",
            latencySec = nativeResult.latencySec,
            errorType = null,
            toolSupportPolicy = toolSupportPolicy,
            thinkingSummaryTrace = thinkingSummaryTrace,
            providerResponseSummary = providerResponseSummary,
            webSearchGroundingTrace = webSearchGroundingTrace
        )
        val baseRun = createDummyConversationRun(request.sessionId)

        val runInfo = baseRun.copy(
            runId = request.requestId,
            userInput = request.userInput,
            status = ConversationRunStatus.COMPLETED,
            totalLatencySec = nativeResult.latencySec,
            totalRetryCount = 0,
            workerResults = baseRun.workerResults.mapIndexed { index, worker ->
                if (index == 0) {
                    worker.copy(
                        status = "COMPLETED",
                        providerName = providerName,
                        modelName = modelName,
                        latencySec = nativeResult.latencySec,
                        retryCount = 0,
                        errorType = "",
                        outputSummary = "Gemini native grounding 호출 완료",
                        rawOutput = buildString {
                            appendLine(displayText.take(800))
                            appendLine()
                            appendLine("[PROVIDER_CALL]")
                            appendLine("providerName: $providerName")
                            appendLine("modelName: $modelName")
                            appendLine("promptLength: $promptLength")
                            appendLine("responseLength: ${displayText.length}")
                            appendLine("latencySec: ${nativeResult.latencySec}")
                            appendLine("actualApiCall: true")
                            if (toolSupportPolicy != null) {
                                appendLine()
                                appendLine(toolSupportPolicy.toTraceSummary())
                            }
                            appendLine()
                            appendLine(thinkingSummaryTrace)
                            appendLine()
                            appendLine("[PROVIDER_RESPONSE_SUMMARY]")
                            appendLine(providerResponseSummary)
                            appendLine()
                            appendLine(webSearchGroundingTrace)
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
                    title = "Gemini native grounding 응답",
                    content = displayText,
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
            rawText = displayText,
            providerName = providerName,
            modelName = modelName,
            latencySec = nativeResult.latencySec,
            errorType = null,
            runInfo = runInfo,
            createdAt = now
        )
    }

    private fun createNativeGroundingFallbackSuccessResponse(
        request: ConversationRequest,
        rawText: String,
        providerName: String,
        modelName: String,
        promptLength: Int,
        nativeResult: GeminiNativeResult,
        fallbackResult: GeminiProviderResult,
        groundingExecuted: Boolean,
        toolSupportPolicy: ToolSupportPolicy? = null
    ): ConversationResponse {
        val thinkingSplit = splitThinkingTaggedContent(rawText)
        val displayText = thinkingSplit.finalText

        if (displayText.isBlank()) {
            return createNativeGroundingFailureResponse(
                request = request,
                providerName = providerName,
                modelName = modelName,
                promptLength = promptLength,
                nativeResult = nativeResult,
                fallbackAttempted = true,
                fallbackSucceeded = false,
                fallbackErrorType = "INVALID_RESPONSE",
                fallbackErrorMessage = "Fallback 일반 Gemini 응답에서 표시 가능한 답변을 찾지 못했습니다.",
                groundingExecuted = groundingExecuted,
                toolSupportPolicy = toolSupportPolicy
            )
        }

        val now = System.currentTimeMillis()
        val totalLatencySec = nativeResult.latencySec + fallbackResult.latencySec
        val providerResponseSummary = buildGeminiNativeProviderResponseSummary(nativeResult, responseLength = 0)
        val fallbackProviderResponseSummary = buildFallbackProviderResponseSummary(
            fallbackResult = fallbackResult,
            responseLength = displayText.length
        )
        val thinkingSummaryTrace = buildGeminiNativeThinkingSummaryTrace(nativeResult, thinkingSplit)
        val webSearchGroundingTrace = buildNativeWebSearchGroundingTraceText(
            request = request,
            providerType = ProviderType.GOOGLE,
            nativeResult = nativeResult,
            errorType = nativeResult.errorType ?: "GROUNDING_FAILED",
            fallbackAllowed = true,
            fallbackAttempted = true,
            fallbackSucceeded = true,
            fallbackErrorType = null,
            finalAnswerSource = "FALLBACK_GENERAL",
            groundingExecuted = groundingExecuted
        )
        val traceText = buildNativeTraceText(
            request = request,
            providerName = providerName,
            modelName = modelName,
            promptLength = promptLength,
            responseLength = displayText.length,
            status = "COMPLETED",
            latencySec = totalLatencySec,
            errorType = null,
            toolSupportPolicy = toolSupportPolicy,
            thinkingSummaryTrace = thinkingSummaryTrace,
            providerResponseSummary = providerResponseSummary,
            webSearchGroundingTrace = webSearchGroundingTrace,
            fallbackProviderResponseSummary = fallbackProviderResponseSummary
        )
        val baseRun = createDummyConversationRun(request.sessionId)

        val runInfo = baseRun.copy(
            runId = request.requestId,
            userInput = request.userInput,
            status = ConversationRunStatus.COMPLETED,
            totalLatencySec = totalLatencySec,
            totalRetryCount = 0,
            workerResults = baseRun.workerResults.mapIndexed { index, worker ->
                if (index == 0) {
                    worker.copy(
                        status = "COMPLETED",
                        providerName = providerName,
                        modelName = modelName,
                        latencySec = totalLatencySec,
                        retryCount = 0,
                        errorType = "",
                        outputSummary = "Web Search 실패 후 일반 Gemini fallback 호출 완료",
                        rawOutput = buildString {
                            appendLine(displayText.take(800))
                            appendLine()
                            appendLine("[PROVIDER_CALL]")
                            appendLine("providerName: $providerName")
                            appendLine("modelName: $modelName")
                            appendLine("promptLength: $promptLength")
                            appendLine("responseLength: ${displayText.length}")
                            appendLine("latencySec: $totalLatencySec")
                            appendLine("actualApiCall: true")
                            appendLine("finalAnswerSource: FALLBACK_GENERAL")
                            if (toolSupportPolicy != null) {
                                appendLine()
                                appendLine(toolSupportPolicy.toTraceSummary())
                            }
                            appendLine()
                            appendLine(thinkingSummaryTrace)
                            appendLine()
                            appendLine("[PROVIDER_RESPONSE_SUMMARY]")
                            appendLine(providerResponseSummary)
                            appendLine()
                            appendLine("[FALLBACK_PROVIDER_RESPONSE_SUMMARY]")
                            appendLine(fallbackProviderResponseSummary)
                            appendLine()
                            appendLine(webSearchGroundingTrace)
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
                    title = "Provider 응답",
                    content = displayText,
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
            rawText = displayText,
            providerName = providerName,
            modelName = modelName,
            latencySec = totalLatencySec,
            errorType = null,
            runInfo = runInfo,
            createdAt = now
        )
    }

    private fun createNativeGroundingFailureResponse(
        request: ConversationRequest,
        providerName: String,
        modelName: String,
        promptLength: Int,
        nativeResult: GeminiNativeResult,
        fallbackAttempted: Boolean = false,
        fallbackSucceeded: Boolean = false,
        fallbackErrorType: String? = null,
        fallbackErrorMessage: String? = null,
        groundingExecuted: Boolean = true,
        toolSupportPolicy: ToolSupportPolicy? = null
    ): ConversationResponse {
        val now = System.currentTimeMillis()
        val errorType = nativeResult.errorType ?: "GROUNDING_FAILED"
        val baseErrorMessage = buildUserFacingErrorMessage(
            errorType = errorType,
            fallbackMessage = nativeResult.errorMessage ?: "Gemini native grounding 호출에 실패했습니다."
        )
        val safeErrorMessage = if (fallbackAttempted && !fallbackSucceeded) {
            buildString {
                appendLine("Web Search grounding 실패 후 일반 답변 fallback도 실패했습니다.")
                appendLine()
                appendLine("groundingErrorType: $errorType")
                appendLine("groundingMessage: $baseErrorMessage")
                appendLine("fallbackErrorType: ${fallbackErrorType ?: "UNKNOWN"}")
                if (!fallbackErrorMessage.isNullOrBlank()) {
                    appendLine("fallbackMessage: ${fallbackErrorMessage.take(300)}")
                }
            }.trim()
        } else {
            baseErrorMessage
        }
        val providerResponseSummary = buildGeminiNativeProviderResponseSummary(nativeResult, responseLength = 0)
        val fallbackProviderResponseSummary = if (fallbackAttempted) {
            buildString {
                appendLine("adapter=GoogleGeminiProviderAdapter")
                appendLine("actualApiCall=true")
                appendLine("status=FAILED")
                appendLine("errorType=${fallbackErrorType ?: "UNKNOWN"}")
                appendLine("latencySec=unknown")
                appendLine("responseLength=0")
            }.trim()
        } else {
            null
        }
        val thinkingSummaryTrace = buildGeminiNativeThinkingSummaryTrace(nativeResult, ThinkingSplitResult.notDetected(0))
        val webSearchGroundingTrace = buildNativeWebSearchGroundingTraceText(
            request = request,
            providerType = ProviderType.GOOGLE,
            nativeResult = nativeResult,
            errorType = errorType,
            fallbackAllowed = request.webSearchFallbackEnabled && isWebSearchFallbackAllowedError(errorType),
            fallbackAttempted = fallbackAttempted,
            fallbackSucceeded = fallbackSucceeded,
            fallbackErrorType = fallbackErrorType,
            finalAnswerSource = "ERROR",
            groundingExecuted = groundingExecuted
        )
        val traceText = buildNativeTraceText(
            request = request,
            providerName = providerName,
            modelName = modelName,
            promptLength = promptLength,
            responseLength = 0,
            status = "FAILED",
            latencySec = nativeResult.latencySec,
            errorType = errorType,
            toolSupportPolicy = toolSupportPolicy,
            thinkingSummaryTrace = thinkingSummaryTrace,
            providerResponseSummary = providerResponseSummary,
            webSearchGroundingTrace = webSearchGroundingTrace,
            fallbackProviderResponseSummary = fallbackProviderResponseSummary
        )
        val baseRun = createDummyConversationRun(request.sessionId)

        val runInfo = baseRun.copy(
            runId = request.requestId,
            userInput = request.userInput,
            status = ConversationRunStatus.FAILED,
            totalLatencySec = nativeResult.latencySec,
            totalRetryCount = 0,
            workerResults = baseRun.workerResults.mapIndexed { index, worker ->
                when (index) {
                    0 -> worker.copy(
                        status = "FAILED",
                        providerName = providerName,
                        modelName = modelName,
                        latencySec = nativeResult.latencySec,
                        retryCount = 0,
                        errorType = errorType,
                        outputSummary = "Gemini native grounding 호출 실패: $errorType",
                        rawOutput = buildString {
                            appendLine(safeErrorMessage.take(800))
                            appendLine()
                            appendLine("[PROVIDER_CALL]")
                            appendLine("providerName: $providerName")
                            appendLine("modelName: $modelName")
                            appendLine("promptLength: $promptLength")
                            appendLine("responseLength: 0")
                            appendLine("latencySec: ${nativeResult.latencySec}")
                            appendLine("actualApiCall: true")
                            appendLine("errorType: $errorType")
                            if (toolSupportPolicy != null) {
                                appendLine()
                                appendLine(toolSupportPolicy.toTraceSummary())
                            }
                            appendLine()
                            appendLine(thinkingSummaryTrace)
                            appendLine()
                            appendLine("[PROVIDER_RESPONSE_SUMMARY]")
                            appendLine(providerResponseSummary)
                            if (!fallbackProviderResponseSummary.isNullOrBlank()) {
                                appendLine()
                                appendLine("[FALLBACK_PROVIDER_RESPONSE_SUMMARY]")
                                appendLine(fallbackProviderResponseSummary)
                            }
                            appendLine()
                            appendLine(webSearchGroundingTrace)
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
                        outputSummary = "Gemini native grounding 실패로 실행 건너뜀",
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
                    title = "Web Search grounding 실패",
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
            latencySec = nativeResult.latencySec,
            errorType = errorType,
            runInfo = runInfo,
            createdAt = now
        )
    }

    private fun createProviderSuccessResponse(
        request: ConversationRequest,
        rawText: String,
        providerName: String,
        modelName: String,
        latencySec: Double,
        promptLength: Int,
        providerResponseSummary: String? = null,
        toolSupportPolicy: ToolSupportPolicy? = null
    ): ConversationResponse {
        val thinkingSplit = splitThinkingTaggedContent(rawText)
        val displayText = thinkingSplit.finalText

        if (displayText.isBlank()) {
            return createThinkingOnlyResponse(
                request = request,
                providerName = providerName,
                modelName = modelName,
                latencySec = latencySec,
                promptLength = promptLength,
                providerResponseSummary = providerResponseSummary,
                toolSupportPolicy = toolSupportPolicy,
                thinkingSplit = thinkingSplit
            )
        }

        val now = System.currentTimeMillis()
        val traceText = buildTraceText(
            ragContext = request.ragContext,
            providerName = providerName,
            modelName = modelName,
            promptLength = promptLength,
            responseLength = displayText.length,
            actualApiCall = true,
            errorType = null,
            providerResponseSummary = providerResponseSummary,
            status = "COMPLETED",
            latencySec = latencySec,
            toolSupportPolicy = toolSupportPolicy,
            thinkingSplit = thinkingSplit,
            request = request
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
                        outputSummary = "Provider 실제 호출 완료",
                        rawOutput = buildString {
                            appendLine(displayText.take(800))
                            appendLine()
                            appendLine("[PROVIDER_CALL]")
                            appendLine("providerName: $providerName")
                            appendLine("modelName: $modelName")
                            appendLine("promptLength: $promptLength")
                            appendLine("responseLength: ${displayText.length}")
                            appendLine("rawResponseLength: ${rawText.length}")
                            appendLine("latencySec: $latencySec")
                            appendLine("actualApiCall: true")
                            if (toolSupportPolicy != null) {
                                appendLine()
                                appendLine(toolSupportPolicy.toTraceSummary())
                            }
                            appendLine()
                            appendThinkingSummaryTrace(thinkingSplit)
                            if (!providerResponseSummary.isNullOrBlank()) {
                                appendLine()
                                appendLine("[PROVIDER_RESPONSE_SUMMARY]")
                                appendLine(providerResponseSummary.take(1200))
                            }
                            appendLine()
                            appendWebSearchGroundingTrace(request, toolSupportPolicy?.providerType)
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
                    title = "Provider 응답",
                    content = displayText,
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
            rawText = displayText,
            providerName = providerName,
            modelName = modelName,
            latencySec = latencySec,
            errorType = null,
            runInfo = runInfo,
            createdAt = now
        )
    }

    private fun createThinkingOnlyResponse(
        request: ConversationRequest,
        providerName: String,
        modelName: String,
        latencySec: Double,
        promptLength: Int,
        providerResponseSummary: String? = null,
        toolSupportPolicy: ToolSupportPolicy? = null,
        thinkingSplit: ThinkingSplitResult
    ): ConversationResponse {
        val now = System.currentTimeMillis()
        val errorType = "INVALID_RESPONSE"
        val errorText = "표시 가능한 답변이 없습니다. 모델 응답에서 thinking/thought 태그형 내용만 감지되어 본문 표시를 중단했습니다."
        val traceText = buildTraceText(
            ragContext = request.ragContext,
            providerName = providerName,
            modelName = modelName,
            promptLength = promptLength,
            responseLength = 0,
            actualApiCall = true,
            errorType = errorType,
            providerResponseSummary = providerResponseSummary,
            status = "FAILED",
            latencySec = latencySec,
            toolSupportPolicy = toolSupportPolicy,
            thinkingSplit = thinkingSplit,
            request = request
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
                        outputSummary = "Provider 응답에서 표시 가능한 본문 없음",
                        rawOutput = buildString {
                            appendLine(errorText)
                            appendLine()
                            appendLine("[PROVIDER_CALL]")
                            appendLine("providerName: $providerName")
                            appendLine("modelName: $modelName")
                            appendLine("promptLength: $promptLength")
                            appendLine("responseLength: 0")
                            appendLine("rawResponseLength: ${thinkingSplit.originalLength}")
                            appendLine("latencySec: $latencySec")
                            appendLine("actualApiCall: true")
                            appendLine("errorType: $errorType")
                            if (toolSupportPolicy != null) {
                                appendLine()
                                appendLine(toolSupportPolicy.toTraceSummary())
                            }
                            appendLine()
                            appendThinkingSummaryTrace(thinkingSplit)
                            if (!providerResponseSummary.isNullOrBlank()) {
                                appendLine()
                                appendLine("[PROVIDER_RESPONSE_SUMMARY]")
                                appendLine(providerResponseSummary.take(1200))
                            }
                            appendLine()
                            appendWebSearchGroundingTrace(request, toolSupportPolicy?.providerType)
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
                    title = "Provider 응답 표시 불가",
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
            errorType = errorType,
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
        responseSummary: String? = null,
        toolSupportPolicy: ToolSupportPolicy? = null
    ): ConversationResponse {
        val now = System.currentTimeMillis()
        val safeErrorMessage = buildUserFacingErrorMessage(errorType, errorMessage)
        val traceText = buildTraceText(
            ragContext = request.ragContext,
            providerName = providerName,
            modelName = modelName,
            promptLength = promptLength,
            responseLength = 0,
            actualApiCall = true,
            errorType = errorType,
            providerResponseSummary = responseSummary,
            status = "FAILED",
            latencySec = latencySec,
            toolSupportPolicy = toolSupportPolicy,
            request = request
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
                        outputSummary = "Provider 호출 실패: $errorType",
                        rawOutput = buildString {
                            appendLine(safeErrorMessage.take(800))
                            appendLine()
                            appendLine("[PROVIDER_CALL]")
                            appendLine("providerName: $providerName")
                            appendLine("modelName: $modelName")
                            appendLine("promptLength: $promptLength")
                            appendLine("responseLength: 0")
                            appendLine("latencySec: $latencySec")
                            appendLine("actualApiCall: true")
                            appendLine("errorType: $errorType")
                            if (toolSupportPolicy != null) {
                                appendLine()
                                appendLine(toolSupportPolicy.toTraceSummary())
                            }
                            if (!responseSummary.isNullOrBlank()) {
                                appendLine()
                                appendLine("[PROVIDER_RESPONSE_SUMMARY]")
                                appendLine(responseSummary.take(1200))
                            }
                            appendLine()
                            appendWebSearchGroundingTrace(request, toolSupportPolicy?.providerType)
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
                    title = "Provider 호출 실패",
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
        promptLength: Int,
        toolSupportPolicy: ToolSupportPolicy? = null
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
            errorType = null,
            status = "COMPLETED",
            latencySec = latencySec,
            toolSupportPolicy = toolSupportPolicy,
            request = request
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
                            if (toolSupportPolicy != null) {
                                appendLine()
                                appendLine(toolSupportPolicy.toTraceSummary())
                            }
                            appendLine()
                            appendWebSearchGroundingTrace(request, toolSupportPolicy?.providerType)
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

    private fun createProviderSetupFailureResponse(
        request: ConversationRequest,
        providerName: String,
        modelName: String,
        startedAt: Long,
        promptLength: Int,
        errorType: String,
        toolSupportPolicy: ToolSupportPolicy? = null
    ): ConversationResponse {
        val now = System.currentTimeMillis()
        val latencySec = ((now - startedAt).coerceAtLeast(1) / 1000.0)
        val errorText = buildUserFacingErrorMessage(errorType, "")
        val traceText = buildTraceText(
            ragContext = request.ragContext,
            providerName = providerName,
            modelName = modelName,
            promptLength = promptLength,
            responseLength = 0,
            actualApiCall = false,
            errorType = errorType,
            status = "FAILED",
            latencySec = latencySec,
            toolSupportPolicy = toolSupportPolicy,
            request = request
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
                        outputSummary = "Provider 설정 오류: $errorType",
                        rawOutput = buildString {
                            appendLine(errorText)
                            appendLine()
                            appendLine("[PROVIDER_CALL]")
                            appendLine("providerName: $providerName")
                            appendLine("providerId: $providerName")
                            appendLine("modelName: $modelName")
                            appendLine("promptLength: $promptLength")
                            appendLine("responseLength: 0")
                            appendLine("latencySec: $latencySec")
                            appendLine("actualApiCall: false")
                            appendLine("errorType: $errorType")
                            if (toolSupportPolicy != null) {
                                appendLine()
                                appendLine(toolSupportPolicy.toTraceSummary())
                            }
                            appendLine()
                            appendWebSearchGroundingTrace(request, toolSupportPolicy?.providerType)
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
                        outputSummary = "Provider 설정 오류로 실행 건너뜀",
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
                    title = "Provider 설정 필요",
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
            errorType = errorType,
            runInfo = runInfo,
            createdAt = now
        )
    }

    private fun createApiKeyMissingResponse(
        request: ConversationRequest,
        providerName: String,
        modelName: String,
        startedAt: Long,
        promptLength: Int,
        toolSupportPolicy: ToolSupportPolicy? = null
    ): ConversationResponse {
        val now = System.currentTimeMillis()
        val latencySec = ((now - startedAt).coerceAtLeast(1) / 1000.0)
        val errorText = buildUserFacingErrorMessage("API_KEY_MISSING", "")
        val traceText = buildTraceText(
            ragContext = request.ragContext,
            providerName = providerName,
            modelName = modelName,
            promptLength = promptLength,
            responseLength = 0,
            actualApiCall = false,
            errorType = "API_KEY_MISSING",
            status = "FAILED",
            latencySec = latencySec,
            toolSupportPolicy = toolSupportPolicy,
            request = request
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
                            appendLine("responseLength: 0")
                            appendLine("latencySec: $latencySec")
                            appendLine("actualApiCall: false")
                            appendLine("errorType: API_KEY_MISSING")
                            if (toolSupportPolicy != null) {
                                appendLine()
                                appendLine(toolSupportPolicy.toTraceSummary())
                            }
                            appendLine()
                            appendWebSearchGroundingTrace(request, toolSupportPolicy?.providerType)
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

    private fun createModelMissingResponse(
        request: ConversationRequest,
        providerName: String,
        modelName: String,
        startedAt: Long,
        promptLength: Int,
        toolSupportPolicy: ToolSupportPolicy? = null
    ): ConversationResponse {
        val now = System.currentTimeMillis()
        val latencySec = ((now - startedAt).coerceAtLeast(1) / 1000.0)
        val errorText = buildUserFacingErrorMessage("MODEL_MISSING", "")
        val traceText = buildTraceText(
            ragContext = request.ragContext,
            providerName = providerName,
            modelName = modelName,
            promptLength = promptLength,
            responseLength = 0,
            actualApiCall = false,
            errorType = "MODEL_MISSING",
            status = "FAILED",
            latencySec = latencySec,
            toolSupportPolicy = toolSupportPolicy,
            request = request
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
                        errorType = "MODEL_MISSING",
                        outputSummary = "기본 대화 모델 미지정",
                        rawOutput = buildString {
                            appendLine(errorText)
                            appendLine()
                            appendLine("[PROVIDER_CALL]")
                            appendLine("providerName: $providerName")
                            appendLine("modelName: $modelName")
                            appendLine("status: FAILED")
                            appendLine("promptLength: $promptLength")
                            appendLine("responseLength: 0")
                            appendLine("latencySec: $latencySec")
                            appendLine("actualApiCall: false")
                            appendLine("errorType: MODEL_MISSING")
                            if (toolSupportPolicy != null) {
                                appendLine()
                                appendLine(toolSupportPolicy.toTraceSummary())
                            }
                            appendLine()
                            appendWebSearchGroundingTrace(request, toolSupportPolicy?.providerType)
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
                        errorType = "MODEL_MISSING",
                        outputSummary = "기본 모델 없음으로 실행 건너뜀",
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
                    title = "기본 모델 필요",
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
            errorType = "MODEL_MISSING",
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

    private fun shouldAttemptWebSearchFallback(
        request: ConversationRequest,
        errorType: String?
    ): Boolean {
        return request.webSearchEnabled &&
                request.webSearchFallbackEnabled &&
                isWebSearchFallbackAllowedError(errorType)
    }

    private fun isWebSearchFallbackAllowedError(errorType: String?): Boolean {
        return when (errorType) {
            "GROUNDING_FAILED",
            "MODEL_UNSUPPORTED_TOOL",
            "WEB_SEARCH_NOT_SUPPORTED",
            "TIMEOUT",
            "SERVER_ERROR" -> true
            else -> false
        }
    }

    private fun buildFallbackProviderResponseSummary(
        fallbackResult: GeminiProviderResult,
        responseLength: Int
    ): String {
        return buildString {
            appendLine("adapter=GoogleGeminiProviderAdapter")
            appendLine("actualApiCall=true")
            appendLine("status=${if (fallbackResult.success) "COMPLETED" else "FAILED"}")
            appendLine("errorType=${fallbackResult.errorType ?: "NONE"}")
            appendLine("latencySec=${fallbackResult.latencySec}")
            appendLine("responseLength=$responseLength")
            appendLine("toolCallDetected=${fallbackResult.toolCallDetected}")
            appendLine("toolCallCount=${fallbackResult.toolCallCount}")
            appendLine("finishReason=${fallbackResult.finishReason ?: "UNKNOWN"}")
            if (!fallbackResult.responseSummary.isNullOrBlank()) {
                appendLine("responseSummary=${fallbackResult.responseSummary.take(500)}")
            }
        }.trim()
    }

    private fun buildNativeTraceText(
        request: ConversationRequest,
        providerName: String,
        modelName: String,
        promptLength: Int,
        responseLength: Int,
        status: String,
        latencySec: Double,
        errorType: String?,
        toolSupportPolicy: ToolSupportPolicy?,
        thinkingSummaryTrace: String,
        providerResponseSummary: String,
        webSearchGroundingTrace: String,
        fallbackProviderResponseSummary: String? = null
    ): String {
        return buildString {
            appendLine("입력 수신 → PromptBuilder 실행 → Provider 확인 → Gemini native grounding 호출 → 응답 생성 → 화면 갱신")
            appendLine()
            appendLine("[PROVIDER_CALL]")
            appendLine("providerName: $providerName")
            appendLine("providerId: $providerName")
            appendLine("modelName: $modelName")
            appendLine("actualApiCall: true")
            appendLine("status: $status")
            appendLine("promptLength: $promptLength")
            appendLine("responseLength: $responseLength")
            appendLine("latencySec: $latencySec")
            appendLine("errorType: ${errorType ?: "NONE"}")
            if (toolSupportPolicy != null) {
                appendLine()
                appendLine(toolSupportPolicy.toTraceSummary())
            }
            appendLine()
            appendLine(thinkingSummaryTrace)
            appendLine()
            appendLine("[PROVIDER_RESPONSE_SUMMARY]")
            appendLine(providerResponseSummary.take(1200))
            if (!fallbackProviderResponseSummary.isNullOrBlank()) {
                appendLine()
                appendLine("[FALLBACK_PROVIDER_RESPONSE_SUMMARY]")
                appendLine(fallbackProviderResponseSummary.take(1200))
            }
            appendLine()
            appendLine(webSearchGroundingTrace)
            appendLine()
            append(buildRagTraceText(request.ragContext))
        }.trim()
    }

    private fun buildGeminiNativeProviderResponseSummary(
        result: GeminiNativeResult,
        responseLength: Int
    ): String {
        return buildString {
            appendLine("providerType=GOOGLE")
            appendLine("adapter=GeminiNativeProviderAdapter")
            appendLine("endpoint=generateContent")
            appendLine("finishReason=${result.finishReason ?: "UNKNOWN"}")
            appendLine("answerPartCount=${result.answerPartCount}")
            appendLine("thoughtPartCount=${result.thoughtPartCount}")
            appendLine("responseLength=$responseLength")
            appendLine("actualApiCall=true")
            appendLine("success=${result.success}")
            appendLine("errorType=${result.errorType ?: "NONE"}")
            appendLine("groundingUsed=${result.groundingUsed}")
            appendLine("citationCount=${result.citations.size}")
            appendLine("searchQueryCount=${result.searchQueries.size}")
            if (!result.rawMetadataSummary.isNullOrBlank()) {
                appendLine("rawMetadataSummary=${result.rawMetadataSummary.take(500)}")
            }
        }.trim()
    }

    private fun buildGeminiNativeThinkingSummaryTrace(
        nativeResult: GeminiNativeResult,
        thinkingSplit: ThinkingSplitResult
    ): String {
        val nativeThinkingPreview = nativeResult.thinkingSummary
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
            ?.take(300)
            .orEmpty()
        val detected = nativeThinkingPreview.isNotBlank() || thinkingSplit.thinkingSummaryDetected
        val length = nativeResult.thinkingSummary?.length ?: thinkingSplit.thinkingSummaryLength
        val preview = nativeThinkingPreview.ifBlank { thinkingSplit.thinkingSummaryPreview }

        return buildString {
            appendLine("[THINKING_SUMMARY]")
            appendLine("THINKING_SUMMARY_DETECTED=$detected")
            appendLine("thinkingSummaryLength=$length")
            if (detected) {
                appendLine("thinkingSummaryPreview=$preview")
            }
        }.trim()
    }

    private fun buildNativeWebSearchGroundingTraceText(
        request: ConversationRequest,
        providerType: ProviderType,
        nativeResult: GeminiNativeResult,
        errorType: String?,
        fallbackAllowed: Boolean = request.webSearchFallbackEnabled && isWebSearchFallbackAllowedError(errorType ?: nativeResult.errorType),
        fallbackAttempted: Boolean = false,
        fallbackSucceeded: Boolean = false,
        fallbackErrorType: String? = null,
        finalAnswerSource: String = if (errorType == null && nativeResult.success) "GROUNDING" else "ERROR",
        groundingExecuted: Boolean = true
    ): String {
        val modelSupportsWebSearch = request.selectedModelWebSearchStatus == CapabilityStatus.USER_ENABLED ||
                request.selectedModelWebSearchStatus == CapabilityStatus.SUPPORTED
        val groundingErrorType = errorType ?: nativeResult.errorType
        val reason = groundingErrorType ?: if (finalAnswerSource == "GROUNDING") "NONE" else "UNKNOWN"
        val citationCount = if (finalAnswerSource == "GROUNDING") nativeResult.citations.size else 0
        val searchQueryCount = if (finalAnswerSource == "GROUNDING") nativeResult.searchQueries.size else 0

        return buildString {
            appendLine("[WEB_SEARCH_GROUNDING]")
            appendLine("requested: true")
            appendLine("providerType: ${providerType.name}")
            appendLine("modelWebSearchStatus: ${request.selectedModelWebSearchStatus?.name ?: "UNKNOWN"}")
            appendLine("nativeGroundingAvailable: true")
            appendLine("canAttemptGrounding: $modelSupportsWebSearch")
            appendLine("groundingExecuted: $groundingExecuted")
            appendLine("groundingUsed: ${finalAnswerSource == "GROUNDING" && nativeResult.groundingUsed}")
            appendLine("citationCount: $citationCount")
            appendLine("searchQueryCount: $searchQueryCount")
            appendLine("modelSupportsWebSearch: $modelSupportsWebSearch")
            appendLine("fallbackAllowed: $fallbackAllowed")
            appendLine("fallbackAttempted: $fallbackAttempted")
            appendLine("fallbackSucceeded: $fallbackSucceeded")
            appendLine("groundingErrorType: ${groundingErrorType ?: "NONE"}")
            appendLine("fallbackErrorType: ${fallbackErrorType ?: "NONE"}")
            appendLine("finalAnswerSource: $finalAnswerSource")
            appendLine("errorType: ${groundingErrorType ?: "NONE"}")
            appendLine("reason: $reason")
            if (finalAnswerSource == "GROUNDING" && nativeResult.searchQueries.isNotEmpty()) {
                appendLine("searchQueries:")
                nativeResult.searchQueries.take(5).forEachIndexed { index, query ->
                    appendLine("${index + 1}. ${query.take(160)}")
                }
            }
            if (finalAnswerSource == "GROUNDING" && nativeResult.citations.isNotEmpty()) {
                appendLine("sources:")
                nativeResult.citations.take(5).forEachIndexed { index, citation ->
                    appendLine("${index + 1}. title=${citation.title ?: "UNKNOWN"}")
                    appendLine("   url=${citation.url?.take(240) ?: "UNKNOWN"}")
                    if (!citation.snippet.isNullOrBlank()) {
                        appendLine("   snippet=${citation.snippet.take(240)}")
                    }
                }
            }
        }.trim()
    }

    private fun buildTraceText(
        ragContext: RagContext?,
        providerName: String,
        modelName: String,
        promptLength: Int,
        responseLength: Int,
        actualApiCall: Boolean,
        errorType: String?,
        providerResponseSummary: String? = null,
        status: String = if (errorType == null) "COMPLETED" else "FAILED",
        latencySec: Double? = null,
        toolSupportPolicy: ToolSupportPolicy? = null,
        thinkingSplit: ThinkingSplitResult? = null,
        request: ConversationRequest? = null
    ): String {
        return buildString {
            appendLine("입력 수신 → PromptBuilder 실행 → Provider 확인 → 응답 생성 → 화면 갱신")
            appendLine()
            appendLine("[PROVIDER_CALL]")
            appendLine("providerName: $providerName")
            appendLine("providerId: $providerName")
            appendLine("modelName: $modelName")
            appendLine("actualApiCall: $actualApiCall")
            appendLine("status: $status")
            appendLine("promptLength: $promptLength")
            appendLine("responseLength: $responseLength")
            appendLine("latencySec: ${latencySec ?: "unknown"}")
            appendLine("errorType: ${errorType ?: "NONE"}")
            if (toolSupportPolicy != null) {
                appendLine()
                appendLine(toolSupportPolicy.toTraceSummary())
            }
            appendLine()
            appendThinkingSummaryTrace(thinkingSplit ?: ThinkingSplitResult.notDetected(originalLength = responseLength))
            if (!providerResponseSummary.isNullOrBlank()) {
                appendLine()
                appendLine("[PROVIDER_RESPONSE_SUMMARY]")
                appendLine(providerResponseSummary.take(1200))
            }
            appendLine()
            append(buildWebSearchGroundingTraceText(request, toolSupportPolicy?.providerType))
            appendLine()
            append(buildRagTraceText(ragContext))
        }.trim()
    }

    private fun splitThinkingTaggedContent(rawText: String): ThinkingSplitResult {
        if (rawText.isBlank()) {
            return ThinkingSplitResult.notDetected(originalLength = rawText.length)
        }

        val thinkingTagRegex = Regex(
            pattern = """(?is)<\s*(thought|thinking|thoughts)\b[^>]*>(.*?)<\s*/\s*\1\s*>"""
        )
        val matches = thinkingTagRegex.findAll(rawText).toList()
        if (matches.isEmpty()) {
            return ThinkingSplitResult.notDetected(
                originalLength = rawText.length,
                finalText = rawText.trim()
            )
        }

        val extractedThinking = matches
            .mapNotNull { match ->
                match.groups[2]
                    ?.value
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
            .joinToString("\n\n")
            .trim()

        val finalText = rawText
            .replace(thinkingTagRegex, "")
            .replace(Regex("""[ \t]+\n"""), "\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()

        return ThinkingSplitResult(
            finalText = finalText,
            thinkingSummaryDetected = true,
            thinkingSummaryLength = extractedThinking.length,
            thinkingSummaryPreview = buildThinkingSummaryPreview(extractedThinking),
            originalLength = rawText.length
        )
    }

    private fun buildThinkingSummaryPreview(thinkingSummary: String): String {
        if (thinkingSummary.isBlank()) return ""
        return thinkingSummary
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(300)
    }

    private fun StringBuilder.appendThinkingSummaryTrace(thinkingSplit: ThinkingSplitResult) {
        appendLine("[THINKING_SUMMARY]")
        appendLine("THINKING_SUMMARY_DETECTED=${thinkingSplit.thinkingSummaryDetected}")
        appendLine("thinkingSummaryLength=${thinkingSplit.thinkingSummaryLength}")
        if (thinkingSplit.thinkingSummaryDetected) {
            appendLine("thinkingSummaryPreview=${thinkingSplit.thinkingSummaryPreview}")
        }
    }

    private fun StringBuilder.appendWebSearchGroundingTrace(
        request: ConversationRequest,
        providerType: ProviderType?
    ) {
        append(buildWebSearchGroundingTraceText(request, providerType))
    }

    private fun buildWebSearchGroundingTraceText(
        request: ConversationRequest?,
        providerType: ProviderType?
    ): String {
        val policy = WebSearchUsageResolver.resolve(
            enabledByUser = request?.webSearchEnabled ?: false,
            providerType = providerType,
            modelWebSearchStatus = request?.selectedModelWebSearchStatus,
            nativeGroundingAvailable = providerType == ProviderType.GOOGLE,
            fallbackAllowed = request?.let { it.webSearchEnabled && it.webSearchFallbackEnabled } ?: false
        )
        return policy.toTraceSummary()
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

    private fun buildUserFacingErrorMessage(
        errorType: String,
        fallbackMessage: String
    ): String {
        return when (errorType) {
            "API_KEY_MISSING" -> "이 Provider는 API Key가 필요합니다. Provider 관리에서 API Key를 입력하세요."
            "PROVIDER_MISSING" -> "Provider 설정을 찾지 못했습니다. Provider 관리에서 Provider를 확인하세요."
            "BASE_URL_MISSING" -> "Provider Base URL이 설정되지 않았습니다. Provider 관리에서 Base URL을 입력하세요."
            "MODEL_MISSING" -> "대화모드 기본 모델이 설정되지 않았습니다. Model 관리에서 기본 모델을 지정하세요."
            "INVALID_REQUEST" -> "요청 형식 또는 모델명이 올바르지 않습니다. 모델명과 Provider 설정을 확인하세요."
            "INVALID_RESPONSE" -> "응답에서 표시 가능한 텍스트를 찾지 못했습니다."
            "TOOL_CALL_NOT_SUPPORTED" -> "모델이 도구 호출을 요청했지만, 현재 대화모드에서는 도구 실행을 아직 지원하지 않습니다."
            "WEB_SEARCH_NOT_SUPPORTED" -> "현재 선택한 Provider에서는 Web Search grounding을 지원하지 않습니다. Google Provider와 Web Search 지원 모델을 선택하세요."
            "MODEL_UNSUPPORTED_TOOL" -> "현재 선택한 모델의 Web Search capability가 활성화되어 있지 않습니다. Model 관리에서 webSearch capability를 USER_ENABLED 또는 SUPPORTED 상태로 설정하세요."
            "GROUNDING_FAILED" -> "Gemini native Web Search grounding 호출에 실패했습니다. 실행정보의 WEB_SEARCH_GROUNDING 섹션을 확인하세요."
            "SAFETY_BLOCKED" -> "Provider 안전 정책에 의해 응답이 차단되었습니다. 입력 내용을 조정한 뒤 다시 시도하세요."
            "RATE_LIMIT" -> "요청 한도에 도달했습니다. 잠시 후 다시 시도하세요."
            "SERVER_ERROR" -> "Provider 서버 오류가 발생했습니다. 잠시 후 다시 시도하세요."
            "AUTH_FAILED" -> "Provider 인증에 실패했습니다. API Key와 Provider 설정을 확인하세요."
            "NETWORK_ERROR" -> "Provider 서버에 연결하지 못했습니다. Base URL, 네트워크, 방화벽, 서버 실행 상태를 확인하세요."
            "TIMEOUT" -> "Provider 서버 응답 시간이 초과되었습니다. Base URL, 네트워크, 서버 실행 상태를 확인하세요."
            else -> fallbackMessage.takeIf { it.isNotBlank() } ?: "알 수 없는 오류가 발생했습니다. 실행정보를 확인하세요."
        }
    }

    private fun isModelMissing(modelName: String): Boolean {
        return modelName.equals("MODEL_MISSING", ignoreCase = true) ||
                modelName.isBlank()
    }

    private fun resolveProviderName(selectedProvider: String?): String {
        return selectedProvider?.trim().orEmpty()
    }

    private fun resolveModelName(selectedModel: String?): String {
        return selectedModel?.trim().orEmpty()
    }

    private fun requiresApiKey(providerName: String): Boolean {
        return !providerName.equals("DUMMY", ignoreCase = true)
    }

    private fun isGoogleProvider(
        providerName: String,
        providerProfile: ProviderProfile?
    ): Boolean {
        return providerProfile?.providerType == ProviderType.GOOGLE ||
                providerName.equals("GOOGLE", ignoreCase = true) ||
                providerName.contains("google", ignoreCase = true) ||
                providerName.contains("gemini", ignoreCase = true)
    }

    private fun isOpenAiCompatibleProvider(providerProfile: ProviderProfile?): Boolean {
        return when (providerProfile?.providerType) {
            ProviderType.OPENAI_COMPATIBLE,
            ProviderType.LOCAL,
            ProviderType.CUSTOM -> true
            else -> false
        }
    }

    private fun isApiKeyRequired(providerType: ProviderType): Boolean {
        return when (providerType) {
            ProviderType.OPENAI,
            ProviderType.OPENAI_COMPATIBLE,
            ProviderType.NVIDIA,
            ProviderType.GOOGLE -> true
            ProviderType.LOCAL,
            ProviderType.CUSTOM -> false
        }
    }
}

private data class ThinkingSplitResult(
    val finalText: String,
    val thinkingSummaryDetected: Boolean,
    val thinkingSummaryLength: Int,
    val thinkingSummaryPreview: String,
    val originalLength: Int
) {
    companion object {
        fun notDetected(
            originalLength: Int,
            finalText: String = ""
        ): ThinkingSplitResult {
            return ThinkingSplitResult(
                finalText = finalText,
                thinkingSummaryDetected = false,
                thinkingSummaryLength = 0,
                thinkingSummaryPreview = "",
                originalLength = originalLength
            )
        }
    }
}
