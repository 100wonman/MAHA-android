// GoogleModelProvider.kt

package com.maha.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder

object GoogleModelProvider : ModelProvider {

    private const val TAG = "GoogleModelProvider"
    private const val API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    private const val MAX_RETRY_COUNT = 1
    private const val RETRY_DELAY_MS = 1_200L

    private const val ERROR_RATE_LIMITED = "GOOGLE_RATE_LIMITED"
    private const val ERROR_SERVER = "GOOGLE_SERVER_ERROR"
    private const val ERROR_TIMEOUT = "GOOGLE_TIMEOUT"
    private const val ERROR_GENERAL = "GOOGLE_API_CALL_FAILED"

    override suspend fun generate(request: ModelRequest): ModelResponse {
        val apiKey = ApiKeyManager.getGoogleApiKey()

        if (apiKey.isBlank()) {
            Log.d(TAG, "Google API key is not set.")
            return ModelResponse(
                outputText = "GOOGLE_API_KEY_NOT_SET",
                status = "SUCCESS"
            )
        }

        val safeModelName = GeminiModelType.sanitize(request.modelName)

        return withContext(Dispatchers.IO) {
            runCatching {
                callGeminiApiWithRetry(
                    apiKey = apiKey,
                    modelName = safeModelName,
                    prompt = request.inputText
                )
            }.getOrElse { exception ->
                Log.e(TAG, "Gemini API exception: ${exception.message}", exception)
                ModelResponse(
                    outputText = ERROR_GENERAL,
                    status = "FAILED"
                )
            }
        }
    }

    private suspend fun callGeminiApiWithRetry(
        apiKey: String,
        modelName: String,
        prompt: String
    ): ModelResponse {
        var attempt = 0
        var lastResponse = ModelResponse(
            outputText = ERROR_GENERAL,
            status = "FAILED"
        )

        while (attempt <= MAX_RETRY_COUNT) {
            val result = callGeminiApiOnce(
                apiKey = apiKey,
                modelName = modelName,
                prompt = prompt,
                attempt = attempt + 1
            )

            if (result.response.status == "SUCCESS") {
                return result.response
            }

            lastResponse = result.response

            if (!shouldRetry(result) || attempt >= MAX_RETRY_COUNT) {
                return lastResponse
            }

            Log.d(
                TAG,
                "Gemini retry scheduled. statusCode=${result.httpStatusCode}, errorType=${result.response.outputText}, delayMs=$RETRY_DELAY_MS"
            )

            delay(RETRY_DELAY_MS)
            attempt += 1
        }

        return lastResponse
    }

    private fun callGeminiApiOnce(
        apiKey: String,
        modelName: String,
        prompt: String,
        attempt: Int
    ): GeminiCallResult {
        val encodedApiKey = URLEncoder.encode(apiKey, "UTF-8")
        val maskedApiKey = maskApiKey(apiKey)
        val safeModelName = GeminiModelType.sanitize(modelName)
        val readTimeoutMs = getReadTimeoutMs(safeModelName)

        val urlText = "$API_BASE_URL/$safeModelName:generateContent?key=$maskedApiKey"
        val realUrl = "$API_BASE_URL/$safeModelName:generateContent?key=$encodedApiKey"

        Log.d(TAG, "Gemini request attempt: $attempt")
        Log.d(TAG, "Gemini request URL: $urlText")
        Log.d(TAG, "Gemini selected model: $safeModelName")
        Log.d(TAG, "Gemini read timeout ms: $readTimeoutMs")

        val url = URL(realUrl)
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = readTimeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")

            val requestJson = createRequestJson(prompt)

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode

            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
            } else {
                connection.errorStream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use(BufferedReader::readText)
                    ?: ""
            }

            Log.d(TAG, "Gemini HTTP status code: $responseCode")
            Log.d(TAG, "Gemini response type: ${classifyHttpFailure(responseCode)}")
            Log.d(TAG, "Gemini response body: $responseText")

            if (responseCode == 429) {
                val retryDelay = extractRetryDelay(responseText)
                Log.d(TAG, "Gemini retryDelay: ${retryDelay.ifBlank { "not provided" }}")

                return GeminiCallResult(
                    httpStatusCode = responseCode,
                    response = ModelResponse(
                        outputText = ERROR_RATE_LIMITED,
                        status = "FAILED"
                    )
                )
            }

            if (responseCode == 500) {
                return GeminiCallResult(
                    httpStatusCode = responseCode,
                    response = ModelResponse(
                        outputText = ERROR_SERVER,
                        status = "FAILED"
                    )
                )
            }

