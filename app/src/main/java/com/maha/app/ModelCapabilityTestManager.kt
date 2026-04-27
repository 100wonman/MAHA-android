// ModelCapabilityTestManager.kt

package com.maha.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ModelCapabilityTestManager {

    private const val PREFS_NAME = "maha_model_capability_test_prefs"
    private const val KEY_RECORDS = "model_capability_test_records_json_v1"
    private const val KEY_RECORDS_BACKUP = "model_capability_test_records_json_v1_backup"

    fun saveRecord(
        context: Context,
        record: ModelCapabilityTestRecord
    ) {
        val safeRecord = sanitizeRecord(record)
        val currentRecords = getAllRecords(context).toMutableList()

        val index = currentRecords.indexOfFirst {
            it.providerName == safeRecord.providerName &&
                    it.modelName == safeRecord.modelName &&
                    it.testType == safeRecord.testType
        }

        if (index >= 0) {
            currentRecords[index] = safeRecord
        } else {
            currentRecords.add(safeRecord)
        }

        val jsonArray = JSONArray()
        currentRecords.forEach { item ->
            jsonArray.put(recordToJson(item))
        }

        saveJsonWithBackup(
            context = context,
            value = jsonArray.toString()
        )

        if (safeRecord.status == ModelCapabilityTestStatus.PASSED) {
            val capability = ModelCapabilityTestType.toCapability(safeRecord.testType)

            if (capability != ModelCapability.UNKNOWN) {
                ModelMetadataManager.addTestedCapability(
                    context = context,
                    providerName = safeRecord.providerName,
                    modelName = safeRecord.modelName,
                    capability = capability
                )
            }
        }
    }

    fun getRecord(
        context: Context,
        providerName: String,
        modelName: String,
        testType: String
    ): ModelCapabilityTestRecord {
        val safeProviderName = providerName.trim().ifBlank { ModelProviderType.DUMMY }
        val safeModelName = modelName.trim().removePrefix("models/")
        val safeTestType = testType.trim()

        return getAllRecords(context).firstOrNull {
            it.providerName == safeProviderName &&
                    it.modelName == safeModelName &&
                    it.testType == safeTestType
        } ?: ModelCapabilityTestRecord(
            providerName = safeProviderName,
            modelName = safeModelName,
            testType = safeTestType,
            status = ModelCapabilityTestStatus.NOT_TESTED,
            testedAt = "",
            latencyMs = 0L,
            message = "아직 테스트하지 않았습니다.",
            sampleOutput = ""
        )
    }

    fun getRecordsForModel(
        context: Context,
        providerName: String,
        modelName: String
    ): List<ModelCapabilityTestRecord> {
        val safeProviderName = providerName.trim().ifBlank { ModelProviderType.DUMMY }
        val safeModelName = modelName.trim().removePrefix("models/")

        return ModelCapabilityTestType.allBasicTests().map { testType ->
            getRecord(
                context = context,
                providerName = safeProviderName,
                modelName = safeModelName,
                testType = testType
            )
        }
    }

    fun getAllRecords(context: Context): List<ModelCapabilityTestRecord> {
        return loadJsonArrayWithBackup(context) { jsonArray ->
            val result = mutableListOf<ModelCapabilityTestRecord>()

            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(index) ?: continue
                result.add(jsonToRecord(item))
            }

            result
                .map { sanitizeRecord(it) }
                .distinctBy { "${it.providerName}:${it.modelName}:${it.testType}" }
        }
    }

    private fun sanitizeRecord(record: ModelCapabilityTestRecord): ModelCapabilityTestRecord {
        return record.copy(
            providerName = record.providerName.trim().ifBlank { ModelProviderType.DUMMY },
            modelName = record.modelName.trim().removePrefix("models/"),
            testType = record.testType.trim(),
            status = sanitizeStatus(record.status),
            testedAt = record.testedAt.trim(),
            message = record.message.trim(),
            sampleOutput = record.sampleOutput.trim().take(1200)
        )
    }

    private fun sanitizeStatus(status: String): String {
        return when (status) {
            ModelCapabilityTestStatus.PASSED -> ModelCapabilityTestStatus.PASSED
            ModelCapabilityTestStatus.FAILED -> ModelCapabilityTestStatus.FAILED
            ModelCapabilityTestStatus.UNSUPPORTED -> ModelCapabilityTestStatus.UNSUPPORTED
            ModelCapabilityTestStatus.TIMEOUT -> ModelCapabilityTestStatus.TIMEOUT
            ModelCapabilityTestStatus.RATE_LIMITED -> ModelCapabilityTestStatus.RATE_LIMITED
            else -> ModelCapabilityTestStatus.NOT_TESTED
        }
    }

    private fun recordToJson(record: ModelCapabilityTestRecord): JSONObject {
        val safeRecord = sanitizeRecord(record)

        return JSONObject().apply {
            put("providerName", safeRecord.providerName)
            put("modelName", safeRecord.modelName)
            put("testType", safeRecord.testType)
            put("status", safeRecord.status)
            put("testedAt", safeRecord.testedAt)
            put("latencyMs", safeRecord.latencyMs)
            put("message", safeRecord.message)
            put("sampleOutput", safeRecord.sampleOutput)
        }
    }

    private fun jsonToRecord(json: JSONObject): ModelCapabilityTestRecord {
        return ModelCapabilityTestRecord(
            providerName = json.optString("providerName", ModelProviderType.DUMMY),
            modelName = json.optString("modelName", ""),
            testType = json.optString("testType", ""),
            status = json.optString("status", ModelCapabilityTestStatus.NOT_TESTED),
            testedAt = json.optString("testedAt", ""),
            latencyMs = json.optLong("latencyMs", 0L),
            message = json.optString("message", ""),
            sampleOutput = json.optString("sampleOutput", "")
        )
    }

    private fun saveJsonWithBackup(
        context: Context,
        value: String
    ) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECORDS_BACKUP, value)
            .putString(KEY_RECORDS, value)
            .apply()
    }

    private fun <T> loadJsonArrayWithBackup(
        context: Context,
        parser: (JSONArray) -> List<T>
    ): List<T> {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val primary = runCatching {
            val jsonString = prefs.getString(KEY_RECORDS, "[]") ?: "[]"
            parser(JSONArray(jsonString))
        }.getOrNull()

        if (primary != null) return primary

        val backup = runCatching {
            val jsonString = prefs.getString(KEY_RECORDS_BACKUP, "[]") ?: "[]"
            parser(JSONArray(jsonString))
        }.getOrNull()

        return backup ?: emptyList()
    }
}