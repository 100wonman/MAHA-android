package com.maha.app

import org.json.JSONObject

/**
 * ProviderErrorSummary
 *
 * 쉬운 설명:
 * Provider/Fetched API 오류 응답을 앱 내부 표준 errorType과 짧은 사용자용 요약으로 정리한 값이다.
 * API Key 원문이나 raw error body 전체는 저장/표시하지 않는다.
 */
data class ProviderErrorSummary(
    val httpStatusCode: Int?,
    val errorType: String,
    val errorMessage: String,
    val providerErrorType: String? = null,
    val providerErrorCode: String? = null,
    val providerRawMessage: String? = null
) {
    fun displayErrorType(): String {
        return if (httpStatusCode != null) {
            "$errorType (HTTP $httpStatusCode)"
        } else {
            errorType
        }
    }

    fun toUserMessage(prefix: String): String {
        return buildString {
            append(prefix)
            append("\n")
            append(displayErrorType())
            if (errorMessage.isNotBlank()) {
                append(" · ")
                append(errorMessage)
            }
            providerErrorType?.takeIf { it.isNotBlank() }?.let {
                append("\nprovider error type: ").append(it)
            }
            providerErrorCode?.takeIf { it.isNotBlank() }?.let {
                append("\nprovider error code: ").append(it)
            }
        }.trim()
    }

    fun toMetadataSummary(): String {
        return buildString {
            httpStatusCode?.let { appendLine("httpStatus=$it") }
            appendLine("errorType=$errorType")
            providerErrorType?.takeIf { it.isNotBlank() }?.let { appendLine("providerErrorType=$it") }
            providerErrorCode?.takeIf { it.isNotBlank() }?.let { appendLine("providerErrorCode=$it") }
            providerRawMessage?.takeIf { it.isNotBlank() }?.let { appendLine("providerRawMessage=${it.take(300)}") }
            appendLine("displayErrorLabel=${displayErrorType()}")
        }.trim()
    }
}

/**
 * ProviderErrorFormatter
 *
 * 쉬운 설명:
 * HTTP status와 Provider error JSON을 공통 정책으로 요약한다.
 */
object ProviderErrorFormatter {

    fun fromHttpError(
        httpStatusCode: Int,
        responseText: String,
        fallbackMessage: String = "Provider 요청에 실패했습니다."
    ): ProviderErrorSummary {
        val parsed = parseProviderError(responseText)
        val internalType = mapHttpStatusToErrorType(httpStatusCode)
        val message = parsed.message
            ?.takeIf { it.isNotBlank() }
            ?.take(300)
            ?: fallbackMessage

        return ProviderErrorSummary(
            httpStatusCode = httpStatusCode,
            errorType = internalType,
            errorMessage = message,
            providerErrorType = parsed.type,
            providerErrorCode = parsed.code,
            providerRawMessage = parsed.message
        )
    }

    fun mapHttpStatusToErrorType(httpStatusCode: Int): String {
        return when (httpStatusCode) {
            400 -> "INVALID_REQUEST"
            401, 403 -> "AUTH_ERROR"
            408 -> "TIMEOUT"
            429 -> "RATE_LIMIT"
            in 500..599 -> "SERVER_ERROR"
            else -> "UNKNOWN_ERROR"
        }
    }

    private fun parseProviderError(responseText: String): ParsedProviderError {
        if (responseText.isBlank()) return ParsedProviderError()
        return runCatching {
            val root = JSONObject(responseText)
            val error = root.optJSONObject("error")
            ParsedProviderError(
                message = error?.optNullableString("message") ?: root.optNullableString("message"),
                type = error?.optNullableString("type") ?: root.optNullableString("type"),
                code = error?.optNullableString("code") ?: root.optNullableString("code")
            )
        }.getOrDefault(ParsedProviderError())
    }

    private data class ParsedProviderError(
        val message: String? = null,
        val type: String? = null,
        val code: String? = null
    )

    private fun JSONObject.optNullableString(name: String): String? {
        return if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotBlank() } else null
    }
}
