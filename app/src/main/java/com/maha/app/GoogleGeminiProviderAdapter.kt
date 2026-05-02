package com.maha.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * - no tool calling
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
                    errorMessage = "API Key가 설정되지 않았습니다."
                )
            }

            if (modelName.isBlank()) {
                return@withContext GeminiProviderResult.failure(
                    latencySec = elapsedSec(startedAt),
                    errorType = "INVALID_REQUEST",
                    errorMessage = "모델명이 비어 있습니다."
                )
            }

            val requestBody = JSONObject()
                .put("model", modelName)
                .put(
                    "messages",
                    org.json.JSONArray()
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
                return@withContext GeminiProviderResult.failure(
                    latencySec = elapsedSec(startedAt),
                    errorType = mapHttpStatusToErrorType(statusCode),
                    errorMessage = summarizeErrorBody(responseText),
                    httpStatusCode = statusCode,
                    rawBody = responseText
                )
            }

            val content = parseAssistantContent(responseText)
            if (content.isBlank()) {
                return@withContext GeminiProviderResult.failure(
                    latencySec = elapsedSec(startedAt),
                    errorType = "INVALID_RESPONSE",
                    errorMessage = "Gemini 응답에서 assistant content를 찾을 수 없습니다.",
                    httpStatusCode = statusCode,
                    rawBody = responseText
                )
            }

            GeminiProviderResult(
                success = true,
                rawText = content,
                latencySec = elapsedSec(startedAt),
                errorType = null,
                errorMessage = null,
                providerName = "Google Gemini",
                modelName = modelName,
                httpStatusCode = statusCode,
                rawBody = responseText
            )
        } catch (timeout: SocketTimeoutException) {
            GeminiProviderResult.failure(
                latencySec = elapsedSec(startedAt),
                errorType = "TIMEOUT",
                errorMessage = timeout.message ?: "요청 시간이 초과되었습니다."
            )
        } catch (throwable: Throwable) {
            GeminiProviderResult.failure(
                latencySec = elapsedSec(startedAt),
                errorType = "UNKNOWN",
                errorMessage = throwable.message ?: "알 수 없는 오류"
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

    private fun parseAssistantContent(responseText: String): String {
        val root = JSONObject(responseText)
        val choices = root.optJSONArray("choices") ?: return ""
        if (choices.length() == 0) return ""

        val firstChoice = choices.optJSONObject(0) ?: return ""
        val message = firstChoice.optJSONObject("message") ?: return ""
        return message.optString("content", "").trim()
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

    private fun summarizeErrorBody(body: String): String {
        if (body.isBlank()) return "Gemini API 요청이 실패했습니다."

        return runCatching {
            val root = JSONObject(body)
            val error = root.optJSONObject("error")
            val message = error?.optString("message")
            message?.takeIf { it.isNotBlank() } ?: body.take(400)
        }.getOrElse {
            body.take(400)
        }
    }

    private fun elapsedSec(startedAt: Long): Double {
        return ((System.currentTimeMillis() - startedAt).coerceAtLeast(1) / 1000.0)
    }
}

data class GeminiProviderResult(
    val success: Boolean,
    val rawText: String,
    val latencySec: Double,
    val errorType: String?,
    val errorMessage: String?,
    val providerName: String,
    val modelName: String,
    val httpStatusCode: Int?,
    val rawBody: String?
) {
    companion object {
        fun failure(
            latencySec: Double,
            errorType: String,
            errorMessage: String,
            httpStatusCode: Int? = null,
            rawBody: String? = null
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
                rawBody = rawBody
            )
        }
    }
}
