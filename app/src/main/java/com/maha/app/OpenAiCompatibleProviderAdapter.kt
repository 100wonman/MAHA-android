package com.maha.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class OpenAiCompatibleProviderAdapter(
    private val connectTimeoutMillis: Int = 30_000,
    private val readTimeoutMillis: Int = 60_000
) {
    suspend fun callChatCompletions(
        prompt: String,
        modelName: String,
        providerProfile: ProviderProfile,
        apiKey: String?
    ): OpenAiCompatibleProviderResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val providerName = providerProfile.displayName.ifBlank { providerProfile.providerId }

        try {
            if (providerProfile.baseUrl.isBlank()) {
                return@withContext OpenAiCompatibleProviderResult.failure(
                    providerName = providerName,
                    modelName = modelName,
                    latencySec = elapsedSec(startedAt),
                    errorType = "BASE_URL_MISSING",
                    errorMessage = "Provider Base URL이 설정되지 않았습니다.",
                    responseSummary = "providerType=${providerProfile.providerType}\nproviderId=${providerProfile.providerId}\nbaseUrl: blank"
                )
            }

            if (modelName.isBlank()) {
                return@withContext OpenAiCompatibleProviderResult.failure(
                    providerName = providerName,
                    modelName = modelName,
                    latencySec = elapsedSec(startedAt),
                    errorType = "MODEL_MISSING",
                    errorMessage = "모델명이 비어 있습니다.",
                    responseSummary = "providerType=${providerProfile.providerType}\nproviderId=${providerProfile.providerId}\nmodelName: blank"
                )
            }

            val endpoint = buildChatCompletionsEndpoint(providerProfile.baseUrl)
            val requestBody = JSONObject()
                .put("model", modelName)
                .put(
                    "messages",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("role", "user")
                                .put("content", prompt)
                        )
                )
                .put("temperature", 0.7)
                .toString()

            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (!apiKey.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            val statusCode = connection.responseCode
            val responseText = readConnectionBody(connection, statusCode)

            if (statusCode !in 200..299) {
                val httpError = parseHttpErrorBody(responseText, statusCode, providerProfile, endpoint)
                return@withContext OpenAiCompatibleProviderResult.failure(
                    providerName = providerName,
                    modelName = modelName,
                    latencySec = elapsedSec(startedAt),
                    errorType = httpError.errorType,
                    errorMessage = httpError.userMessage,
                    httpStatusCode = statusCode,
                    rawBody = responseText,
                    responseSummary = httpError.summary
                )
            }

            val parsed = parseOpenAiCompatibleResponse(
                responseText = responseText,
                providerProfile = providerProfile,
                endpoint = endpoint
            )

            if (parsed.hasToolCall) {
                return@withContext OpenAiCompatibleProviderResult.failure(
                    providerName = providerName,
                    modelName = modelName,
                    latencySec = elapsedSec(startedAt),
                    errorType = "TOOL_CALL_NOT_SUPPORTED",
                    errorMessage = "모델이 도구 호출을 요청했지만, 현재 대화모드에서는 도구 실행을 아직 지원하지 않습니다.",
                    httpStatusCode = statusCode,
                    rawBody = responseText,
                    responseSummary = parsed.summary,
                    toolCallDetected = true,
                    toolCallCount = parsed.toolCallCount,
                    toolNames = parsed.toolNames,
                    finishReason = parsed.finishReason
                )
            }

            if (parsed.content.isNotBlank()) {
                return@withContext OpenAiCompatibleProviderResult(
                    success = true,
                    rawText = parsed.content,
                    latencySec = elapsedSec(startedAt),
                    errorType = null,
                    errorMessage = null,
                    providerName = providerName,
                    modelName = modelName,
                    httpStatusCode = statusCode,
                    rawBody = responseText,
                    responseSummary = parsed.summary,
                    toolCallDetected = false,
                    toolCallCount = parsed.toolCallCount,
                    toolNames = parsed.toolNames,
                    finishReason = parsed.finishReason
                )
            }

            OpenAiCompatibleProviderResult.failure(
                providerName = providerName,
                modelName = modelName,
                latencySec = elapsedSec(startedAt),
                errorType = "INVALID_RESPONSE",
                errorMessage = "응답에서 표시 가능한 텍스트를 찾지 못했습니다.",
                httpStatusCode = statusCode,
                rawBody = responseText,
                responseSummary = parsed.summary
            )
        } catch (timeout: SocketTimeoutException) {
            OpenAiCompatibleProviderResult.failure(
                providerName = providerName,
                modelName = modelName,
                latencySec = elapsedSec(startedAt),
                errorType = "TIMEOUT",
                errorMessage = timeout.message ?: "요청 시간이 초과되었습니다.",
                responseSummary = "providerType=${providerProfile.providerType}\nproviderId=${providerProfile.providerId}\nSocketTimeoutException: ${timeout.message ?: "timeout"}"
            )
        } catch (throwable: Throwable) {
            OpenAiCompatibleProviderResult.failure(
                providerName = providerName,
                modelName = modelName,
                latencySec = elapsedSec(startedAt),
                errorType = "NETWORK_ERROR",
                errorMessage = throwable.message ?: "Provider 서버에 연결하지 못했습니다.",
                responseSummary = "providerType=${providerProfile.providerType}\nproviderId=${providerProfile.providerId}\n${throwable::class.java.simpleName}: ${throwable.message ?: "unknown"}"
            )
        }
    }

    private fun buildChatCompletionsEndpoint(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/chat/completions", ignoreCase = true)) {
            trimmed
        } else {
            "$trimmed/chat/completions"
        }
    }

    private fun readConnectionBody(
        connection: HttpURLConnection,
        statusCode: Int
    ): String {
        val stream = runCatching {
            if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
        }.getOrNull() ?: return ""

        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            reader.readText()
        }
    }

    private fun parseOpenAiCompatibleResponse(
        responseText: String,
        providerProfile: ProviderProfile,
        endpoint: String
    ): OpenAiCompatibleParseResult {
        return runCatching {
            val root = JSONObject(responseText)
            val choices = root.optJSONArray("choices")
            val rootKeys = keysOf(root)

            if (choices == null || choices.length() == 0) {
                return@runCatching OpenAiCompatibleParseResult(
                    content = "",
                    hasToolCall = false,
                    finishReason = null,
                    toolCallCount = 0,
                    toolNames = emptyList(),
                    summary = buildString {
                        appendLine("providerType: ${providerProfile.providerType}")
                        appendLine("providerId: ${providerProfile.providerId}")
                        appendLine("baseUrl: ${providerProfile.baseUrl}")
                        appendLine("endpoint: $endpoint")
                        appendLine("choices: missing_or_empty")
                        appendLine("rootKeys: ${rootKeys.joinToString(",")}")
                    }.trim()
                )
            }

            val firstChoice = choices.optJSONObject(0)
            if (firstChoice == null) {
                return@runCatching OpenAiCompatibleParseResult(
                    content = "",
                    hasToolCall = false,
                    finishReason = null,
                    toolCallCount = 0,
                    toolNames = emptyList(),
                    summary = buildString {
                        appendLine("providerType: ${providerProfile.providerType}")
                        appendLine("providerId: ${providerProfile.providerId}")
                        appendLine("endpoint: $endpoint")
                        appendLine("choices[0]: not_object")
                        appendLine("rootKeys: ${rootKeys.joinToString(",")}")
                    }.trim()
                )
            }

            val finishReason = firstChoice.optString("finish_reason", "").takeIf { it.isNotBlank() }
            val message = firstChoice.optJSONObject("message")
            val choiceKeys = keysOf(firstChoice)
            val messageKeys = keysOf(message)

            if (message == null) {
                return@runCatching OpenAiCompatibleParseResult(
                    content = "",
                    hasToolCall = finishReason?.contains("tool", ignoreCase = true) == true,
                    finishReason = finishReason,
                    toolCallCount = 0,
                    toolNames = emptyList(),
                    summary = buildString {
                        appendLine("providerType: ${providerProfile.providerType}")
                        appendLine("providerId: ${providerProfile.providerId}")
                        appendLine("endpoint: $endpoint")
                        appendLine("finish_reason: ${finishReason ?: "null"}")
                        appendLine("message: missing")
                        appendLine("choiceKeys: ${choiceKeys.joinToString(",")}")
                        appendLine("rootKeys: ${rootKeys.joinToString(",")}")
                    }.trim()
                )
            }

            val content = extractMessageContent(message)
            val toolCalls = message.optJSONArray("tool_calls")
            val toolCallCount = toolCalls?.length() ?: 0
            val functionCall = message.optJSONObject("function_call")
            val hasFunctionCall = message.has("function_call") && !message.isNull("function_call")
            val hasToolLikeFinishReason = finishReason?.contains("tool", ignoreCase = true) == true ||
                    finishReason?.contains("function", ignoreCase = true) == true
            val finalToolCallCount = toolCallCount + if (hasFunctionCall) 1 else 0
            val toolNames = extractToolNames(toolCalls, functionCall)
            val argumentPreviews = extractToolArgumentPreviews(toolCalls, functionCall)

            OpenAiCompatibleParseResult(
                content = content,
                hasToolCall = finalToolCallCount > 0 || hasToolLikeFinishReason,
                finishReason = finishReason,
                toolCallCount = finalToolCallCount,
                toolNames = toolNames,
                summary = buildString {
                    appendLine("providerType: ${providerProfile.providerType}")
                    appendLine("providerId: ${providerProfile.providerId}")
                    appendLine("baseUrl: ${providerProfile.baseUrl}")
                    appendLine("endpoint: $endpoint")
                    appendLine("finish_reason: ${finishReason ?: "null"}")
                    appendLine("contentPresent: ${content.isNotBlank()}")
                    appendLine("toolCallDetected: ${finalToolCallCount > 0 || hasToolLikeFinishReason}")
                    appendLine("toolCallCount: $finalToolCallCount")
                    appendLine("toolNames: ${toolNames.joinToString(prefix = "[", postfix = "]")}")
                    appendLine("functionCallDetected: $hasFunctionCall")
                    appendLine("requestedToolCount: $finalToolCallCount")
                    appendLine("argumentsPreview: ${argumentPreviews.joinToString(prefix = "[", postfix = "]")}")
                    appendLine("executionAttempted: false")
                    appendLine("executionBlockedReason: TOOL_EXECUTION_NOT_IMPLEMENTED")
                    appendLine("tool_calls count: $toolCallCount")
                    appendLine("function_call present: $hasFunctionCall")
                    appendLine("messageKeys: ${messageKeys.joinToString(",")}")
                    appendLine("choiceKeys: ${choiceKeys.joinToString(",")}")
                    appendLine("rootKeys: ${rootKeys.joinToString(",")}")
                }.trim()
            )
        }.getOrElse { throwable ->
            OpenAiCompatibleParseResult(
                content = "",
                hasToolCall = false,
                finishReason = null,
                toolCallCount = 0,
                toolNames = emptyList(),
                summary = "providerType=${providerProfile.providerType}\nproviderId=${providerProfile.providerId}\nparseError: ${throwable::class.java.simpleName}: ${throwable.message ?: "unknown"}"
            )
        }
    }

    private fun extractMessageContent(message: JSONObject): String {
        val contentValue = message.opt("content") ?: return ""
        if (contentValue == JSONObject.NULL) return ""

        return when (contentValue) {
            is String -> contentValue.trim()
            is JSONArray -> extractTextFromContentArray(contentValue).trim()
            else -> contentValue.toString().trim()
        }
    }

    private fun extractTextFromContentArray(contentArray: JSONArray): String {
        val parts = mutableListOf<String>()
        for (index in 0 until contentArray.length()) {
            val item = contentArray.opt(index)
            when (item) {
                is String -> parts.add(item)
                is JSONObject -> {
                    val directText = item.optString("text", "").takeIf { it.isNotBlank() }
                    val typedText = if (item.optString("type") == "text") {
                        item.optString("text", "").takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                    val nestedText = item.optJSONObject("text")?.optString("value", "")?.takeIf { it.isNotBlank() }
                    listOf(directText, typedText, nestedText)
                        .firstOrNull { !it.isNullOrBlank() }
                        ?.let { parts.add(it) }
                }
            }
        }
        return parts.joinToString("\n")
    }

    private fun extractToolNames(
        toolCalls: JSONArray?,
        functionCall: JSONObject?
    ): List<String> {
        val names = linkedSetOf<String>()

        if (toolCalls != null) {
            for (index in 0 until toolCalls.length()) {
                val toolCall = toolCalls.optJSONObject(index) ?: continue
                val directName = toolCall.optString("name", "").takeIf { it.isNotBlank() }
                val functionName = toolCall.optJSONObject("function")
                    ?.optString("name", "")
                    ?.takeIf { it.isNotBlank() }
                val name = functionName ?: directName
                if (!name.isNullOrBlank()) {
                    names.add(name.take(80))
                }
            }
        }

        val functionName = functionCall
            ?.optString("name", "")
            ?.takeIf { it.isNotBlank() }
        if (!functionName.isNullOrBlank()) {
            names.add(functionName.take(80))
        }

        return names.toList()
    }

    private fun extractToolArgumentPreviews(
        toolCalls: JSONArray?,
        functionCall: JSONObject?
    ): List<String> {
        val previews = mutableListOf<String>()

        if (toolCalls != null) {
            for (index in 0 until toolCalls.length()) {
                val toolCall = toolCalls.optJSONObject(index) ?: continue
                val functionObject = toolCall.optJSONObject("function")
                val argumentText = functionObject?.opt("arguments")?.toString()
                    ?: toolCall.opt("arguments")?.toString()
                    ?: toolCall.opt("input")?.toString()
                sanitizeToolArgumentsPreview(argumentText)?.let { previews.add(it) }
            }
        }

        val functionArguments = functionCall?.opt("arguments")?.toString()
        sanitizeToolArgumentsPreview(functionArguments)?.let { previews.add(it) }

        return previews.take(5)
    }

    private fun sanitizeToolArgumentsPreview(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw
            .replace(Regex("\\s+"), " ")
            .replace(Regex("(?i)(api[_-]?key|authorization|bearer)\\s*[:=]\\s*[^,} ]+"), "\$1=***")
            .trim()
            .take(220)
    }

    private fun parseHttpErrorBody(
        responseText: String,
        statusCode: Int,
        providerProfile: ProviderProfile,
        endpoint: String
    ): OpenAiCompatibleHttpErrorSummary {
        val fallbackErrorType = mapHttpStatusToErrorType(statusCode)
        if (responseText.isBlank()) {
            return OpenAiCompatibleHttpErrorSummary(
                errorType = fallbackErrorType,
                userMessage = "HTTP $statusCode 오류가 발생했습니다.",
                summary = buildString {
                    appendLine("providerType: ${providerProfile.providerType}")
                    appendLine("providerId: ${providerProfile.providerId}")
                    appendLine("endpoint: $endpoint")
                    appendLine("httpStatusCode: $statusCode")
                    appendLine("errorBody: blank")
                }.trim()
            )
        }

        return runCatching {
            val root = JSONObject(responseText)
            val error = root.optJSONObject("error")
            val message = error?.optString("message", "")?.takeIf { it.isNotBlank() }
                ?: root.optString("message", "").takeIf { it.isNotBlank() }
                ?: "HTTP $statusCode 오류가 발생했습니다."
            val status = error?.optString("status", "")?.takeIf { it.isNotBlank() }
                ?: root.optString("status", "").takeIf { it.isNotBlank() }
            val code = error?.opt("code")?.toString()
                ?: root.opt("code")?.toString()
            val errorType = mapHttpStatusToErrorType(statusCode)

            OpenAiCompatibleHttpErrorSummary(
                errorType = errorType,
                userMessage = message,
                summary = buildString {
                    appendLine("providerType: ${providerProfile.providerType}")
                    appendLine("providerId: ${providerProfile.providerId}")
                    appendLine("endpoint: $endpoint")
                    appendLine("httpStatusCode: $statusCode")
                    appendLine("errorType: $errorType")
                    appendLine("error.message: ${message.take(500)}")
                    if (status != null) appendLine("error.status: $status")
                    if (code != null) appendLine("error.code: $code")
                    appendLine("rootKeys: ${keysOf(root).joinToString(",")}")
                }.trim()
            )
        }.getOrElse { throwable ->
            OpenAiCompatibleHttpErrorSummary(
                errorType = fallbackErrorType,
                userMessage = "HTTP $statusCode 오류가 발생했습니다.",
                summary = buildString {
                    appendLine("providerType: ${providerProfile.providerType}")
                    appendLine("providerId: ${providerProfile.providerId}")
                    appendLine("endpoint: $endpoint")
                    appendLine("httpStatusCode: $statusCode")
                    appendLine("errorType: $fallbackErrorType")
                    appendLine("parseError: ${throwable::class.java.simpleName}: ${throwable.message ?: "unknown"}")
                    appendLine("bodyPreview: ${responseText.take(500)}")
                }.trim()
            )
        }
    }

    private fun keysOf(jsonObject: JSONObject?): List<String> {
        if (jsonObject == null) return emptyList()
        val keys = mutableListOf<String>()
        val iterator = jsonObject.keys()
        while (iterator.hasNext()) {
            keys.add(iterator.next())
        }
        return keys
    }

    private fun mapHttpStatusToErrorType(statusCode: Int): String {
        return when (statusCode) {
            400 -> "INVALID_REQUEST"
            401, 403 -> "AUTH_FAILED"
            408 -> "TIMEOUT"
            429 -> "RATE_LIMIT"
            in 500..599 -> "SERVER_ERROR"
            else -> "UNKNOWN"
        }
    }

    private fun elapsedSec(startedAt: Long): Double {
        return ((System.currentTimeMillis() - startedAt).coerceAtLeast(1) / 1000.0)
    }
}

