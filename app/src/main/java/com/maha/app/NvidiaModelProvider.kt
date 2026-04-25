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
                    prompt = request.inputText
                )
            }.getOrElse { exception ->
                Log.e(TAG, "NVIDIA API exception: ${exception.message}", exception)
                ModelResponse(
                    outputText = "NVIDIA_API_CALL_FAILED",
                    status = "FAILED"
                )
            }
        }
    }

    private fun callNvidiaApi(
        apiKey: String,
        modelName: String,
        prompt: String
    ): ModelResponse {
        Log.d(TAG, "NVIDIA request URL: $CHAT_COMPLETIONS_URL")
        Log.d(TAG, "NVIDIA selected model: $modelName")
        Log.d(TAG, "NVIDIA API key: ${maskApiKey(apiKey)}")

        val connection = URL(CHAT_COMPLETIONS_URL).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")

            val requestJson = createRequestJson(
                modelName = modelName,
                prompt = prompt
            )

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

            Log.d(TAG, "NVIDIA HTTP status code: $responseCode")
            Log.d(TAG, "NVIDIA response body: $responseText")

            if (responseCode !in 200..299) {
                return ModelResponse(
                    outputText = "NVIDIA_API_CALL_FAILED",
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
        } finally {
            connection.disconnect()
        }
    }

    private fun createRequestJson(
        modelName: String,
        prompt: String
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
            put("max_tokens", 1024)
            put("temperature", 0.7)
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

        return message.optString("content", "")
    }

    private fun maskApiKey(apiKey: String): String {
        val prefix = apiKey.take(4)
        return "$prefix****"
    }
}