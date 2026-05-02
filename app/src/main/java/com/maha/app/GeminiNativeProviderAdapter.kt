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
 * GeminiNativeProviderAdapter
 *
 * 쉬운 설명:
 * Gemini native generateContent endpoint를 직접 호출하는 Adapter다.
 *
 * 현재 단계 정책:
 * - 실제 HTTP POST 호출은 구현한다.
 * - ConversationEngine native 분기에는 아직 연결하지 않는다.
 * - nativeGroundingAvailable=true 전환은 하지 않는다.
 * - Google Search grounding은 request.enableGoogleSearch=true일 때만 요청 body에 포함한다.
 * - thought part는 ASSISTANT 본문으로 반환하지 않는다.
 */
class GeminiNativeProviderAdapter(
    private val endpointBase: String = "https://generativelanguage.googleapis.com/v1beta",
    private val connectTimeoutMillis: Int = 30_000,
    private val readTimeoutMillis: Int = 60_000
) {

    suspend fun execute(
        request: GeminiNativeRequest,
        apiKey: String
    ): GeminiNativeResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()

        try {
            if (apiKey.isBlank()) {
                return@withContext GeminiNativeResult.apiKeyMissing(
                    latencySec = elapsedSec(startedAt)
                )
            }

            if (request.modelName.isBlank()) {
                return@withContext GeminiNativeResult.failure(
                    latencySec = elapsedSec(startedAt),
                    errorType = "INVALID_REQUEST",
                    errorMessage = "Gemini native 호출을 위한 모델명이 비어 있습니다.",
                    rawMetadataSummary = "modelName: blank"
                )
            }

            if (request.prompt.isBlank()) {
                return@withContext GeminiNativeResult.failure(
                    latencySec = elapsedSec(startedAt),
                    errorType = "INVALID_REQUEST",
                    errorMessage = "Gemini native 호출을 위한 프롬프트가 비어 있습니다.",
                    rawMetadataSummary = "prompt: blank"
                )
            }

            val endpoint = buildGenerateContentEndpoint(request.modelName)
            val requestBody = buildRequestBody(request).toString()

            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-key", apiKey)
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            val statusCode = connection.responseCode
            val responseText = readConnectionBody(connection, statusCode)
            connection.disconnect()

            if (statusCode !in 200..299) {
                val httpError = parseHttpErrorBody(responseText, statusCode, request.enableGoogleSearch)
                return@withContext GeminiNativeResult.failure(
                    latencySec = elapsedSec(startedAt),
                    errorType = httpError.errorType,
                    errorMessage = httpError.userMessage,
                    rawMetadataSummary = httpError.summary
                )
            }

            val parsed = parseGenerateContentResponse(responseText, request.enableGoogleSearch)

            if (parsed.errorType != null) {
                return@withContext GeminiNativeResult.failure(
                    latencySec = elapsedSec(startedAt),
                    errorType = parsed.errorType,
                    errorMessage = parsed.errorMessage ?: "Gemini native 응답을 처리하지 못했습니다.",
                    rawMetadataSummary = parsed.rawMetadataSummary,
                    finishReason = parsed.finishReason,
                    answerPartCount = parsed.answerPartCount,
                    thoughtPartCount = parsed.thoughtPartCount
                )
            }

            GeminiNativeResult(
                success = true,
                rawText = parsed.answerText,
                groundingUsed = parsed.groundingUsed,
                citations = parsed.citations,
                searchQueries = parsed.searchQueries,
                thinkingSummary = parsed.thinkingSummary,
                latencySec = elapsedSec(startedAt),
                errorType = null,
                errorMessage = null,
                rawMetadataSummary = parsed.rawMetadataSummary,
                finishReason = parsed.finishReason,
                answerPartCount = parsed.answerPartCount,
                thoughtPartCount = parsed.thoughtPartCount
            )
        } catch (timeout: SocketTimeoutException) {
            GeminiNativeResult.failure(
                latencySec = elapsedSec(startedAt),
                errorType = "TIMEOUT",
                errorMessage = timeout.message ?: "Gemini native 요청 시간이 초과되었습니다.",
                rawMetadataSummary = "SocketTimeoutException: ${timeout.message ?: "timeout"}"
            )
        } catch (throwable: Throwable) {
            GeminiNativeResult.failure(
                latencySec = elapsedSec(startedAt),
                errorType = "UNKNOWN",
                errorMessage = throwable.message ?: "Gemini native 호출 중 알 수 없는 오류가 발생했습니다.",
                rawMetadataSummary = "${throwable::class.java.simpleName}: ${throwable.message ?: "unknown"}"
            )
        }
    }

    private fun buildGenerateContentEndpoint(modelName: String): String {
        val normalizedModelName = modelName
            .trim()
            .removePrefix("models/")
            .trim('/')
        return "${endpointBase.trimEnd('/')}/models/$normalizedModelName:generateContent"
    }

    private fun buildRequestBody(request: GeminiNativeRequest): JSONObject {
        val root = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(
                            JSONObject().put("text", request.prompt)
                        )
                    )
                )
            )

        if (request.enableGoogleSearch) {
            root.put(
                "tools",
                JSONArray().put(
                    JSONObject().put("google_search", JSONObject())
                )
            )
        }

        val generationConfig = JSONObject()
        request.temperature?.let { generationConfig.put("temperature", it) }
        request.maxOutputTokens?.let { generationConfig.put("maxOutputTokens", it) }
        if (generationConfig.length() > 0) {
            root.put("generationConfig", generationConfig)
        }

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

    private fun parseGenerateContentResponse(
        responseText: String,
        googleSearchRequested: Boolean
    ): GeminiNativeParsedResponse {
        return runCatching {
            val root = JSONObject(responseText)

            val responseError = root.optJSONObject("error")
            if (responseError != null) {
                val mapped = mapResponseError(responseError, googleSearchRequested)
                return@runCatching GeminiNativeParsedResponse.failure(
                    errorType = mapped.errorType,
                    errorMessage = mapped.userMessage,
                    rawMetadataSummary = mapped.summary
                )
            }

            val candidates = root.optJSONArray("candidates")
            val rootKeys = keysOf(root)
            if (candidates == null || candidates.length() == 0) {
                return@runCatching GeminiNativeParsedResponse.failure(
                    errorType = "INVALID_RESPONSE",
                    errorMessage = "Gemini native 응답에 candidates가 없습니다.",
                    rawMetadataSummary = "candidates: missing_or_empty\nrootKeys: ${rootKeys.joinToString(",")}"
                )
            }

            val firstCandidate = candidates.optJSONObject(0)
            if (firstCandidate == null) {
                return@runCatching GeminiNativeParsedResponse.failure(
                    errorType = "INVALID_RESPONSE",
                    errorMessage = "Gemini native 응답의 candidates[0]가 객체가 아닙니다.",
                    rawMetadataSummary = "candidates[0]: not_object\nrootKeys: ${rootKeys.joinToString(",")}"
                )
            }

            val finishReason = firstCandidate.optString("finishReason", "")
                .ifBlank { firstCandidate.optString("finish_reason", "") }
                .takeIf { it.isNotBlank() }
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val candidateKeys = keysOf(firstCandidate)
            val contentKeys = keysOf(content)

            if (parts == null || parts.length() == 0) {
                return@runCatching GeminiNativeParsedResponse.failure(
                    errorType = "INVALID_RESPONSE",
                    errorMessage = "Gemini native 응답에 표시 가능한 parts가 없습니다.",
                    rawMetadataSummary = buildString {
                        appendLine("finishReason: ${finishReason ?: "null"}")
                        appendLine("parts: missing_or_empty")
                        appendLine("contentKeys: ${contentKeys.joinToString(",")}")
                        appendLine("candidateKeys: ${candidateKeys.joinToString(",")}")
                        appendLine("rootKeys: ${rootKeys.joinToString(",")}")
                    }.trim(),
                    finishReason = finishReason
                )
            }

            val answerParts = mutableListOf<String>()
            val thoughtPreviews = mutableListOf<String>()
            var thoughtPartCount = 0

            for (index in 0 until parts.length()) {
                val part = parts.optJSONObject(index) ?: continue
                val text = part.optString("text", "").trim()
                val isThoughtPart = isThoughtPart(part)

                if (isThoughtPart) {
                    thoughtPartCount += 1
                    if (text.isNotBlank()) {
                        thoughtPreviews.add(text.take(500))
                    }
                } else if (text.isNotBlank()) {
                    answerParts.add(text)
                }
            }

            val answerText = answerParts.joinToString("\n\n").trim()
            val thinkingSummary = thoughtPreviews
                .joinToString("\n\n")
                .take(1_000)
                .takeIf { it.isNotBlank() }
            val groundingMetadata = firstCandidate.optJSONObject("groundingMetadata")
            val grounding = parseGroundingMetadata(groundingMetadata)

            if (answerText.isBlank()) {
                return@runCatching GeminiNativeParsedResponse.failure(
                    errorType = "INVALID_RESPONSE",
                    errorMessage = "Gemini native 응답에서 thought를 제외한 표시 가능한 답변을 찾지 못했습니다.",
                    rawMetadataSummary = buildString {
                        appendLine("finishReason: ${finishReason ?: "null"}")
                        appendLine("answerPartCount: 0")
                        appendLine("thoughtPartCount: $thoughtPartCount")
                        appendLine("groundingMetadataPresent: ${groundingMetadata != null}")
                        appendLine("candidateKeys: ${candidateKeys.joinToString(",")}")
                        appendLine("rootKeys: ${rootKeys.joinToString(",")}")
                    }.trim(),
                    finishReason = finishReason,
                    answerPartCount = 0,
                    thoughtPartCount = thoughtPartCount
                )
            }

            GeminiNativeParsedResponse(
                answerText = answerText,
                groundingUsed = grounding.groundingUsed,
                citations = grounding.citations,
                searchQueries = grounding.searchQueries,
                thinkingSummary = thinkingSummary,
                rawMetadataSummary = buildString {
                    appendLine("finishReason: ${finishReason ?: "null"}")
                    appendLine("answerPartCount: ${answerParts.size}")
                    appendLine("thoughtPartCount: $thoughtPartCount")
                    appendLine("groundingMetadataPresent: ${groundingMetadata != null}")
                    appendLine("groundingUsed: ${grounding.groundingUsed}")
                    appendLine("citationCount: ${grounding.citations.size}")
                    appendLine("searchQueryCount: ${grounding.searchQueries.size}")
                    if (googleSearchRequested && groundingMetadata == null) {
                        appendLine("groundingNote: groundingMetadata missing")
                    }
                    appendLine("candidateKeys: ${candidateKeys.joinToString(",")}")
                    appendLine("rootKeys: ${rootKeys.joinToString(",")}")
                }.trim(),
                finishReason = finishReason,
                answerPartCount = answerParts.size,
                thoughtPartCount = thoughtPartCount,
                errorType = null,
                errorMessage = null
            )
        }.getOrElse { throwable ->
            GeminiNativeParsedResponse.failure(
                errorType = "INVALID_RESPONSE",
                errorMessage = "Gemini native 응답 파싱 중 오류가 발생했습니다.",
                rawMetadataSummary = "parseError: ${throwable::class.java.simpleName}: ${throwable.message ?: "unknown"}"
            )
        }
    }

    private fun parseGroundingMetadata(groundingMetadata: JSONObject?): GeminiNativeGroundingParseResult {
        if (groundingMetadata == null) {
            return GeminiNativeGroundingParseResult(
                groundingUsed = false,
                citations = emptyList(),
                searchQueries = emptyList()
            )
        }

        val searchQueries = extractStringArray(groundingMetadata.optJSONArray("webSearchQueries"))
        val snippetByChunkIndex = buildSnippetByChunkIndex(groundingMetadata.optJSONArray("groundingSupports"))
        val citations = mutableListOf<GroundingCitation>()
        val chunks = groundingMetadata.optJSONArray("groundingChunks")

        if (chunks != null) {
            for (index in 0 until chunks.length()) {
                val chunk = chunks.optJSONObject(index) ?: continue
                val web = chunk.optJSONObject("web") ?: continue
                val title = web.optString("title", "").takeIf { it.isNotBlank() }
                val uri = web.optString("uri", "").takeIf { it.isNotBlank() }

                if (title != null || uri != null) {
                    citations.add(
                        GroundingCitation(
                            title = title,
                            url = uri,
                            snippet = snippetByChunkIndex[index],
                            sourceIndex = index
                        )
                    )
                }
            }
        }

        val hasSearchEntryPoint = groundingMetadata.has("searchEntryPoint") &&
                !groundingMetadata.isNull("searchEntryPoint")

        return GeminiNativeGroundingParseResult(
            groundingUsed = searchQueries.isNotEmpty() || citations.isNotEmpty() || hasSearchEntryPoint,
            citations = citations,
            searchQueries = searchQueries
        )
    }

    private fun buildSnippetByChunkIndex(groundingSupports: JSONArray?): Map<Int, String> {
        if (groundingSupports == null) return emptyMap()

        val snippetByChunkIndex = mutableMapOf<Int, String>()
        for (supportIndex in 0 until groundingSupports.length()) {
            val support = groundingSupports.optJSONObject(supportIndex) ?: continue
            val segmentText = support
                .optJSONObject("segment")
                ?.optString("text", "")
                ?.takeIf { it.isNotBlank() }
                ?: continue
            val indices = support.optJSONArray("groundingChunkIndices") ?: continue

            for (indexPosition in 0 until indices.length()) {
                val chunkIndex = indices.optInt(indexPosition, -1)
                if (chunkIndex >= 0 && !snippetByChunkIndex.containsKey(chunkIndex)) {
                    snippetByChunkIndex[chunkIndex] = segmentText.take(500)
                }
            }
        }
        return snippetByChunkIndex
    }

    private fun extractStringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val values = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val value = array.optString(index, "").trim()
            if (value.isNotBlank()) {
                values.add(value)
            }
        }
        return values
    }

    private fun isThoughtPart(part: JSONObject): Boolean {
        val explicitThought = part.optBoolean("thought", false)
        val hasThoughtSignature = part.has("thoughtSignature") && !part.isNull("thoughtSignature")
        val hasThoughtLikeKey = keysOf(part).any { key ->
            key.equals("thoughts", ignoreCase = true) ||
                    key.equals("thinking", ignoreCase = true)
        }
        return explicitThought || hasThoughtSignature || hasThoughtLikeKey
    }

    private fun parseHttpErrorBody(
        responseText: String,
        statusCode: Int,
        googleSearchRequested: Boolean
    ): GeminiNativeHttpError {
        return runCatching {
            val root = JSONObject(responseText)
            val error = root.optJSONObject("error")
            val message = error?.optString("message", "")?.takeIf { it.isNotBlank() }
                ?: "Gemini native HTTP 오류가 발생했습니다."
            val status = error?.optString("status", "")?.takeIf { it.isNotBlank() }
            val code = error?.optInt("code", statusCode) ?: statusCode
            val errorType = mapHttpStatusToErrorType(statusCode, message, googleSearchRequested)

            GeminiNativeHttpError(
                errorType = errorType,
                userMessage = message.take(500),
                summary = buildString {
                    appendLine("httpStatusCode: $statusCode")
                    appendLine("errorCode: $code")
                    appendLine("errorStatus: ${status ?: "null"}")
                    appendLine("errorType: $errorType")
                    appendLine("messagePreview: ${message.take(300)}")
                }.trim()
            )
        }.getOrElse {
            val errorType = mapHttpStatusToErrorType(statusCode, responseText, googleSearchRequested)
            GeminiNativeHttpError(
                errorType = errorType,
                userMessage = responseText.take(500).ifBlank { "Gemini native HTTP 오류가 발생했습니다." },
                summary = buildString {
                    appendLine("httpStatusCode: $statusCode")
                    appendLine("errorType: $errorType")
                    appendLine("messagePreview: ${responseText.take(300)}")
                }.trim()
            )
        }
    }

    private fun mapResponseError(
        error: JSONObject,
        googleSearchRequested: Boolean
    ): GeminiNativeHttpError {
        val message = error.optString("message", "Gemini native 응답 오류가 발생했습니다.")
        val status = error.optString("status", "").takeIf { it.isNotBlank() }
        val code = error.optInt("code", 0)
        val statusCode = if (code > 0) code else 400
        val errorType = mapHttpStatusToErrorType(statusCode, message, googleSearchRequested)

        return GeminiNativeHttpError(
            errorType = errorType,
            userMessage = message.take(500),
            summary = buildString {
                appendLine("responseError: true")
                appendLine("errorCode: $code")
                appendLine("errorStatus: ${status ?: "null"}")
                appendLine("errorType: $errorType")
                appendLine("messagePreview: ${message.take(300)}")
            }.trim()
        )
    }

    private fun mapHttpStatusToErrorType(
        statusCode: Int,
        message: String,
        googleSearchRequested: Boolean
    ): String {
        val lowerMessage = message.lowercase()
        val looksLikeToolUnsupported = googleSearchRequested &&
                (lowerMessage.contains("google_search") ||
                        lowerMessage.contains("googlesearch") ||
                        lowerMessage.contains("grounding") ||
                        lowerMessage.contains("tool")) &&
                (lowerMessage.contains("unsupported") ||
                        lowerMessage.contains("not supported") ||
                        lowerMessage.contains("invalid") ||
                        lowerMessage.contains("not enabled"))

        if (looksLikeToolUnsupported) return "MODEL_UNSUPPORTED_TOOL"

        return when (statusCode) {
            400 -> "INVALID_REQUEST"
            401, 403 -> "AUTH_FAILED"
            408 -> "TIMEOUT"
            429 -> "RATE_LIMIT"
            in 500..599 -> "SERVER_ERROR"
            else -> "UNKNOWN"
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

    private fun elapsedSec(startedAt: Long): Double {
        return (System.currentTimeMillis() - startedAt) / 1000.0
    }
}

private data class GeminiNativeParsedResponse(
    val answerText: String,
    val groundingUsed: Boolean,
    val citations: List<GroundingCitation>,
    val searchQueries: List<String>,
    val thinkingSummary: String?,
    val rawMetadataSummary: String?,
    val finishReason: String?,
    val answerPartCount: Int,
    val thoughtPartCount: Int,
    val errorType: String?,
    val errorMessage: String?
) {
    companion object {
        fun failure(
            errorType: String,
            errorMessage: String,
            rawMetadataSummary: String? = null,
            finishReason: String? = null,
            answerPartCount: Int = 0,
            thoughtPartCount: Int = 0
        ): GeminiNativeParsedResponse {
            return GeminiNativeParsedResponse(
                answerText = "",
                groundingUsed = false,
                citations = emptyList(),
                searchQueries = emptyList(),
                thinkingSummary = null,
                rawMetadataSummary = rawMetadataSummary,
                finishReason = finishReason,
                answerPartCount = answerPartCount,
                thoughtPartCount = thoughtPartCount,
                errorType = errorType,
                errorMessage = errorMessage
            )
        }
    }
}

private data class GeminiNativeGroundingParseResult(
    val groundingUsed: Boolean,
    val citations: List<GroundingCitation>,
    val searchQueries: List<String>
)

private data class GeminiNativeHttpError(
    val errorType: String,
    val userMessage: String,
    val summary: String
)
