// GeminiModelType.kt

package com.maha.app

object GeminiModelType {
    const val DEFAULT = "gemini-2.5-flash"
    const val FLASH = "gemini-2.5-flash"
    const val FLASH_LITE = "gemini-2.5-flash-lite"

    fun sanitize(modelName: String): String {
        return when (modelName.trim()) {
            FLASH -> FLASH
            FLASH_LITE -> FLASH_LITE
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