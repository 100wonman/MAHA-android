package com.maha.app

class ConversationEngine(
    private val promptBuilder: ConversationPromptBuilder = ConversationPromptBuilder(),
    private val apiKeyProvider: (providerName: String) -> String? = { null },
    private val providerProfileProvider: (providerId: String) -> ProviderProfile? = { null },
    private val googleGeminiProviderAdapter: GoogleGeminiProviderAdapter = GoogleGeminiProviderAdapter(),
    private val openAiCompatibleProviderAdapter: OpenAiCompatibleProviderAdapter = OpenAiCompatibleProviderAdapter()
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

            if (isModelMissing(modelName)) {
                return createModelMissingResponse(
                    request = request,
                    providerName = providerName.ifBlank { "PROVIDER_UNKNOWN" },
                    modelName = modelName,
                    startedAt = startedAt,
                    promptLength = prompt.length
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
                    errorType = "PROVIDER_MISSING"
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
                        promptLength = prompt.length,
                        providerResponseSummary = providerResult.responseSummary
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

            if (isOpenAiCompatibleProvider(providerProfile)) {
                if (providerProfile == null) {
                    return createProviderSetupFailureResponse(
                        request = request,
                        providerName = providerName,
                        modelName = modelName,
                        startedAt = startedAt,
                        promptLength = prompt.length,
                        errorType = "PROVIDER_MISSING"
                    )
                }

                if (providerProfile.baseUrl.isBlank()) {
                    return createProviderSetupFailureResponse(
                        request = request,
                        providerName = providerName,
                        modelName = modelName,
                        startedAt = startedAt,
                        promptLength = prompt.length,
                        errorType = "BASE_URL_MISSING"
                    )
                }

                val apiKey = apiKeyProvider(providerProfile.providerId)
                if (isApiKeyRequired(providerProfile.providerType) && apiKey.isNullOrBlank()) {
                    return createApiKeyMissingResponse(
                        request = request,
                        providerName = providerName,
                        modelName = modelName,
                        startedAt = startedAt,
                        promptLength = prompt.length
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
                        providerResponseSummary = providerResult.responseSummary
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
                        responseSummary = providerResult.responseSummary
                    )
                }
            }

            createProviderSetupFailureResponse(
                request = request,
                providerName = providerName,
                modelName = modelName,
                startedAt = startedAt,
                promptLength = prompt.length,
                errorType = "PROVIDER_MISSING"
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
        promptLength: Int,
        providerResponseSummary: String? = null
    ): ConversationResponse {
        val now = System.currentTimeMillis()
        val traceText = buildTraceText(
            ragContext = request.ragContext,
            providerName = providerName,
            modelName = modelName,
            promptLength = promptLength,
            responseLength = rawText.length,
            actualApiCall = true,
            errorType = null,
            providerResponseSummary = providerResponseSummary,
            status = "COMPLETED",
            latencySec = latencySec
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
                            appendLine(rawText.take(800))
                            appendLine()
                            appendLine("[PROVIDER_CALL]")
                            appendLine("providerName: $providerName")
                            appendLine("modelName: $modelName")
                            appendLine("promptLength: $promptLength")
                            appendLine("responseLength: ${rawText.length}")
                            appendLine("latencySec: $latencySec")
                            appendLine("actualApiCall: true")
                            if (!providerResponseSummary.isNullOrBlank()) {
                                appendLine()
                                appendLine("[PROVIDER_RESPONSE_SUMMARY]")
                                appendLine(providerResponseSummary.take(1200))
                            }
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
            latencySec = latencySec
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
            errorType = null,
            status = "COMPLETED",
            latencySec = latencySec
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

    private fun createProviderSetupFailureResponse(
        request: ConversationRequest,
        providerName: String,
        modelName: String,
        startedAt: Long,
        promptLength: Int,
        errorType: String
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
            latencySec = latencySec
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
        promptLength: Int
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
            latencySec = latencySec
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
        promptLength: Int
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
            latencySec = latencySec
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
        latencySec: Double? = null
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
            "TOOL_CALL_NOT_SUPPORTED" -> "이 응답은 도구 호출이 필요하지만, 현재 대화모드에서는 도구 실행을 아직 지원하지 않습니다."
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
            ProviderType.OPENAI_COMPATIBLE,
            ProviderType.NVIDIA,
            ProviderType.GOOGLE -> true
            ProviderType.LOCAL,
            ProviderType.CUSTOM -> false
        }
    }
}
