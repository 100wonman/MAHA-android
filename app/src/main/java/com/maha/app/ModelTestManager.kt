// ModelTestManager.kt

package com.maha.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ModelTestManager {

    private const val PREFS_NAME = "maha_model_test_prefs"
    private const val KEY_MODEL_TEST_RECORDS = "model_test_records_json_v1"

    fun saveRecord(context: Context, record: ModelTestRecord) {
        val currentRecords = getRecords(context).toMutableList()
        val index = currentRecords.indexOfFirst {
            it.providerName == record.providerName && it.modelName == record.modelName
        }

        if (index >= 0) {
            currentRecords[index] = record
        } else {
            currentRecords.add(record)
        }

        val jsonArray = JSONArray()
        currentRecords.forEach { item ->
            jsonArray.put(recordToJson(item))
        }

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODEL_TEST_RECORDS, jsonArray.toString())
            .apply()
    }

    fun getRecord(
        context: Context,
        providerName: String,
        modelName: String
    ): ModelTestRecord {
        return getRecords(context).firstOrNull {
            it.providerName == providerName && it.modelName == modelName
        } ?: ModelTestRecord(
            providerName = providerName,
            modelName = modelName,
            status = NvidiaModelTestStatus.UNTESTED,
            lastTestedAt = "",
            httpStatusCode = 0,
            message = "아직 테스트하지 않았습니다.",
            latencyMs = 0L
        )
    }

    private fun getRecords(context: Context): List<ModelTestRecord> {
        val jsonString = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODEL_TEST_RECORDS, "[]")
            ?: "[]"

        return runCatching {
            val jsonArray = JSONArray(jsonString)
            val result = mutableListOf<ModelTestRecord>()

            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(index) ?: continue
                result.add(jsonToRecord(item))
            }

            result
        }.getOrDefault(emptyList())
    }

    private fun recordToJson(record: ModelTestRecord): JSONObject {
        return JSONObject().apply {
            put("providerName", record.providerName)
            put("modelName", record.modelName)
            put("status", record.status)
            put("lastTestedAt", record.lastTestedAt)
            put("httpStatusCode", record.httpStatusCode)
            put("message", record.message)
            put("latencyMs", record.latencyMs)
        }
    }

    private fun jsonToRecord(json: JSONObject): ModelTestRecord {
        return ModelTestRecord(
            providerName = json.optString("providerName", ModelProviderType.NVIDIA),
            modelName = json.optString("modelName", ""),
            status = json.optString("status", NvidiaModelTestStatus.UNTESTED),
            lastTestedAt = json.optString("lastTestedAt", ""),
            httpStatusCode = json.optInt("httpStatusCode", 0),
            message = json.optString("message", ""),
            latencyMs = json.optLong("latencyMs", 0L)
        )
    }
}