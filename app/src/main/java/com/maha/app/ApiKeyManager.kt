// ApiKeyManager.kt

package com.maha.app

import android.content.Context

object ApiKeyManager {

    private const val PREFS_NAME = "maha_api_key_prefs"
    private const val KEY_GOOGLE_API_KEY = "google_api_key"

    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun saveGoogleApiKey(context: Context, apiKey: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GOOGLE_API_KEY, apiKey.trim())
            .apply()

        initialize(context)
    }

    fun getGoogleApiKey(context: Context): String {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_GOOGLE_API_KEY, "")
            ?: ""
    }

    fun getGoogleApiKey(): String {
        val context = appContext ?: return ""
        return getGoogleApiKey(context)
    }

    fun hasGoogleApiKey(): Boolean {
        return getGoogleApiKey().isNotBlank()
    }
}