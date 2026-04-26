// GoogleModelProvider.kt

package com.maha.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

object GoogleModelProvider : ModelProvider {

    private const val TAG = "GoogleModelProvider"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 75_000
    private const val RETRY_DELAY_MS = 2_000L

    override suspend fun generate(request: ModelRequest): ModelResponse {
        val apiKey = ApiKeyManager.getGoogleApiKey()

        if (apiKey.isBlank()) {
            return ModelResponse(
                outputText = "GOOGLE_API_KEY_NOT_SET",
                status = "FAILED"
            )
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                callApiWithTimeoutRetry(
                    modelName = request.modelName,
                    apiKey = apiKey,
                    prompt = request.inputText,
                    maxTokens = 1024
                )
            }.getOrElse { exception ->
                Log.e(TAG, "Google API exception: ${exception.message}", exception)

                ModelResponse(
                    outputText = if (exception is SocketTimeoutException) {
                        "GOOGLE_TIMEOUT"
                    } else {
                        "GOOGLE_API_CALL_FAILED"
                    },
                    status = "FAILED"
                )
            }
        }
    }

    suspend fun testModel(modelName: String): ModelTestRecord {
        val apiKey = ApiKeyManager.getGoogleApiKey()
        val safeModelName = modelName.trim().removePrefix("models/")

        if (apiKey.isBlank()) {
            return ModelTestRecord(
                providerName = ModelProviderType.GOOGLE,
                modelName = safeModelName,
                status = NvidiaModelTestStatus.AUTH_REQUIRED,
                lastTestedAt = getCurrentTimeText(),
                httpStatusCode = 401,
                message = "API Key 없음",
                latencyMs = 0L
            )
        }

        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()

            runCatching {
                callApiWithTimeoutRetry(
                    modelName = safeModelName,
                    apiKey = apiKey,
                    prompt = "Reply with only: OK",
                    maxTokens = 8
                )
            }.fold(
                onSuccess = {
                    val latency = System.currentTimeMillis() - start
                    val modelInfo = runCatching {
                        requestSelfReportedModelInfo(
                            modelName = safeModelName,
                            apiKey = apiKey
                        )
                    }.getOrDefault(ModelInfo())

                    ModelTestRecord(
                        providerName = ModelProviderType.GOOGLE,
                        modelName = safeModelName,
                        status = NvidiaModelTestStatus.AVAILABLE,
                        lastTestedAt = getCurrentTimeText(),
                        httpStatusCode = 200,
                        message = "호출 가능",
                        latencyMs = latency,
                        selfReportedInfo = modelInfo
                    )
                },
                onFailure = { exception ->
                    val latency = System.currentTimeMillis() - start

                    ModelTestRecord(
                        providerName = ModelProviderType.GOOGLE,
                        modelName = safeModelName,
                        status = NvidiaModelTestStatus.FAILED,
                        lastTestedAt = getCurrentTimeText(),
                        httpStatusCode = -1,
                        message = if (exception is SocketTimeoutException) {
                            "테스트 timeout"
                        } else {
                            exception.message ?: "실패"
                        },
                        latencyMs = latency
                    )
                }
            )
        }
    }

    private suspend fun callApiWithTimeoutRetry(
        modelName: String,
        apiKey: String,
        prompt: String,
        maxTokens: Int
    ): ModelResponse {
        return try {
            callApi(
                modelName = modelName,
                apiKey = apiKey,
                prompt = prompt,
                maxTokens = maxTokens
            )
        } catch (firstTimeout: SocketTimeoutException) {
            Log.e(TAG, "Google timeout. Retry once after delay.", firstTimeout)

            delay(RETRY_DELAY_MS)

            callApi(
                modelName = modelName,
                apiKey = apiKey,
                prompt = prompt,
                maxTokens = maxTokens
            )
        }
    }

    private fun callApi(
        modelName: String,
        apiKey: String,
        prompt: String,
        maxTokens: Int
    ): ModelResponse {
        val safeModelName = modelName.trim().removePrefix("models/")
        val url = "$BASE_URL$safeModelName:generateContent?key=$apiKey"
        val connection = URL(url).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")

            val body = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().apply {
                            put(
                                "parts",
                                JSONArray().put(
                                    JSONObject().apply {
                                        put("text", prompt)
                                    }
                                )
                            )
                        }
                    )
                )

                put(
                    "generationConfig",
                    JSONObject().apply {
                        put("maxOutputTokens", maxTokens)
                    }
                )
            }

            connection.outputStream.use {
                it.write(body.toString().toByteArray())
            }

            val responseCode = connection.responseCode

            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                connection.errorStream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    ?: ""
            }

            if (responseCode !in 200..299) {
                return ModelResponse(
                    outputText = classifyFailureText(responseCode),
                    status = "FAILED"
                )
            }

            val outputText = parseGoogleResponse(responseText)

            return if (outputText.isNotBlank()) {
                ModelResponse(
                    outputText = outputText,
                    status = "SUCCESS"
                )
            } else {
                ModelResponse(
                    outputText = "GOOGLE_EMPTY_RESPONSE",
                    status = "FAILED"
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun requestSelfReportedModelInfo(
        modelName: String,
        apiKey: String
    ): ModelInfo {
        val response = callApi(
            modelName = modelName,
            apiKey = apiKey,
            prompt = buildSelfReportPrompt(modelName),
            maxTokens = 512
        )

        if (response.status != "SUCCESS") return ModelInfo()

        return parseSelfReportedInfo(response.outputText)
    }

    private fun buildSelfReportPrompt(modelName: String): String {
        return """
            Return only compact JSON.
            Do not use markdown.
            Describe this model: $modelName.
            Required JSON keys:
            displayName, modelFamily, strengths, limitations, recommendedUse.
            Keep every value under 160 characters.
        """.trimIndent()
    }

    private fun parseSelfReportedInfo(text: String): ModelInfo {
        val cleanText = text
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return runCatching {
            val json = JSONObject(cleanText)

            ModelInfo(
                displayName = json.optString("displayName", ""),
                modelFamily = json.optString("modelFamily", ""),
                strengths = json.optString("strengths", ""),
                limitations = json.optString("limitations", ""),
                recommendedUse = json.optString("recommendedUse", ""),
                rawJson = cleanText
            )
        }.getOrElse {
            ModelInfo(rawJson = text.take(1000))
        }
    }

    private fun parseGoogleResponse(responseText: String): String {
        val root = JSONObject(responseText)
        val candidates = root.optJSONArray("candidates") ?: return ""

        if (candidates.length() == 0) return ""

        val firstCandidate = candidates.optJSONObject(0) ?: return ""
        val content = firstCandidate.optJSONObject("content") ?: return ""
        val parts = content.optJSONArray("parts") ?: return ""

        val result = StringBuilder()

        for (index in 0 until parts.length()) {
            val part = parts.optJSONObject(index) ?: continue
            val text = part.optString("text", "")
            if (text.isNotBlank()) {
                result.append(text)
            }
        }

        return result.toString().trim()
    }

    private fun classifyFailureText(responseCode: Int): String {
        return when (responseCode) {
            401 -> "GOOGLE_UNAUTHORIZED"
            403 -> "GOOGLE_FORBIDDEN"
            404 -> "GOOGLE_MODEL_NOT_FOUND"
            429 -> "GOOGLE_RATE_LIMITED"
            500, 502, 503, 504 -> "GOOGLE_SERVER_ERROR"
            else -> "GOOGLE_API_CALL_FAILED"
        }
    }
}