// GoogleModelProvider.kt

package com.maha.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object GoogleModelProvider : ModelProvider {

    private const val MODEL_NAME = "gemini-pro"
    private const val API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    override suspend fun generate(request: ModelRequest): ModelResponse {
        val apiKey = ApiKeyManager.getGoogleApiKey()

        if (apiKey.isBlank()) {
            return ModelResponse(
                outputText = "GOOGLE_API_KEY_NOT_SET",
                status = "SUCCESS"
            )
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                callGeminiApi(
                    apiKey = apiKey,
                    prompt = request.inputText
                )
            }.getOrElse {
                ModelResponse(
                    outputText = "GOOGLE_API_CALL_FAILED",
                    status = "FAILED"
                )
            }
        }
    }

    private fun callGeminiApi(
        apiKey: String,
        prompt: String
    ): ModelResponse {
        val encodedApiKey = URLEncoder.encode(apiKey, "UTF-8")
        val url = URL("$API_BASE_URL/$MODEL_NAME:generateContent?key=$encodedApiKey")

        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
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
                connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText)
                    ?: ""
            }

            if (responseCode !in 200..299) {
                return ModelResponse(
                    outputText = "GOOGLE_API_CALL_FAILED",
                    status = "FAILED"
                )
            }

            val outputText = parseGeminiResponse(responseText)

            return ModelResponse(
                outputText = outputText.ifBlank { "GOOGLE_API_CALL_FAILED" },
                status = if (outputText.isNotBlank()) "SUCCESS" else "FAILED"
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

        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
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
}