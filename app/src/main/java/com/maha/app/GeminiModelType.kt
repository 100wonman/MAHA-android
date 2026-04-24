// GeminiModelType.kt

package com.maha.app

object GeminiModelType {
    const val DEFAULT = "gemini-2.5-flash"
    const val FLASH = "gemini-2.5-flash"
    const val FLASH_LITE = "gemini-2.5-flash-lite"
    const val GEMMA_4_31B_IT = "gemma-4-31b-it"
    const val GEMMA_4_26B_A4B_IT = "gemma-4-26b-a4b-it"

    fun sanitize(modelName: String): String {
        return when (modelName.trim()) {
            FLASH -> FLASH
            FLASH_LITE -> FLASH_LITE
            GEMMA_4_31B_IT -> GEMMA_4_31B_IT
            GEMMA_4_26B_A4B_IT -> GEMMA_4_26B_A4B_IT
            else -> DEFAULT
        }
    }

    fun recommendedForAgent(agentName: String): String {
        return when (agentName.trim().lowercase()) {
            "writer" -> FLASH_LITE
            else -> FLASH
        }
    }
}