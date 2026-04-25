package com.maha.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

object GoogleModelProvider : ModelProvider {

    private const val TAG = "GoogleModelProvider"
    private const val BASE_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/"

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
                callApi(
                    modelName = request.modelName,
                    apiKey = apiKey,
                    prompt = request.inputText,
                    maxTokens = 1024
                )
            }.getOrElse {
                ModelResponse(
                    outputText = "GOOGLE_API_CALL_FAILED",
                    status = "FAILED"
                )
            }
        }
    }

    suspend fun testModel(modelName: String): ModelTestRecord {
        val apiKey = ApiKeyManager.getGoogleApiKey()

        if (apiKey.isBlank()) {
            return ModelTestRecord(
                providerName = ModelProviderType.GOOGLE,
                modelName = modelName,
                status = NvidiaModelTestStatus.AUTH_REQUIRED,
                lastTestedAt = getCurrentTimeText(),
                httpStatusCode = 401,
                message = "API Key 없음",
                latencyMs = 0
            )
        }

        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()

            runCatching {
                callApi(
                    modelName = modelName,
                    apiKey = apiKey,
                    prompt = "Reply with only: OK",
                    maxTokens = 8
                )
            }.fold(
                onSuccess = {
                    val latency = System.currentTimeMillis() - start
                    ModelTestRecord(
                        providerName = ModelProviderType.GOOGLE,
                        modelName = modelName,
                        status = NvidiaModelTestStatus.AVAILABLE,
                        lastTestedAt = getCurrentTimeText(),
                        httpStatusCode = 200,
                        message = "호출 가능",
                        latencyMs = latency
                    )
                },
                onFailure = { e ->
                    val latency = System.currentTimeMillis() - start

                    val status = when (e) {
                        is SocketTimeoutException -> NvidiaModelTestStatus.FAILED
                        else -> NvidiaModelTestStatus.FAILED
                    }

                    ModelTestRecord(
                        providerName = ModelProviderType.GOOGLE,
                        modelName = modelName,
                        status = status,
                        lastTestedAt = getCurrentTimeText(),
                        httpStatusCode = -1,
                        message = e.message ?: "실패",
                        latencyMs = latency
                    )
                }
            )
        }
    }

    private fun callApi(
        modelName: String,
        apiKey: String,
        prompt: String,
        maxTokens: Int
    ): ModelResponse {

        val url =
            "$BASE_URL$modelName:generateContent?key=$apiKey"

        val connection = URL(url).openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.connectTimeout = 15000
        connection.readTimeout = 45000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")

        val body = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().apply {
                    put("parts", JSONArray().put(
                        JSONObject().apply {
                            put("text", prompt)
                        }
                    ))
                }
            ))
        }

        connection.outputStream.use {
            it.write(body.toString().toByteArray())
        }

        val code = connection.responseCode

        if (code !in 200..299) {
            throw RuntimeException("HTTP $code")
        }

        return ModelResponse(
            outputText = "OK",
            status = "SUCCESS"
        )
    }
}