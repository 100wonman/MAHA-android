package com.maha.app

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SettingsBackupResult(
    val success: Boolean,
    val backupFolderName: String?,
    val providerCount: Int,
    val modelCount: Int,
    val message: String,
    val errorType: String? = null
)

data class SettingsRestoreResult(
    val success: Boolean,
    val restoredProviders: Int,
    val skippedProviders: Int,
    val restoredModels: Int,
    val skippedModels: Int,
    val message: String,
    val errorType: String? = null
)

data class SettingsBackupEntry(
    val folderName: String,
    val createdAt: Long,
    val providerCount: Int,
    val modelCount: Int,
    val includesApiKeys: Boolean
)

class SettingsBackupManager(
    context: Context
) {
    private val appContext = context.applicationContext
    private val providerSettingsStore = ProviderSettingsStore(appContext)

    private val settingsDir: File
        get() = File(File(appContext.getExternalFilesDir(null), "MAHA"), "settings")

    private val providerProfilesFile: File
        get() = File(settingsDir, "provider_profiles.json")

    private val modelProfilesFile: File
        get() = File(settingsDir, "model_profiles.json")

    fun backupModelApiSettingsToSaf(): SettingsBackupResult {
        return runCatching {
            providerSettingsStore.ensureSettingsFiles()

            val settingsRoot = getSettingsBackupRootDocument()
                ?: return SettingsBackupResult(
                    success = false,
                    backupFolderName = null,
                    providerCount = 0,
                    modelCount = 0,
                    message = "SAF 저장소가 연결되지 않았습니다.",
                    errorType = "SAF_NOT_CONNECTED"
                )

            val providers = providerSettingsStore.loadProviderProfiles()
            val models = providerSettingsStore.loadModelProfiles()
            val folderName = "settings_backup_${buildTimestamp()}"
            val backupFolder = settingsRoot.createDirectory(folderName)
                ?: return SettingsBackupResult(
                    success = false,
                    backupFolderName = folderName,
                    providerCount = providers.size,
                    modelCount = models.size,
                    message = "설정 백업 폴더를 만들지 못했습니다.",
                    errorType = "WRITE_FAILED"
                )

            writeTextFile(
                parent = backupFolder,
                fileName = "provider_profiles.json",
                text = providerProfilesFile.readText()
            )

            writeTextFile(
                parent = backupFolder,
                fileName = "model_profiles.json",
                text = modelProfilesFile.readText()
            )

            val manifest = JSONObject()
                .put("backupVersion", 1)
                .put("createdAt", System.currentTimeMillis())
                .put("includesProviderProfiles", true)
                .put("includesModelProfiles", true)
                .put("includesApiKeys", false)
                .put("providerCount", providers.size)
                .put("modelCount", models.size)
                .put("note", "API Key is not included for security.")

            writeTextFile(
                parent = backupFolder,
                fileName = "backup_manifest.json",
                text = manifest.toString(2)
            )

            SettingsBackupResult(
                success = true,
                backupFolderName = folderName,
                providerCount = providers.size,
                modelCount = models.size,
                message = "설정 백업 완료: Provider ${providers.size}개 / Model ${models.size}개"
            )
        }.getOrElse { exception ->
            SettingsBackupResult(
                success = false,
                backupFolderName = null,
                providerCount = 0,
                modelCount = 0,
                message = "설정 백업 실패: ${exception.message ?: "알 수 없는 오류"}",
                errorType = "UNKNOWN"
            )
        }
    }

    fun listSettingsBackupsFromSaf(): List<SettingsBackupEntry> {
        val settingsRoot = getSettingsBackupRootDocument() ?: return emptyList()

        return settingsRoot.listFiles()
            .filter { it.isDirectory }
            .mapNotNull { folder ->
                val manifestFile = folder.findFile("backup_manifest.json") ?: return@mapNotNull null
                val manifestText = readTextFile(manifestFile) ?: return@mapNotNull null
                val manifest = runCatching { JSONObject(manifestText) }.getOrNull() ?: return@mapNotNull null
                val version = manifest.optInt("backupVersion", -1)
                if (version != 1) return@mapNotNull null

                SettingsBackupEntry(
                    folderName = folder.name ?: return@mapNotNull null,
                    createdAt = manifest.optLong("createdAt", 0L),
                    providerCount = manifest.optInt("providerCount", 0),
                    modelCount = manifest.optInt("modelCount", 0),
                    includesApiKeys = manifest.optBoolean("includesApiKeys", false)
                )
            }
            .sortedByDescending { it.createdAt }
    }

    fun restoreSettingsBackupFromSaf(folderName: String): SettingsRestoreResult {
        return runCatching {
            val settingsRoot = getSettingsBackupRootDocument()
                ?: return SettingsRestoreResult(
                    success = false,
                    restoredProviders = 0,
                    skippedProviders = 0,
                    restoredModels = 0,
                    skippedModels = 0,
                    message = "SAF 저장소가 연결되지 않았습니다.",
                    errorType = "SAF_NOT_CONNECTED"
                )

            val backupFolder = settingsRoot.findFile(folderName)
                ?: return SettingsRestoreResult(
                    success = false,
                    restoredProviders = 0,
                    skippedProviders = 0,
                    restoredModels = 0,
                    skippedModels = 0,
                    message = "선택한 설정 백업을 찾을 수 없습니다.",
                    errorType = "BACKUP_NOT_FOUND"
                )

            val manifestFile = backupFolder.findFile("backup_manifest.json")
                ?: return invalidBackupResult("backup_manifest.json이 없습니다.")

            val manifest = readTextFile(manifestFile)
                ?.let { JSONObject(it) }
                ?: return invalidBackupResult("backup_manifest.json을 읽을 수 없습니다.")

            if (manifest.optInt("backupVersion", -1) != 1) {
                return invalidBackupResult("지원하지 않는 설정 백업 버전입니다.")
            }

            val providerJsonText = backupFolder.findFile("provider_profiles.json")
                ?.let { readTextFile(it) }
                ?: return invalidBackupResult("provider_profiles.json이 없습니다.")

            val modelJsonText = backupFolder.findFile("model_profiles.json")
                ?.let { readTextFile(it) }
                ?: return invalidBackupResult("model_profiles.json이 없습니다.")

            val backupProviders = parseProviderProfiles(providerJsonText)
            val backupModels = parseModelProfiles(modelJsonText)

            val currentProviders = providerSettingsStore.loadProviderProfiles()
            val currentModels = providerSettingsStore.loadModelProfiles()
            val currentProviderIds = currentProviders.map { it.providerId }.toSet()
            val currentModelIds = currentModels.map { it.modelId }.toSet()

            val providersToRestore = backupProviders.filterNot { it.providerId in currentProviderIds }
            val modelsToRestore = backupModels.filterNot { it.modelId in currentModelIds }

            if (providersToRestore.isNotEmpty()) {
                providerSettingsStore.saveProviderProfiles(currentProviders + providersToRestore)
            }

            if (modelsToRestore.isNotEmpty()) {
                providerSettingsStore.saveModelProfiles(currentModels + modelsToRestore)
            }

            val skippedProviders = backupProviders.size - providersToRestore.size
            val skippedModels = backupModels.size - modelsToRestore.size

            SettingsRestoreResult(
                success = true,
                restoredProviders = providersToRestore.size,
                skippedProviders = skippedProviders,
                restoredModels = modelsToRestore.size,
                skippedModels = skippedModels,
                message = "설정 복원 완료: Provider 복원 ${providersToRestore.size}개 / 건너뜀 ${skippedProviders}개, Model 복원 ${modelsToRestore.size}개 / 건너뜀 ${skippedModels}개"
            )
        }.getOrElse { exception ->
            SettingsRestoreResult(
                success = false,
                restoredProviders = 0,
                skippedProviders = 0,
                restoredModels = 0,
                skippedModels = 0,
                message = "설정 복원 실패: ${exception.message ?: "알 수 없는 오류"}",
                errorType = "UNKNOWN"
            )
        }
    }

    private fun invalidBackupResult(message: String): SettingsRestoreResult {
        return SettingsRestoreResult(
            success = false,
            restoredProviders = 0,
            skippedProviders = 0,
            restoredModels = 0,
            skippedModels = 0,
            message = "잘못된 설정 백업입니다: $message",
            errorType = "INVALID_BACKUP"
        )
    }

    private fun getSettingsBackupRootDocument(): DocumentFile? {
        val root = getPersistedWritableRootDocument() ?: return null
        val mahaRoot = if (root.name == "MAHA") {
            root
        } else {
            root.findFile("MAHA") ?: root.createDirectory("MAHA") ?: return null
        }

        val backupsRoot = mahaRoot.findFile("backups") ?: mahaRoot.createDirectory("backups") ?: return null
        return backupsRoot.findFile("settings") ?: backupsRoot.createDirectory("settings")
    }

    private fun getPersistedWritableRootDocument(): DocumentFile? {
        val permission = appContext.contentResolver.persistedUriPermissions
            .firstOrNull { it.isWritePermission }
            ?: return null

        return DocumentFile.fromTreeUri(appContext, permission.uri)
    }

    private fun writeTextFile(
        parent: DocumentFile,
        fileName: String,
        text: String
    ) {
        parent.findFile(fileName)?.delete()
        val file = parent.createFile("application/json", fileName)
            ?: throw IllegalStateException("파일 생성 실패: $fileName")
        appContext.contentResolver.openOutputStream(file.uri, "wt")?.use { outputStream ->
            outputStream.write(text.toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("파일 쓰기 실패: $fileName")
    }

    private fun readTextFile(file: DocumentFile): String? {
        return appContext.contentResolver.openInputStream(file.uri)?.use { inputStream ->
            inputStream.bufferedReader(Charsets.UTF_8).readText()
        }
    }

    private fun buildTimestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }

    private fun parseProviderProfiles(jsonText: String): List<ProviderProfile> {
        val root = JSONObject(jsonText)
        val providers = root.optJSONArray("providers") ?: JSONArray()
        return buildList {
            for (index in 0 until providers.length()) {
                val item = providers.optJSONObject(index) ?: continue
                val providerId = item.optString("providerId")
                if (providerId.isBlank()) continue
                add(
                    ProviderProfile(
                        providerId = providerId,
                        displayName = item.optString("displayName"),
                        providerType = parseProviderType(item.optString("providerType")),
                        baseUrl = item.optString("baseUrl"),
                        apiKeyAlias = item.optNullableString("apiKeyAlias"),
                        modelListEndpoint = item.optNullableString("modelListEndpoint"),
                        isEnabled = item.optBoolean("isEnabled", true),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    private fun parseModelProfiles(jsonText: String): List<ConversationModelProfile> {
        val root = JSONObject(jsonText)
        val models = root.optJSONArray("models") ?: JSONArray()
        return buildList {
            for (index in 0 until models.length()) {
                val item = models.optJSONObject(index) ?: continue
                val modelId = item.optString("modelId")
                if (modelId.isBlank()) continue

                val legacyCapabilities = item.optJSONObject("capabilities")?.toConversationModelCapability()
                    ?: ConversationModelCapability()
                val parsedCapabilitiesV2 = item.optJSONObject("capabilitiesV2")?.toModelCapabilityV2()
                    ?: legacyCapabilities.toModelCapabilityV2()

                add(
                    ConversationModelProfile(
                        modelId = modelId,
                        providerId = item.optString("providerId"),
                        displayName = item.optString("displayName"),
                        rawModelName = item.optString("rawModelName"),
                        contextWindow = item.optNullableInt("contextWindow"),
                        inputModalities = item.optStringList("inputModalities"),
                        outputModalities = item.optStringList("outputModalities"),
                        capabilities = legacyCapabilities,
                        isFavorite = item.optBoolean("isFavorite", false),
                        isDefaultForConversation = item.optBoolean("isDefaultForConversation", false),
                        lastUsedAt = item.optNullableLong("lastUsedAt"),
                        enabled = item.optBoolean("enabled", true),
                        capabilitiesV2 = parsedCapabilitiesV2,
                        capabilitySource = item.optString("capabilitySource", "USER").ifBlank { "USER" },
                        supportedGenerationMethods = item.optStringList("supportedGenerationMethods"),
                        inputTokenLimit = item.optNullableInt("inputTokenLimit"),
                        outputTokenLimit = item.optNullableInt("outputTokenLimit"),
                        metadataRawSummary = item.optNullableString("metadataRawSummary"),
                        lastMetadataFetchedAt = item.optNullableLong("lastMetadataFetchedAt")
                    )
                )
            }
        }
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

    private fun JSONObject.toModelCapabilityV2(): ModelCapabilityV2 {
        return ModelCapabilityV2(
            input = optJSONObject("input")?.toModelInputCapability() ?: ModelInputCapability(),
            output = optJSONObject("output")?.toModelOutputCapability() ?: ModelOutputCapability(),
            tools = optJSONObject("tools")?.toModelToolCapability() ?: ModelToolCapability(),
            reasoning = optJSONObject("reasoning")?.toModelReasoningCapability() ?: ModelReasoningCapability()
        )
    }

    private fun JSONObject.toModelInputCapability(): ModelInputCapability {
        return ModelInputCapability(
            text = optCapabilityStatus("text", CapabilityStatus.SUPPORTED),
            image = optCapabilityStatus("image", CapabilityStatus.UNKNOWN),
            audio = optCapabilityStatus("audio", CapabilityStatus.UNKNOWN),
            video = optCapabilityStatus("video", CapabilityStatus.UNKNOWN),
            file = optCapabilityStatus("file", CapabilityStatus.UNKNOWN)
        )
    }

    private fun JSONObject.toModelOutputCapability(): ModelOutputCapability {
        return ModelOutputCapability(
            text = optCapabilityStatus("text", CapabilityStatus.SUPPORTED),
            code = optCapabilityStatus("code", CapabilityStatus.UNKNOWN),
            json = optCapabilityStatus("json", CapabilityStatus.UNKNOWN),
            image = optCapabilityStatus("image", CapabilityStatus.UNKNOWN),
            audio = optCapabilityStatus("audio", CapabilityStatus.UNKNOWN),
            video = optCapabilityStatus("video", CapabilityStatus.UNKNOWN)
        )
    }

    private fun JSONObject.toModelToolCapability(): ModelToolCapability {
        return ModelToolCapability(
            functionCalling = optCapabilityStatus("functionCalling", CapabilityStatus.UNKNOWN),
            webSearch = optCapabilityStatus("webSearch", CapabilityStatus.UNKNOWN),
            codeExecution = optCapabilityStatus("codeExecution", CapabilityStatus.UNKNOWN),
            structuredOutput = optCapabilityStatus("structuredOutput", CapabilityStatus.UNKNOWN)
        )
    }

    private fun JSONObject.toModelReasoningCapability(): ModelReasoningCapability {
        return ModelReasoningCapability(
            thinking = optCapabilityStatus("thinking", CapabilityStatus.UNKNOWN),
            thinkingSummary = optCapabilityStatus("thinkingSummary", CapabilityStatus.UNKNOWN),
            chainOfThoughtRawAllowed = optCapabilityStatus("chainOfThoughtRawAllowed", CapabilityStatus.UNSUPPORTED)
        )
    }

    private fun JSONObject.optCapabilityStatus(
        key: String,
        defaultValue: CapabilityStatus
    ): CapabilityStatus {
        val rawValue = optString(key).takeIf { it.isNotBlank() } ?: return defaultValue
        return runCatching { CapabilityStatus.valueOf(rawValue) }.getOrDefault(defaultValue)
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
