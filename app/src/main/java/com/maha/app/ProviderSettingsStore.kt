package com.maha.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ProviderSettingsStore(
    context: Context
) {
    private val appContext = context.applicationContext
    private val settingsDir: File
        get() = File(File(appContext.getExternalFilesDir(null), "MAHA"), "settings")

    private val providerProfilesFile: File
        get() = File(settingsDir, "provider_profiles.json")

    private val modelProfilesFile: File
        get() = File(settingsDir, "model_profiles.json")

    fun ensureSettingsFiles() {
        if (!settingsDir.exists()) {
            settingsDir.mkdirs()
        }

        if (!providerProfilesFile.exists()) {
            createEmptyProviderFile()
        }

        if (!modelProfilesFile.exists()) {
            createEmptyModelFile()
        }
    }

    fun createEmptyProviderFile() {
        ensureSettingsDirOnly()
        providerProfilesFile.writeText(
            JSONObject()
                .put("version", 1)
                .put("providers", JSONArray())
                .toString(2)
        )
    }

    fun createEmptyModelFile() {
        ensureSettingsDirOnly()
        modelProfilesFile.writeText(
            JSONObject()
                .put("version", 1)
                .put("models", JSONArray())
                .toString(2)
        )
    }

    fun loadProviderProfiles(): List<ProviderProfile> {
        ensureSettingsFiles()

        return runCatching {
            val root = JSONObject(providerProfilesFile.readText())
            val providers = root.optJSONArray("providers") ?: JSONArray()

            buildList {
                for (index in 0 until providers.length()) {
                    val item = providers.optJSONObject(index) ?: continue
                    add(item.toProviderProfile())
                }
            }
        }.getOrElse {
            emptyList()
        }
    }

    fun saveProviderProfiles(providers: List<ProviderProfile>) {
        ensureSettingsFiles()

        val array = JSONArray()
        providers.forEach { profile ->
            array.put(profile.toJsonObject())
        }

        providerProfilesFile.writeText(
            JSONObject()
                .put("version", 1)
                .put("providers", array)
                .toString(2)
        )
    }

    fun addProviderProfile(profile: ProviderProfile) {
        val current = loadProviderProfiles()
        val updated = current
            .filterNot { it.providerId == profile.providerId } + profile

        saveProviderProfiles(updated)
    }

    fun updateProviderProfile(profile: ProviderProfile) {
        val current = loadProviderProfiles()
        val updated = if (current.any { it.providerId == profile.providerId }) {
            current.map { existing ->
                if (existing.providerId == profile.providerId) profile else existing
            }
        } else {
            current + profile
        }

        saveProviderProfiles(updated)
    }

    fun deleteProviderProfile(providerId: String) {
        val updated = loadProviderProfiles().filterNot { it.providerId == providerId }
        saveProviderProfiles(updated)
    }

    fun loadModelProfiles(): List<ConversationModelProfile> {
        ensureSettingsFiles()

        return runCatching {
            val root = JSONObject(modelProfilesFile.readText())
            val models = root.optJSONArray("models") ?: JSONArray()

            buildList {
                for (index in 0 until models.length()) {
                    val item = models.optJSONObject(index) ?: continue
                    add(item.toConversationModelProfile())
                }
            }
        }.getOrElse {
            emptyList()
        }
    }

    fun saveModelProfiles(models: List<ConversationModelProfile>) {
        ensureSettingsFiles()

        val array = JSONArray()
        models.forEach { profile ->
            array.put(profile.toJsonObject())
        }

        modelProfilesFile.writeText(
            JSONObject()
                .put("version", 1)
                .put("models", array)
                .toString(2)
        )
    }

    fun addModelProfile(profile: ConversationModelProfile) {
        val current = loadModelProfiles()
        val updated = current
            .filterNot { it.modelId == profile.modelId } + profile

        saveModelProfiles(updated)
    }

    fun updateModelProfile(profile: ConversationModelProfile) {
        val current = loadModelProfiles()
        val updated = if (current.any { it.modelId == profile.modelId }) {
            current.map { existing ->
                if (existing.modelId == profile.modelId) profile else existing
            }
        } else {
            current + profile
        }

        saveModelProfiles(updated)
    }

    fun deleteModelProfile(modelId: String) {
        val updated = loadModelProfiles().filterNot { it.modelId == modelId }
        saveModelProfiles(updated)
    }

    private fun ensureSettingsDirOnly() {
        if (!settingsDir.exists()) {
            settingsDir.mkdirs()
        }
    }

    private fun JSONObject.toProviderProfile(): ProviderProfile {
        return ProviderProfile(
            providerId = optString("providerId"),
            displayName = optString("displayName"),
            providerType = parseProviderType(optString("providerType")),
            baseUrl = optString("baseUrl"),
            apiKeyAlias = optNullableString("apiKeyAlias"),
            modelListEndpoint = optNullableString("modelListEndpoint"),
            isEnabled = optBoolean("isEnabled", true),
            createdAt = optLong("createdAt", System.currentTimeMillis()),
            updatedAt = optLong("updatedAt", System.currentTimeMillis())
        )
    }

    private fun ProviderProfile.toJsonObject(): JSONObject {
        return JSONObject()
            .put("providerId", providerId)
            .put("displayName", displayName)
            .put("providerType", providerType.name)
            .put("baseUrl", baseUrl)
            .put("apiKeyAlias", apiKeyAlias)
            .put("modelListEndpoint", modelListEndpoint)
            .put("isEnabled", isEnabled)
            .put("createdAt", createdAt)
            .put("updatedAt", updatedAt)
    }

    private fun JSONObject.toConversationModelProfile(): ConversationModelProfile {
        return ConversationModelProfile(
            modelId = optString("modelId"),
            providerId = optString("providerId"),
            displayName = optString("displayName"),
            rawModelName = optString("rawModelName"),
            contextWindow = optNullableInt("contextWindow"),
            inputModalities = optStringList("inputModalities"),
            outputModalities = optStringList("outputModalities"),
            capabilities = optJSONObject("capabilities")?.toConversationModelCapability() ?: ConversationModelCapability(),
            isFavorite = optBoolean("isFavorite", false),
            isDefaultForConversation = optBoolean("isDefaultForConversation", false),
            lastUsedAt = optNullableLong("lastUsedAt"),
            enabled = optBoolean("enabled", true)
        )
    }

    private fun ConversationModelProfile.toJsonObject(): JSONObject {
        return JSONObject()
            .put("modelId", modelId)
            .put("providerId", providerId)
            .put("displayName", displayName)
            .put("rawModelName", rawModelName)
            .put("contextWindow", contextWindow)
            .put("inputModalities", inputModalities.toJsonArray())
            .put("outputModalities", outputModalities.toJsonArray())
            .put("capabilities", capabilities.toJsonObject())
            .put("isFavorite", isFavorite)
            .put("isDefaultForConversation", isDefaultForConversation)
            .put("lastUsedAt", lastUsedAt)
            .put("enabled", enabled)
    }

    private fun JSONObject.toConversationModelCapability(): ConversationModelCapability {
        return ConversationModelCapability(
            text = optBoolean("text", true),
            code = optBoolean("code", false),
            vision = optBoolean("vision", false),
            audio = optBoolean("audio", false),
            video = optBoolean("video", false),
            toolCalling = optBoolean("toolCalling", false),
            functionCalling = optBoolean("functionCalling", false),
            webSearch = optBoolean("webSearch", false),
            jsonMode = optBoolean("jsonMode", false),
            imageGeneration = optBoolean("imageGeneration", false),
            structuredOutput = optBoolean("structuredOutput", false)
        )
    }

    private fun ConversationModelCapability.toJsonObject(): JSONObject {
        return JSONObject()
            .put("text", text)
            .put("code", code)
            .put("vision", vision)
            .put("audio", audio)
            .put("video", video)
            .put("toolCalling", toolCalling)
            .put("functionCalling", functionCalling)
            .put("webSearch", webSearch)
            .put("jsonMode", jsonMode)
            .put("imageGeneration", imageGeneration)
            .put("structuredOutput", structuredOutput)
    }

    private fun List<String>.toJsonArray(): JSONArray {
        val array = JSONArray()
        forEach { value -> array.put(value) }
        return array
    }

    private fun JSONObject.optStringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index)
                if (value.isNotBlank()) {
                    add(value)
                }
            }
        }
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key)
    }

    private fun JSONObject.optNullableLong(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return optLong(key)
    }

    private fun parseProviderType(value: String): ProviderType {
        return runCatching { ProviderType.valueOf(value) }
            .getOrDefault(ProviderType.CUSTOM)
    }
}
