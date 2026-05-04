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

/**
 * Google Gemini OpenAI-compatible chat/completions adapter.
 *
 * This adapter performs only the first text-only call path:
 * - no streaming
 * - no tool calling execution
 * - no multimodal input
 * - no model list API
 */
class GoogleGeminiProviderAdapter(
    private val endpoint: String = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
    private val connectTimeoutMillis: Int = 30_000,
    private val readTimeoutMillis: Int = 60_000
) {
    suspend fun callGemini(
        prompt: String,
        modelName: String,
        apiKey: String
    ): GeminiProviderResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()

        try {
            if (apiKey.isBlank()) {
                return@withContext GeminiProviderResult.failure(
                    latencySec = elapsedSec(startedAt),
                    errorType = "API_KEY_MISSING",
                    errorMessage = "API Key가 설정되지 않았습니다.",
                    responseSummary = "apiKey: blank"
                )
            }

            if (modelName.isBlank()) {
                return@withContext GeminiProviderResult.failure(
                    latencySec = elapsedSec(startedAt),
                    errorType = "INVALID_REQUEST",
                    errorMessage = "모델명이 비어 있습니다.",
                    responseSummary = "modelName: blank"
                )
            }

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
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            val statusCode = connection.responseCode
            val responseText = readConnectionBody(connection, statusCode)

            if (statusCode !in 200..299) {
                val httpError = parseHttpErrorBody(responseText, statusCode)
                return@withContext GeminiProviderResult.failure(
                    latencySec = elapsedSec(startedAt),
                    errorType = httpError.errorType,
                    errorMessage = httpError.userMessage,
                    httpStatusCode = statusCode,
                    rawBody = responseText,
                    responseSummary = httpError.summary
                )
            }

            val parsed = parseGeminiResponse(responseText)

            if (parsed.hasToolCall) {
                return@withContext GeminiProviderResult.failure(
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
                return@withContext GeminiProviderResult(
                    success = true,
                    rawText = parsed.content,
                    latencySec = elapsedSec(startedAt),
                    errorType = null,
                    errorMessage = null,
                    providerName = "Google Gemini",
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

            GeminiProviderResult.failure(
                latencySec = elapsedSec(startedAt),
                errorType = "INVALID_RESPONSE",
                errorMessage = "Gemini 응답에서 표시 가능한 텍스트를 찾지 못했습니다.",
                httpStatusCode = statusCode,
                rawBody = responseText,
                responseSummary = parsed.summary
            )
        } catch (timeout: SocketTimeoutException) {
            GeminiProviderResult.failure(
                latencySec = elapsedSec(startedAt),
                errorType = "TIMEOUT",
                errorMessage = timeout.message ?: "요청 시간이 초과되었습니다.",
                responseSummary = "SocketTimeoutException: ${timeout.message ?: "timeout"}"
            )
        } catch (throwable: Throwable) {
            GeminiProviderResult.failure(
                latencySec = elapsedSec(startedAt),
                errorType = "UNKNOWN",
                errorMessage = throwable.message ?: "알 수 없는 오류",
                responseSummary = "${throwable::class.java.simpleName}: ${throwable.message ?: "unknown"}"
            )
        }
    }

    private fun readConnectionBody(
        connection: HttpURLConnection,
        statusCode: Int
    ): String {
        val stream = if (statusCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        }

        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            reader.readText()
        }
    }

    private fun parseGeminiResponse(responseText: String): GeminiParseResult {
        return runCatching {
            val root = JSONObject(responseText)
            val choices = root.optJSONArray("choices")
            val rootKeys = keysOf(root)

            if (choices == null || choices.length() == 0) {
                return@runCatching GeminiParseResult(
                    content = "",
                    hasToolCall = false,
                    finishReason = null,
                    toolCallCount = 0,
                    toolNames = emptyList(),
                    summary = buildString {
                        appendLine("choices: missing_or_empty")
                        appendLine("rootKeys: ${rootKeys.joinToString(",")}")
                    }.trim()
                )
            }

            val firstChoice = choices.optJSONObject(0)
            if (firstChoice == null) {
                return@runCatching GeminiParseResult(
                    content = "",
                    hasToolCall = false,
                    finishReason = null,
                    toolCallCount = 0,
                    toolNames = emptyList(),
                    summary = buildString {
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
                return@runCatching GeminiParseResult(
                    content = "",
                    hasToolCall = finishReason?.contains("tool", ignoreCase = true) == true,
                    finishReason = finishReason,
                    toolCallCount = 0,
                    toolNames = emptyList(),
                    summary = buildString {
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
            val refusal = message.optString("refusal", "").takeIf { it.isNotBlank() }
            val finalToolCallCount = toolCallCount + if (hasFunctionCall) 1 else 0
            val toolNames = extractToolNames(toolCalls, functionCall)
            val argumentPreviews = extractToolArgumentPreviews(toolCalls, functionCall)

            GeminiParseResult(
                content = content,
                hasToolCall = finalToolCallCount > 0 || hasToolLikeFinishReason,
                finishReason = finishReason,
                toolCallCount = finalToolCallCount,
                toolNames = toolNames,
                summary = buildString {
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
                    if (refusal != null) appendLine("refusal: ${refusal.take(200)}")
                    appendLine("messageKeys: ${messageKeys.joinToString(",")}")
                    appendLine("choiceKeys: ${choiceKeys.joinToString(",")}")
                    appendLine("rootKeys: ${rootKeys.joinToString(",")}")
                }.trim()
            )
        }.getOrElse { throwable ->
            GeminiParseResult(
                content = "",
                hasToolCall = false,
                finishReason = null,
                toolCallCount = 0,
                toolNames = emptyList(),
                summary = "parseError: ${throwable::class.java.simpleName}: ${throwable.message ?: "unknown"}"
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
        val builder = StringBuilder()
        for (index in 0 until contentArray.length()) {
            val item = contentArray.opt(index)
            when (item) {
                is String -> builder.appendLine(item)
                is JSONObject -> {
                    val text = item.optString("text", "")
                    if (text.isNotBlank()) builder.appendLine(text)
                }
            }
        }
        return builder.toString()
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

    private fun parseHttpErrorBody(body: String, statusCode: Int): GeminiHttpErrorSummary {
        val fallbackType = mapHttpStatusToErrorType(statusCode)
        if (body.isBlank()) {
            return GeminiHttpErrorSummary(
                errorType = fallbackType,
                userMessage = "Gemini API 요청이 실패했습니다.",
                summary = "httpStatus: $statusCode\nerrorBody: blank"
            )
        }

        return runCatching {
            val root = JSONObject(body)
            val error = root.optJSONObject("error")
            val message = error?.optString("message", "")?.takeIf { it.isNotBlank() }
            val status = error?.optString("status", "")?.takeIf { it.isNotBlank() }
            val code = if (error != null && error.has("code")) error.optInt("code") else statusCode

            GeminiHttpErrorSummary(
                errorType = fallbackType,
                userMessage = message ?: body.take(400),
                summary = buildString {
                    appendLine("httpStatus: $statusCode")
                    appendLine("error.code: $code")
                    appendLine("error.status: ${status ?: "null"}")
                    appendLine("error.message: ${(message ?: "").take(400)}")
                    appendLine("rootKeys: ${keysOf(root).joinToString(",")}")
                }.trim()
            )
        }.getOrElse {
            GeminiHttpErrorSummary(
                errorType = fallbackType,
                userMessage = body.take(400),
                summary = "httpStatus: $statusCode\nerrorBodyPreview: ${body.take(500)}"
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
            401, 403 -> "API_KEY_MISSING"
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

private data class GeminiParseResult(
    val content: String,
    val hasToolCall: Boolean,
    val finishReason: String?,
    val toolCallCount: Int,
    val toolNames: List<String>,
    val summary: String
)

private data class GeminiHttpErrorSummary(
    val errorType: String,
    val userMessage: String,
    val summary: String
)

data class GeminiProviderResult(
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
        ): GeminiProviderResult {
            return GeminiProviderResult(
                success = false,
                rawText = "",
                latencySec = latencySec,
                errorType = errorType,
                errorMessage = errorMessage,
                providerName = "Google Gemini",
                modelName = "",
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
