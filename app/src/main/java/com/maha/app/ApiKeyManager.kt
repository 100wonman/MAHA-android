// ApiKeyManager.kt

package com.maha.app

import android.content.Context

object ApiKeyManager {

    private const val PREFS_NAME = "maha_api_key_prefs"
    private const val KEY_GOOGLE_API_KEY = "google_api_key"
    private const val KEY_SELECTED_PROVIDER = "selected_provider"

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

    fun saveSelectedProvider(context: Context, provider: String) {
        val safeProvider = when (provider) {
            ModelProviderType.GOOGLE -> ModelProviderType.GOOGLE
            else -> ModelProviderType.DUMMY
        }

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_PROVIDER, safeProvider)
            .apply()

        initialize(context)
    }

    fun getSelectedProvider(context: Context): String {
        val savedProvider = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_PROVIDER, ModelProviderType.DUMMY)
            ?: ModelProviderType.DUMMY

        return when (savedProvider) {
            ModelProviderType.GOOGLE -> ModelProviderType.GOOGLE
            else -> ModelProviderType.DUMMY
        }
    }

    fun getSelectedProvider(): String {
        val context = appContext ?: return ModelProviderType.DUMMY
        return getSelectedProvider(context)
    }
}