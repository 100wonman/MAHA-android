// ModelCapability.kt

package com.maha.app

object ModelCapability {
    const val TEXT_ONLY = "TEXT_ONLY"
    const val MULTIMODAL_INPUT = "MULTIMODAL_INPUT"
    const val IMAGE_INPUT = "IMAGE_INPUT"
    const val IMAGE_OUTPUT = "IMAGE_OUTPUT"
    const val VIDEO_INPUT = "VIDEO_INPUT"
    const val AUDIO_INPUT = "AUDIO_INPUT"
    const val WEB_GROUNDING = "WEB_GROUNDING"
    const val CODING = "CODING"
    const val REASONING = "REASONING"
    const val FAST = "FAST"
    const val LONG_CONTEXT = "LONG_CONTEXT"
    const val EMBEDDING = "EMBEDDING"
    const val RERANKING = "RERANKING"
    const val VISION = "VISION"
    const val STRUCTURED_OUTPUT = "STRUCTURED_OUTPUT"
    const val JSON_OUTPUT = "JSON_OUTPUT"
    const val UNKNOWN = "UNKNOWN"
}

object ModelCapabilitySource {
    const val API_METADATA = "API_METADATA"
    const val TEST_RESULT = "TEST_RESULT"
    const val SELF_REPORTED = "SELF_REPORTED"
    const val MANUAL = "MANUAL"
}

object ModelMetadataConfidence {
    const val HIGH = "HIGH"
    const val MEDIUM_HIGH = "MEDIUM_HIGH"
    const val MEDIUM = "MEDIUM"
    const val LOW = "LOW"
    const val UNKNOWN = "UNKNOWN"
}

object ModelCapabilityTestType {
    const val TEXT_TEST = "TEXT_TEST"
    const val SUMMARY_TEST = "SUMMARY_TEST"
    const val CODING_TEST = "CODING_TEST"
    const val REASONING_TEST = "REASONING_TEST"
    const val JSON_TEST = "JSON_TEST"

    fun allBasicTests(): List<String> {
        return listOf(
            TEXT_TEST,
            SUMMARY_TEST,
            CODING_TEST,
            REASONING_TEST,
            JSON_TEST
        )
    }

    fun toDisplayName(testType: String): String {
        return when (testType) {
            TEXT_TEST -> "텍스트 생성 테스트"
            SUMMARY_TEST -> "요약 테스트"
            CODING_TEST -> "코딩 테스트"
            REASONING_TEST -> "추론 테스트"
            JSON_TEST -> "JSON 응답 테스트"
            else -> "알 수 없는 테스트"
        }
    }

    fun toCapability(testType: String): String {
        return when (testType) {
            TEXT_TEST -> ModelCapability.TEXT_ONLY
            SUMMARY_TEST -> ModelCapability.TEXT_ONLY
            CODING_TEST -> ModelCapability.CODING
            REASONING_TEST -> ModelCapability.REASONING
            JSON_TEST -> ModelCapability.JSON_OUTPUT
            else -> ModelCapability.UNKNOWN
        }
    }
}

object ModelCapabilityTestStatus {
    const val NOT_TESTED = "NOT_TESTED"
    const val PASSED = "PASSED"
    const val FAILED = "FAILED"
    const val UNSUPPORTED = "UNSUPPORTED"
    const val TIMEOUT = "TIMEOUT"
    const val RATE_LIMITED = "RATE_LIMITED"

    fun toKorean(status: String): String {
        return when (status) {
            PASSED -> "통과"
            FAILED -> "실패"
            UNSUPPORTED -> "지원 안 됨"
            TIMEOUT -> "타임아웃"
            RATE_LIMITED -> "제한됨"
            else -> "미테스트"
        }
    }
}