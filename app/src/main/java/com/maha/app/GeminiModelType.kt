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

    fun getCatalog(): List<ModelCatalogItem> {
        return listOf(
            ModelCatalogItem(
                modelName = FLASH,
                displayName = "Gemini 2.5 Flash",
                description = "기본 품질 모델입니다. 계획, 조사, 분석 Worker에 적합합니다.",
                stabilityStatus = "Stable",
                recommendedWorker = "Planner / Researcher",
                estimatedDailyLimit = 250
            ),
            ModelCatalogItem(
                modelName = FLASH_LITE,
                displayName = "Gemini 2.5 Flash Lite",
                description = "빠른 경량 모델입니다. Writer, 요약, 반복 테스트에 적합합니다.",
                stabilityStatus = "Stable",
                recommendedWorker = "Writer / Summary",
                estimatedDailyLimit = 250
            ),
            ModelCatalogItem(
                modelName = GEMMA_4_31B_IT,
                displayName = "Gemma 4 31B IT",
                description = "Gemma 4 고품질 테스트 모델입니다. 복잡한 계획/분석 테스트에 사용합니다.",
                stabilityStatus = "Test",
                recommendedWorker = "Planner / Analyzer",
                estimatedDailyLimit = 100
            ),
            ModelCatalogItem(
                modelName = GEMMA_4_26B_A4B_IT,
                displayName = "Gemma 4 26B A4B IT",
                description = "Gemma 4 효율형 테스트 모델입니다. Writer/요약 Worker 테스트에 사용합니다.",
                stabilityStatus = "Test",
                recommendedWorker = "Writer / Researcher",
                estimatedDailyLimit = 100
            )
        )
    }
}