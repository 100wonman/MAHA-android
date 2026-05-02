package com.maha.app

data class LocalServerValidationResult(
    val success: Boolean,
    val missingBaseUrl: Boolean,
    val missingModelName: Boolean,
    val message: String
)

class LocalServerProviderAdapter {
    fun validateConfig(
        providerProfile: ProviderProfile,
        modelProfile: ConversationModelProfile?
    ): LocalServerValidationResult {
        val missingBaseUrl = providerProfile.baseUrl.isBlank()
        val missingModelName = modelProfile?.rawModelName.isNullOrBlank()
        val success = !missingBaseUrl && !missingModelName
        val message = when {
            success -> "LOCAL 설정 확인 가능"
            missingBaseUrl && missingModelName -> "Base URL과 모델명이 필요합니다."
            missingBaseUrl -> "Base URL이 필요합니다."
            missingModelName -> "모델명이 필요합니다."
            else -> "LOCAL 설정 확인 실패"
        }

        return LocalServerValidationResult(
            success = success,
            missingBaseUrl = missingBaseUrl,
            missingModelName = missingModelName,
            message = message
        )
    }
}
