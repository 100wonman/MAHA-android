// NvidiaModelProvider.kt

package com.maha.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

object NvidiaModelProvider : ModelProvider {

    private const val TAG = "NvidiaModelProvider"
    private const val CHAT_COMPLETIONS_URL = "https://integrate.api.nvidia.com/v1/chat/completions"
    private const val DEFAULT_NVIDIA_MODEL = "meta/llama-3.1-8b-instruct"

    override suspend fun generate(request: ModelRequest): ModelResponse {
        val apiKey = ApiKeyManager.getNvidiaApiKey()

        if (apiKey.isBlank()) {
            Log.d(TAG, "NVIDIA API key is not set.")
            return ModelResponse(
                outputText = "NVIDIA_API_KEY_NOT_SET",
                status = "SUCCESS"
            )
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                callNvidiaApi(
                    apiKey = apiKey,
                    modelName = request.modelName.ifBlank { DEFAULT_NVIDIA_MODEL },
                    prompt = request.inputText,
                    maxTokens = 1024,
                    temperature = 0.7
                )
            }.getOrElse { exception ->
                Log.e(TAG, "NVIDIA API exception: ${exception.message}", exception)
                ModelResponse(
                    outputText = if (exception is SocketTimeoutException) {
                        "NVIDIA_TIMEOUT"
                    } else {
                        "NVIDIA_API_CALL_FAILED"
                    },
                    status = "FAILED"
                )
            }
        }
    }

    suspend fun testModel(modelName: String): ModelTestRecord {
        val apiKey = ApiKeyManager.getNvidiaApiKey()
        val safeModelName = modelName.trim()

        if (apiKey.isBlank()) {
            return ModelTestRecord(
                providerName = ModelProviderType.NVIDIA,
                modelName = safeModelName,
                status = NvidiaModelTestStatus.AUTH_REQUIRED,
                lastTestedAt = getCurrentTimeText(),
                httpStatusCode = 401,
                message = "NVIDIA API Key가 없습니다.",
                latencyMs = 0L
            )
        }

        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            runCatching {
                callNvidiaModelTest(
                    apiKey = apiKey,
                    modelName = safeModelName
                )
            }.getOrElse { exception ->
                val latencyMs = System.currentTimeMillis() - startTime

                Log.e(TAG, "NVIDIA model test exception: ${exception.message}", exception)

                ModelTestRecord(
                    providerName = ModelProviderType.NVIDIA,
                    modelName = safeModelName,
                    status = NvidiaModelTestStatus.FAILED,
                    lastTestedAt = getCurrentTimeText(),
                    httpStatusCode = -1,
                    message = if (exception is SocketTimeoutException) {
                        "테스트 timeout"
                    } else {
                        exception.message ?: "테스트 실패"
                    },
                    latencyMs = latencyMs
                )
            }
        }
    }

    private fun callNvidiaApi(
        apiKey: String,
        modelName: String,
        prompt: String,
        maxTokens: Int,
        temperature: Double
    ): ModelResponse {
        val safeModelName = modelName.trim()
        val readTimeoutMs = getReadTimeoutMs(safeModelName)

        Log.d(TAG, "NVIDIA request URL: $CHAT_COMPLETIONS_URL")
        Log.d(TAG, "NVIDIA selected model: $safeModelName")
        Log.d(TAG, "NVIDIA API key: ${maskApiKey(apiKey)}")
        Log.d(TAG, "NVIDIA read timeout ms: $readTimeoutMs")

        val connection = URL(CHAT_COMPLETIONS_URL).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = readTimeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")

            val requestJson = createRequestJson(
                modelName = safeModelName,
                prompt = prompt,
                maxTokens = maxTokens,
                temperature = temperature
            )

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode

            logRateLimitHeaders(connection)

            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
            } else {
                connection.errorStream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use(BufferedReader::readText)
                    ?: ""
            }

            Log.d(TAG, "NVIDIA HTTP status code: $responseCode")
            Log.d(TAG, "NVIDIA response body: $responseText")

            if (responseCode !in 200..299) {
                return ModelResponse(
                    outputText = classifyFailureText(responseCode),
                    status = "FAILED"
                )
            }

            val outputText = parseNvidiaResponse(responseText)

            return if (outputText.isNotBlank()) {
                ModelResponse(
                    outputText = outputText,
                    status = "SUCCESS"
                )
            } else {
                ModelResponse(
                    outputText = "NVIDIA_API_CALL_FAILED",
                    status = "FAILED"
                )
            }
        } catch (exception: SocketTimeoutException) {
            Log.e(
                TAG,
                "NVIDIA timeout. model=$safeModelName, readTimeoutMs=$readTimeoutMs, message=${exception.message}",
                exception
            )

            return ModelResponse(
                outputText = "NVIDIA_TIMEOUT",
                status = "FAILED"
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun callNvidiaModelTest(
        apiKey: String,
        modelName: String
    ): ModelTestRecord {
        val startTime = System.currentTimeMillis()
        val connection = URL(CHAT_COMPLETIONS_URL).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")

            val requestJson = createRequestJson(
                modelName = modelName,
                prompt = "Reply with only: OK",
                maxTokens = 8,
                temperature = 0.0
            )

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            val latencyMs = System.currentTimeMillis() - startTime

            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
            } else {
                connection.errorStream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use(BufferedReader::readText)
                    ?: ""
            }

            Log.d(TAG, "NVIDIA model test status code: $responseCode")
            Log.d(TAG, "NVIDIA model test body: $responseText")

            val parsedText = if (responseCode in 200..299) {
                parseNvidiaResponse(responseText)
            } else {
                ""
            }

            return ModelTestRecord(
                providerName = ModelProviderType.NVIDIA,
                modelName = modelName,
                status = classifyTestStatus(
                    responseCode = responseCode,
                    parsedText = parsedText
                ),
                lastTestedAt = getCurrentTimeText(),
                httpStatusCode = responseCode,
                message = buildTestMessage(
                    responseCode = responseCode,
                    parsedText = parsedText,
                    responseText = responseText
                ),
                latencyMs = latencyMs
            )
        } catch (exception: SocketTimeoutException) {
            val latencyMs = System.currentTimeMillis() - startTime

            Log.e(
                TAG,
                "NVIDIA model test timeout. model=$modelName, message=${exception.message}",
                exception
            )

            return ModelTestRecord(
                providerName = ModelProviderType.NVIDIA,
                modelName = modelName,
                status = NvidiaModelTestStatus.FAILED,
                lastTestedAt = getCurrentTimeText(),
                httpStatusCode = -1,
                message = "테스트 timeout",
                latencyMs = latencyMs
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun createRequestJson(
        modelName: String,
        prompt: String,
        maxTokens: Int,
        temperature: Double
    ): JSONObject {
        val messageObject = JSONObject().apply {
            put("role", "user")
            put("content", prompt)
        }

        val messagesArray = JSONArray().apply {
            put(messageObject)
        }

        return JSONObject().apply {
            put("model", modelName)
            put("messages", messagesArray)
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            put("top_p", 0.9)
            put("stream", false)
        }
    }

    private fun parseNvidiaResponse(responseText: String): String {
        val root = JSONObject(responseText)
        val choices = root.optJSONArray("choices") ?: return ""

        if (choices.length() == 0) return ""

        val firstChoice = choices.optJSONObject(0) ?: return ""
        val message = firstChoice.optJSONObject("message") ?: return ""

        val content = message.optString("content", "")
        if (content.isNotBlank() && content != "null") {
            return content
        }

        val reasoning = message.optString("reasoning", "")
        if (reasoning.isNotBlank() && reasoning != "null") {
            return reasoning
        }

        return ""
    }

    private fun classifyTestStatus(
        responseCode: Int,
        parsedText: String
    ): String {
        return when {
            responseCode in 200..299 && parsedText.isNotBlank() -> NvidiaModelTestStatus.AVAILABLE
            responseCode == 401 || responseCode == 403 -> NvidiaModelTestStatus.AUTH_REQUIRED
            responseCode == 404 || responseCode == 410 -> NvidiaModelTestStatus.UNSUPPORTED
            responseCode == 429 -> NvidiaModelTestStatus.RATE_LIMITED
            responseCode == 500 || responseCode == 502 || responseCode == 503 || responseCode == 504 -> NvidiaModelTestStatus.FAILED
            else -> NvidiaModelTestStatus.FAILED
        }
    }

    private fun buildTestMessage(
        responseCode: Int,
        parsedText: String,
        responseText: String
    ): String {
        return when {
            responseCode in 200..299 && parsedText.isNotBlank() -> "호출 가능"
            responseCode == 401 || responseCode == 403 -> "권한 필요"
            responseCode == 404 || responseCode == 410 -> "지원 안 됨"
            responseCode == 429 -> "제한됨"
            responseCode == 500 || responseCode == 502 || responseCode == 503 || responseCode == 504 -> "서버 오류"
            responseText.isNotBlank() -> responseText.take(300)
            else -> "실패"
        }
    }

    private fun getReadTimeoutMs(modelName: String): Int {
        val lowerName = modelName.lowercase()

        return when {
            lowerName.contains("kimi") -> 90_000
            lowerName.contains("70b") -> 80_000
            lowerName.contains("405b") -> 120_000
            lowerName.contains("deepseek") -> 90_000
            lowerName.contains("nemotron") -> 90_000
            lowerName.contains("8b") -> 45_000
            lowerName.contains("7b") -> 45_000
            else -> 60_000
        }
    }

    private fun classifyFailureText(responseCode: Int): String {
        return when (responseCode) {
            401 -> "NVIDIA_UNAUTHORIZED"
            403 -> "NVIDIA_FORBIDDEN"
            404 -> "NVIDIA_MODEL_NOT_FOUND"
            410 -> "NVIDIA_MODEL_GONE"
            429 -> "NVIDIA_RATE_LIMITED"
            500, 502, 503, 504 -> "NVIDIA_SERVER_ERROR"
            else -> "NVIDIA_API_CALL_FAILED"
        }
    }

    private fun logRateLimitHeaders(connection: HttpURLConnection) {
        val limit = connection.getHeaderField("X-RateLimit-Limit")
        val remaining = connection.getHeaderField("X-RateLimit-Remaining")
        val reset = connection.getHeaderField("X-RateLimit-Reset")
        val retryAfter = connection.getHeaderField("Retry-After")

        Log.d(TAG, "NVIDIA rate limit header X-RateLimit-Limit: ${limit ?: "not provided"}")
        Log.d(TAG, "NVIDIA rate limit header X-RateLimit-Remaining: ${remaining ?: "not provided"}")
        Log.d(TAG, "NVIDIA rate limit header X-RateLimit-Reset: ${reset ?: "not provided"}")
        Log.d(TAG, "NVIDIA rate limit header Retry-After: ${retryAfter ?: "not provided"}")
    }

    private fun maskApiKey(apiKey: String): String {
        val prefix = apiKey.take(4)
        return "$prefix****"
    }
}