private data class OpenAiCompatibleParseResult(
    val content: String,
    val hasToolCall: Boolean,
    val finishReason: String?,
    val toolCallCount: Int,
    val toolNames: List<String>,
    val summary: String
)

private data class OpenAiCompatibleHttpErrorSummary(
    val errorType: String,
    val userMessage: String,
    val summary: String
)

data class OpenAiCompatibleProviderResult(
    val success: Boolean,
    val rawText: String,
    val latencySec: Double,
    val errorType: String?,
    val errorMessage: String?,
    val providerName: String,
    val modelName: String,
    val httpStatusCode: Int?,
    val rawBody: String?,
    val responseSummary: String?,
    val toolCallDetected: Boolean = false,
    val toolCallCount: Int = 0,
    val toolNames: List<String> = emptyList(),
    val finishReason: String? = null
) {
    companion object {
        fun failure(
            providerName: String,
            modelName: String,
            latencySec: Double,
            errorType: String,
            errorMessage: String,
            httpStatusCode: Int? = null,
            rawBody: String? = null,
            responseSummary: String? = null,
            toolCallDetected: Boolean = false,
            toolCallCount: Int = 0,
            toolNames: List<String> = emptyList(),
            finishReason: String? = null
        ): OpenAiCompatibleProviderResult {
            return OpenAiCompatibleProviderResult(
                success = false,
                rawText = "",
                latencySec = latencySec,
                errorType = errorType,
                errorMessage = errorMessage,
                providerName = providerName,
                modelName = modelName,
                httpStatusCode = httpStatusCode,
                rawBody = rawBody,
                responseSummary = responseSummary,
                toolCallDetected = toolCallDetected,
                toolCallCount = toolCallCount,
                toolNames = toolNames,
                finishReason = finishReason
            )
        }
    }
}
