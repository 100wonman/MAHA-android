// ModelTestManager.kt

package com.maha.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ModelTestManager {

    private const val PREFS_NAME = "maha_model_test_prefs"
    private const val KEY_MODEL_TEST_RECORDS = "model_test_records_json_v2"

    fun saveRecord(context: Context, record: ModelTestRecord) {
        val currentRecords = getRecords(context).toMutableList()
        val index = currentRecords.indexOfFirst {
            it.providerName == record.providerName && it.modelName == record.modelName
        }

        val previousRecord = if (index >= 0) currentRecords[index] else null
        val mergedRecord = mergeRecord(previousRecord, record)

        if (index >= 0) {
            currentRecords[index] = mergedRecord
        } else {
            currentRecords.add(mergedRecord)
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
            latencyMs = 0L,
            testCount = 0,
            successCount = 0,
            successRate = 0,
            averageLatencyMs = 0L,
            selfReportedInfo = ModelInfo()
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

    private fun mergeRecord(
        previousRecord: ModelTestRecord?,
        newRecord: ModelTestRecord
    ): ModelTestRecord {
        val previousTestCount = previousRecord?.testCount ?: 0
        val previousSuccessCount = previousRecord?.successCount ?: 0
        val previousAverageLatencyMs = previousRecord?.averageLatencyMs ?: 0L

        val nextTestCount = previousTestCount + 1
        val nextSuccessCount = previousSuccessCount + if (newRecord.status == NvidiaModelTestStatus.AVAILABLE) 1 else 0

        val nextAverageLatencyMs = if (nextTestCount <= 1) {
            newRecord.latencyMs
        } else {
            (((previousAverageLatencyMs * previousTestCount) + newRecord.latencyMs) / nextTestCount)
        }

        val nextSuccessRate = if (nextTestCount == 0) {
            0
        } else {
            ((nextSuccessCount.toDouble() / nextTestCount.toDouble()) * 100).toInt()
        }

        val nextSelfReportedInfo = if (newRecord.selfReportedInfo.rawJson.isNotBlank()) {
            newRecord.selfReportedInfo
        } else {
            previousRecord?.selfReportedInfo ?: ModelInfo()
        }

        return newRecord.copy(
            testCount = nextTestCount,
            successCount = nextSuccessCount,
            successRate = nextSuccessRate,
            averageLatencyMs = nextAverageLatencyMs,
            selfReportedInfo = nextSelfReportedInfo
        )
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
            put("testCount", record.testCount)
            put("successCount", record.successCount)
            put("successRate", record.successRate)
            put("averageLatencyMs", record.averageLatencyMs)
            put("selfReportedInfo", modelInfoToJson(record.selfReportedInfo))
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
            latencyMs = json.optLong("latencyMs", 0L),
            testCount = json.optInt("testCount", 0),
            successCount = json.optInt("successCount", 0),
            successRate = json.optInt("successRate", 0),
            averageLatencyMs = json.optLong("averageLatencyMs", 0L),
            selfReportedInfo = jsonToModelInfo(json.optJSONObject("selfReportedInfo"))
        )
    }

    private fun modelInfoToJson(info: ModelInfo): JSONObject {
        return JSONObject().apply {
            put("displayName", info.displayName)
            put("modelFamily", info.modelFamily)
            put("strengths", info.strengths)
            put("limitations", info.limitations)
            put("recommendedUse", info.recommendedUse)
            put("rawJson", info.rawJson)
        }
    }

    private fun jsonToModelInfo(json: JSONObject?): ModelInfo {
        if (json == null) return ModelInfo()

        return ModelInfo(
            displayName = json.optString("displayName", ""),
            modelFamily = json.optString("modelFamily", ""),
            strengths = json.optString("strengths", ""),
            limitations = json.optString("limitations", ""),
            recommendedUse = json.optString("recommendedUse", ""),
            rawJson = json.optString("rawJson", "")
        )
    }
}