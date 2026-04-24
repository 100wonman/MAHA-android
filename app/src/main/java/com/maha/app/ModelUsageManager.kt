// ModelUsageManager.kt

package com.maha.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ModelUsageManager {

    private const val PREFS_NAME = "maha_model_usage_prefs"
    private const val KEY_MODEL_USAGE = "model_usage_json_v1"

    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun recordSuccess(modelName: String) {
        recordUsage(
            modelName = modelName,
            isSuccess = true,
            isRateLimited = false,
            blockedUntilMillis = 0L
        )
    }

    fun recordFailure(modelName: String) {
        recordUsage(
            modelName = modelName,
            isSuccess = false,
            isRateLimited = false,
            blockedUntilMillis = 0L
        )
    }

    fun recordRateLimit(
        modelName: String,
        blockedUntilMillis: Long
    ) {
        recordUsage(
            modelName = modelName,
            isSuccess = false,
            isRateLimited = true,
            blockedUntilMillis = blockedUntilMillis
        )
    }

    fun getTodayUsage(modelName: String): ModelUsage {
        val safeModelName = GeminiModelType.sanitize(modelName)
        val today = getTodayDateText()

        return loadAllUsage()
            .firstOrNull { usage ->
                usage.modelName == safeModelName && usage.date == today
            }
            ?: createEmptyUsage(
                modelName = safeModelName,
                date = today
            )
    }

    fun getAllTodayUsage(): List<ModelUsage> {
        val today = getTodayDateText()

        return GeminiModelType.getCatalog()
            .map { item ->
                loadAllUsage()
                    .firstOrNull { usage ->
                        usage.modelName == item.modelName && usage.date == today
                    }
                    ?: createEmptyUsage(
                        modelName = item.modelName,
                        date = today
                    )
            }
    }

    fun isModelBlocked(modelName: String): Boolean {
        val usage = getTodayUsage(modelName)
        return usage.blockedUntilMillis > System.currentTimeMillis()
    }

    fun getBlockedUntilText(modelName: String): String {
        val usage = getTodayUsage(modelName)

        if (usage.blockedUntilMillis <= System.currentTimeMillis()) {
            return ""
        }

        return SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.getDefault()
        ).format(Date(usage.blockedUntilMillis))
    }

    private fun recordUsage(
        modelName: String,
        isSuccess: Boolean,
        isRateLimited: Boolean,
        blockedUntilMillis: Long
    ) {
        val context = appContext ?: return
        val safeModelName = GeminiModelType.sanitize(modelName)
        val today = getTodayDateText()
        val nowText = getNowText()

        val usageList = loadAllUsage().toMutableList()

        val index = usageList.indexOfFirst { usage ->
            usage.modelName == safeModelName && usage.date == today
        }

        val currentUsage = if (index >= 0) {
            usageList[index]
        } else {
            createEmptyUsage(
                modelName = safeModelName,
                date = today
            )
        }

        val updatedUsage = currentUsage.copy(
            requestCount = currentUsage.requestCount + 1,
            successCount = currentUsage.successCount + if (isSuccess) 1 else 0,
            failureCount = currentUsage.failureCount + if (!isSuccess) 1 else 0,
            rateLimitCount = currentUsage.rateLimitCount + if (isRateLimited) 1 else 0,
            lastUsedAt = nowText,
            blockedUntilMillis = if (blockedUntilMillis > 0L) {
                blockedUntilMillis
            } else {
                currentUsage.blockedUntilMillis
            }
        )

        if (index >= 0) {
            usageList[index] = updatedUsage
        } else {
            usageList.add(updatedUsage)
        }

        saveAllUsage(context, usageList)
    }

    private fun loadAllUsage(): List<ModelUsage> {
        val context = appContext ?: return emptyList()

        val jsonString = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODEL_USAGE, "[]")
            ?: "[]"

        return runCatching {
            val jsonArray = JSONArray(jsonString)
            val result = mutableListOf<ModelUsage>()

            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(index) ?: continue
                result.add(jsonToModelUsage(item))
            }

            result
        }.getOrDefault(emptyList())
    }

    private fun saveAllUsage(
        context: Context,
        usageList: List<ModelUsage>
    ) {
        val jsonArray = JSONArray()

        usageList.forEach { usage ->
            jsonArray.put(modelUsageToJson(usage))
        }

        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODEL_USAGE, jsonArray.toString())
            .apply()
    }

    private fun modelUsageToJson(usage: ModelUsage): JSONObject {
        return JSONObject().apply {
            put("modelName", usage.modelName)
            put("date", usage.date)
            put("requestCount", usage.requestCount)
            put("successCount", usage.successCount)
            put("failureCount", usage.failureCount)
            put("rateLimitCount", usage.rateLimitCount)
            put("lastUsedAt", usage.lastUsedAt)
            put("blockedUntilMillis", usage.blockedUntilMillis)
        }
    }

    private fun jsonToModelUsage(json: JSONObject): ModelUsage {
        return ModelUsage(
            modelName = GeminiModelType.sanitize(
                json.optString("modelName", GeminiModelType.DEFAULT)
            ),
            date = json.optString("date", getTodayDateText()),
            requestCount = json.optInt("requestCount", 0),
            successCount = json.optInt("successCount", 0),
            failureCount = json.optInt("failureCount", 0),
            rateLimitCount = json.optInt("rateLimitCount", 0),
            lastUsedAt = json.optString("lastUsedAt", ""),
            blockedUntilMillis = json.optLong("blockedUntilMillis", 0L)
        )
    }

    private fun createEmptyUsage(
        modelName: String,
        date: String
    ): ModelUsage {
        return ModelUsage(
            modelName = GeminiModelType.sanitize(modelName),
            date = date,
            requestCount = 0,
            successCount = 0,
            failureCount = 0,
            rateLimitCount = 0,
            lastUsedAt = "",
            blockedUntilMillis = 0L
        )
    }

    private fun getTodayDateText(): String {
        return SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.getDefault()
        ).format(Date())
    }

    private fun getNowText(): String {
        return SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.getDefault()
        ).format(Date())
    }
}