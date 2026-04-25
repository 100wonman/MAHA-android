// GeminiModelType.kt

package com.maha.app

object GeminiModelType {
    const val DEFAULT = "gemini-2.5-flash"

    const val FLASH = "gemini-2.5-flash"
    const val FLASH_LITE = "gemini-2.5-flash-lite"
    const val GEMINI_3_1_FLASH_LITE = "gemini-flash-lite-latest"
    const val GEMMA_4_31B_IT = "gemma-4-31b-it"
    const val GEMMA_4_26B_A4B_IT = "gemma-4-26b-a4b-it"

    fun sanitize(modelName: String): String {
        val trimmedModelName = modelName.trim().removePrefix("models/")

        if (trimmedModelName.isBlank()) {
            return DEFAULT
        }

        val manualModelNames = getCatalog().map { it.modelName }.toSet()
        if (trimmedModelName in manualModelNames) {
            return trimmedModelName
        }

        val discoveredModel = ModelCatalogManager
            .getDiscoveredModels()
            .firstOrNull { model ->
                model.modelName == trimmedModelName &&
                        model.isGenerateContentSupported
            }

        if (discoveredModel != null) {
            return discoveredModel.modelName
        }

        return trimmedModelName
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
                estimatedDailyLimit = 250,
                providerName = ModelProviderType.GOOGLE
            ),
            ModelCatalogItem(
                modelName = FLASH_LITE,
                displayName = "Gemini 2.5 Flash Lite",
                description = "빠른 경량 모델입니다. Writer, 요약, 반복 테스트에 적합합니다.",
                stabilityStatus = "Stable",
                recommendedWorker = "Writer / Summary",
                estimatedDailyLimit = 250,
                providerName = ModelProviderType.GOOGLE
            ),
            ModelCatalogItem(
                modelName = GEMINI_3_1_FLASH_LITE,
                displayName = "Gemini 3.1 Flash Lite",
                description = "Google AI Dashboard에서 Gemini 3.1 Flash Lite로 집계되는 최신 Flash Lite 모델입니다.",
                stabilityStatus = "Fallback",
                recommendedWorker = "All Workers",
                estimatedDailyLimit = 250,
                providerName = ModelProviderType.GOOGLE
            ),
            ModelCatalogItem(
                modelName = GEMMA_4_31B_IT,
                displayName = "Gemma 4 31B IT",
                description = "Gemma 4 고품질 테스트 모델입니다. 복잡한 계획/분석 테스트에 사용합니다.",
                stabilityStatus = "Test",
                recommendedWorker = "Planner / Analyzer",
                estimatedDailyLimit = 100,
                providerName = ModelProviderType.GOOGLE
            ),
            ModelCatalogItem(
                modelName = GEMMA_4_26B_A4B_IT,
                displayName = "Gemma 4 26B A4B IT",
                description = "Gemma 4 효율형 테스트 모델입니다. Writer/요약 Worker 테스트에 사용합니다.",
                stabilityStatus = "Test",
                recommendedWorker = "Writer / Researcher",
                estimatedDailyLimit = 100,
                providerName = ModelProviderType.GOOGLE
            )
        )
    }
}