            if (responseCode !in 200..299) {
                return GeminiCallResult(
                    httpStatusCode = responseCode,
                    response = ModelResponse(
                        outputText = ERROR_GENERAL,
                        status = "FAILED"
                    )
                )
            }

            val outputText = parseGeminiResponse(responseText)

            return if (outputText.isNotBlank()) {
                GeminiCallResult(
                    httpStatusCode = responseCode,
                    response = ModelResponse(
                        outputText = outputText,
                        status = "SUCCESS"
                    )
                )
            } else {
                Log.d(TAG, "Gemini parse result is empty.")
                GeminiCallResult(
                    httpStatusCode = responseCode,
                    response = ModelResponse(
                        outputText = ERROR_GENERAL,
                        status = "FAILED"
                    )
                )
            }
        } catch (exception: SocketTimeoutException) {
            Log.e(
                TAG,
                "Gemini timeout exception. model=$safeModelName, readTimeoutMs=$readTimeoutMs, message=${exception.message}",
                exception
            )

            return GeminiCallResult(
                httpStatusCode = -1,
                response = ModelResponse(
                    outputText = ERROR_TIMEOUT,
                    status = "FAILED"
                )
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun createRequestJson(prompt: String): JSONObject {
        val textPart = JSONObject().apply {
            put("text", prompt)
        }

        val partsArray = JSONArray().apply {
            put(textPart)
        }

        val contentObject = JSONObject().apply {
            put("role", "user")
            put("parts", partsArray)
        }

        val contentsArray = JSONArray().apply {
            put(contentObject)
        }

        return JSONObject().apply {
            put("contents", contentsArray)
        }
    }

    private fun parseGeminiResponse(responseText: String): String {
        val root = JSONObject(responseText)
        val candidates = root.optJSONArray("candidates") ?: return ""

        if (candidates.length() == 0) return ""

        val firstCandidate = candidates.optJSONObject(0) ?: return ""
        val content = firstCandidate.optJSONObject("content") ?: return ""
        val parts = content.optJSONArray("parts") ?: return ""

        val outputBuilder = StringBuilder()

        for (index in 0 until parts.length()) {
            val part = parts.optJSONObject(index) ?: continue
            val text = part.optString("text", "")

            if (text.isNotBlank()) {
                if (outputBuilder.isNotEmpty()) {
                    outputBuilder.append("\n")
                }

                outputBuilder.append(text)
            }
        }

        return outputBuilder.toString()
    }

    private fun shouldRetry(result: GeminiCallResult): Boolean {
        return result.httpStatusCode == 500 ||
                result.response.outputText == ERROR_TIMEOUT
    }

    private fun getReadTimeoutMs(modelName: String): Int {
        return when (GeminiModelType.sanitize(modelName)) {
            GeminiModelType.FLASH_LITE -> 30_000
            GeminiModelType.FLASH -> 45_000
            GeminiModelType.GEMMA_4_26B_A4B_IT -> 60_000
            GeminiModelType.GEMMA_4_31B_IT -> 75_000
            else -> 45_000
        }
    }

    private fun extractRetryDelay(responseText: String): String {
        return runCatching {
            val root = JSONObject(responseText)
            val error = root.optJSONObject("error") ?: return@runCatching ""

            val details = error.optJSONArray("details") ?: return@runCatching ""

            for (index in 0 until details.length()) {
                val detail = details.optJSONObject(index) ?: continue
                val retryDelay = detail.optString("retryDelay", "")
                if (retryDelay.isNotBlank()) {
                    return@runCatching retryDelay
                }
            }

            ""
        }.getOrDefault("")
    }

    private fun classifyHttpFailure(statusCode: Int): String {
        return when (statusCode) {
            200, 201, 202 -> "SUCCESS"
            400 -> "BAD_REQUEST"
            401 -> "UNAUTHORIZED_OR_INVALID_API_KEY"
            403 -> "FORBIDDEN_OR_PERMISSION_DENIED"
            404 -> "MODEL_OR_ENDPOINT_NOT_FOUND"
            429 -> "RATE_LIMIT_OR_QUOTA_EXCEEDED"
            500 -> "SERVER_ERROR"
            502 -> "BAD_GATEWAY"
            503 -> "SERVICE_UNAVAILABLE"
            504 -> "GATEWAY_TIMEOUT"
            -1 -> "TIMEOUT"
            else -> "HTTP_ERROR"
        }
    }

    private fun maskApiKey(apiKey: String): String {
        val prefix = apiKey.take(4)
        return "$prefix****"
    }

    private data class GeminiCallResult(
        val httpStatusCode: Int,
        val response: ModelResponse
    )
}