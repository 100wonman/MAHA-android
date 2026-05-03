package com.maha.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * OpenAIResponsesProviderAdapter
 *
 * 쉬운 설명:
 * OpenAI 공식 Responses API(/v1/responses)를 호출하는 Adapter다.
 * 이번 단계에서는 일반 텍스트 대화만 지원한다.
 * Web Search, streaming, tool/function execution은 구현하지 않는다.
 */
class OpenAIResponsesProviderAdapter(
    private val defaultBaseUrl: String = "https://api.openai.com/v1",
    private val connectTimeoutMillis: Int = 30_000,
    private val readTimeoutMillis: Int = 60_000
) {

    suspend fun execute(
        request: OpenAIResponsesRequest,
        apiKey: String
    ): OpenAIResponsesResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val endpoint = buildResponsesEndpoint(request.baseUrl)

        try {
            if (apiKey.isBlank()) {
                return@withContext OpenAIResponsesResult.apiKeyMissing().copy(
                    latencySec = elapsedSec(startedAt),
                    endpoint = endpoint
                )
            }

            if (request.modelName.isBlank()) {
                return@withContext OpenAIResponsesResult.failure(
                    errorType = "MODEL_MISSING",
                    errorMessage = "OpenAI Responses API 호출을 위한 모델명이 비어 있습니다.",
                    latencySec = elapsedSec(startedAt),
                    endpoint = endpoint,
                    rawMetadataSummary = "modelName=blank"
                )
            }

            if (request.prompt.isBlank()) {
                return@withContext OpenAIResponsesResult.failure(
                    errorType = "INVALID_REQUEST",
                    errorMessage = "OpenAI Responses API 호출을 위한 프롬프트가 비어 있습니다.",
                    latencySec = elapsedSec(startedAt),
                    endpoint = endpoint,
                    rawMetadataSummary = "prompt=blank"
                )
            }

            if (request.enableWebSearch) {
                return@withContext OpenAIResponsesResult.failure(
                    errorType = "OPENAI_WEB_SEARCH_NOT_IMPLEMENTED",
                    errorMessage = "OpenAI Web Search 호출은 아직 구현되지 않았습니다. Web Search를 끄고 다시 시도하세요.",
                    latencySec = elapsedSec(startedAt),
                    endpoint = endpoint,
                    rawMetadataSummary = "webSearchRequested=true, webSearchImplemented=false",
                    actualApiCall = false
                )
            }

            val requestBody = buildRequestBody(request).toString()
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            try {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(requestBody)
                    writer.flush()
                }

                val statusCode = connection.responseCode
                val responseText = readConnectionBody(connection, statusCode)
                val latencySec = elapsedSec(startedAt)

                if (statusCode !in 200..299) {
                    val providerError = ProviderErrorFormatter.fromHttpError(
                        httpStatusCode = statusCode,
                        responseText = responseText,
                        fallbackMessage = "OpenAI Responses API 호출에 실패했습니다."
                    )
                    return@withContext OpenAIResponsesResult.failure(
                        errorType = providerError.errorType,
                        errorMessage = providerError.toUserMessage("OpenAI Responses API 호출에 실패했습니다."),
                        latencySec = latencySec,
                        endpoint = endpoint,
                        rawMetadataSummary = providerError.toMetadataSummary(),
                        actualApiCall = true
                    )
                }

                val parsed = parseResponsesBody(responseText)
                if (parsed.rawText.isBlank()) {
                    return@withContext OpenAIResponsesResult.failure(
                        errorType = "PARSE_ERROR",
                        errorMessage = "OpenAI Responses API 응답에서 표시 가능한 output text를 찾지 못했습니다.",
                        latencySec = latencySec,
                        endpoint = endpoint,
                        rawMetadataSummary = parsed.rawMetadataSummary,
                        actualApiCall = true
                    )
                }

                return@withContext OpenAIResponsesResult.success(
                    rawText = parsed.rawText,
                    latencySec = latencySec,
                    endpoint = endpoint,
                    responseId = parsed.responseId,
                    responseStatus = parsed.responseStatus,
                    rawMetadataSummary = parsed.rawMetadataSummary,
                    outputTextParsed = parsed.outputTextParsed,
                    outputContentParsed = parsed.outputContentParsed
                )
            } finally {
                connection.disconnect()
            }
        } catch (timeout: SocketTimeoutException) {
            OpenAIResponsesResult.failure(
                errorType = "TIMEOUT",
                errorMessage = timeout.message ?: "OpenAI Responses API 요청 시간이 초과되었습니다.",
                latencySec = elapsedSec(startedAt),
                endpoint = endpoint,
                rawMetadataSummary = "SocketTimeoutException",
                actualApiCall = true
            )
        } catch (json: JSONException) {
            OpenAIResponsesResult.failure(
                errorType = "PARSE_ERROR",
                errorMessage = json.message?.take(240) ?: "OpenAI Responses API 응답 파싱에 실패했습니다.",
                latencySec = elapsedSec(startedAt),
                endpoint = endpoint,
                rawMetadataSummary = "JSONException",
                actualApiCall = true
            )
        } catch (throwable: Throwable) {
            OpenAIResponsesResult.failure(
                errorType = "UNKNOWN_ERROR",
                errorMessage = throwable.message?.take(240) ?: "OpenAI Responses API 호출 중 알 수 없는 오류가 발생했습니다.",
                latencySec = elapsedSec(startedAt),
                endpoint = endpoint,
                rawMetadataSummary = throwable::class.java.simpleName,
                actualApiCall = true
            )
        }
    }

    private fun buildResponsesEndpoint(baseUrl: String?): String {
        val rawBase = baseUrl
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: defaultBaseUrl

        val normalized = rawBase.trimEnd('/')
        return if (normalized.endsWith("/responses")) {
            normalized
        } else {
            "$normalized/responses"
        }
    }

    private fun buildRequestBody(request: OpenAIResponsesRequest): JSONObject {
        val root = JSONObject()
            .put("model", request.modelName)
            .put("input", request.prompt)

        request.temperature?.let { root.put("temperature", it) }

        return root
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

    private fun parseResponsesBody(responseText: String): ParsedOpenAIResponse {
        val root = JSONObject(responseText)
        val responseId = root.optNullableString("id")
        val responseStatus = root.optNullableString("status")

        val outputText = root.optNullableString("output_text")
        if (!outputText.isNullOrBlank()) {
            return ParsedOpenAIResponse(
                rawText = outputText.trim(),
                responseId = responseId,
                responseStatus = responseStatus,
                outputTextParsed = true,
                outputContentParsed = false,
                rawMetadataSummary = buildMetadataSummary(
                    root = root,
                    answerSource = "output_text",
                    outputTextParsed = true,
                    outputContentParsed = false,
                    contentTextCount = countOutputContentText(root.optJSONArray("output"))
                )
            )
        }

        val collectedParts = collectOutputContentTextParts(root.optJSONArray("output"))
        val collected = collectedParts.joinToString("\n\n")
        return ParsedOpenAIResponse(
            rawText = collected.trim(),
            responseId = responseId,
            responseStatus = responseStatus,
            outputTextParsed = false,
            outputContentParsed = collected.isNotBlank(),
            rawMetadataSummary = buildMetadataSummary(
                root = root,
                answerSource = "output.content",
                outputTextParsed = false,
                outputContentParsed = collected.isNotBlank(),
                contentTextCount = collectedParts.size
            )
        )
    }

    private fun collectOutputContentTextParts(output: JSONArray?): List<String> {
        if (output == null) return emptyList()
        val parts = mutableListOf<String>()

        for (outputIndex in 0 until output.length()) {
            val item = output.optJSONObject(outputIndex) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (contentIndex in 0 until content.length()) {
                val contentItem = content.optJSONObject(contentIndex) ?: continue
                val type = contentItem.optString("type")
                if (!isAssistantTextContent(type)) continue

                extractTextCandidates(contentItem)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { parts.add(it) }
            }
        }

        return parts.distinct()
    }

    private fun countOutputContentText(output: JSONArray?): Int {
        return collectOutputContentTextParts(output).size
    }

    private fun extractTextCandidates(contentItem: JSONObject): List<String> {
        val candidates = mutableListOf<String>()

        contentItem.optNullableStringStrict("text")?.let { candidates.add(it) }
        contentItem.optJSONObject("text")?.let { textObject ->
            textObject.optNullableStringStrict("value")?.let { candidates.add(it) }
            textObject.optNullableStringStrict("text")?.let { candidates.add(it) }
            textObject.optNullableStringStrict("content")?.let { candidates.add(it) }
        }
        contentItem.optNullableStringStrict("content")?.let { candidates.add(it) }
        contentItem.optJSONObject("content")?.let { contentObject ->
            contentObject.optNullableStringStrict("value")?.let { candidates.add(it) }
            contentObject.optNullableStringStrict("text")?.let { candidates.add(it) }
        }

        return candidates
    }

    private fun isAssistantTextContent(type: String): Boolean {
        val normalized = type.trim().lowercase()
        return normalized.isBlank() ||
                normalized == "output_text" ||
                normalized == "text" ||
                normalized == "message"
    }

    private fun buildMetadataSummary(
        root: JSONObject,
        answerSource: String,
        outputTextParsed: Boolean,
        outputContentParsed: Boolean,
        contentTextCount: Int
    ): String {
        val output = root.optJSONArray("output")
        return buildString {
            root.optNullableString("id")?.let { appendLine("id=$it") }
            root.optNullableString("model")?.let { appendLine("model=$it") }
            root.optNullableString("status")?.let { appendLine("status=$it") }
            appendLine("answerSource=$answerSource")
            appendLine("outputCount=${output?.length() ?: 0}")
            appendLine("contentTextCount=$contentTextCount")
            appendLine("outputTextParsed=$outputTextParsed")
            appendLine("outputContentParsed=$outputContentParsed")
        }.trim()
    }

    private fun parseErrorMessage(responseText: String): String? {
        if (responseText.isBlank()) return null
        return runCatching {
            val root = JSONObject(responseText)
            val error = root.optJSONObject("error")
            error?.optString("message")?.takeIf { it.isNotBlank() }?.take(300)
                ?: root.optString("message").takeIf { it.isNotBlank() }?.take(300)
        }.getOrNull()
    }

    private fun mapHttpStatusToErrorType(statusCode: Int): String {
        return ProviderErrorFormatter.mapHttpStatusToErrorType(statusCode)
    }

    private fun elapsedSec(startedAt: Long): Double {
        return (System.currentTimeMillis() - startedAt).coerceAtLeast(1) / 1000.0
    }

    private fun JSONObject.optNullableString(name: String): String? {
        return if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotBlank() } else null
    }

    private fun JSONObject.optNullableStringStrict(name: String): String? {
        if (!has(name) || isNull(name)) return null
        val value = opt(name)
        return if (value is String && value.isNotBlank()) value else null
    }
}

private data class ParsedOpenAIResponse(
    val rawText: String,
    val responseId: String?,
    val responseStatus: String?,
    val outputTextParsed: Boolean,
    val outputContentParsed: Boolean,
    val rawMetadataSummary: String?
)
