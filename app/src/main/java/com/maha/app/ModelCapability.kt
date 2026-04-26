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