// ApiKeyManager.kt

package com.maha.app

import android.content.Context

object ApiKeyManager {

    private const val PREFS_NAME = "maha_api_key_prefs"

    private const val KEY_GOOGLE_API_KEY = "google_api_key"
    private const val KEY_NVIDIA_API_KEY = "nvidia_api_key"
    private const val KEY_SELECTED_PROVIDER = "selected_provider"
    private const val KEY_FALLBACK_PROVIDER = "fallback_provider"
    private const val KEY_FALLBACK_MODEL = "fallback_model"

    private const val DEFAULT_FALLBACK_MODEL = "gemini-flash-lite-latest"

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

    fun saveNvidiaApiKey(context: Context, apiKey: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NVIDIA_API_KEY, apiKey.trim())
            .apply()

        initialize(context)
    }

    fun getNvidiaApiKey(context: Context): String {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_NVIDIA_API_KEY, "")
            ?: ""
    }

    fun getNvidiaApiKey(): String {
        val context = appContext ?: return ""
        return getNvidiaApiKey(context)
    }

    fun hasNvidiaApiKey(): Boolean {
        return getNvidiaApiKey().isNotBlank()
    }

    fun saveSelectedProvider(context: Context, provider: String) {
        val safeProvider = sanitizeProvider(provider)

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

        return sanitizeProvider(savedProvider)
    }

    fun getSelectedProvider(): String {
        val context = appContext ?: return ModelProviderType.DUMMY
        return getSelectedProvider(context)
    }

    fun saveFallbackProvider(context: Context, provider: String) {
        val safeProvider = sanitizeProvider(provider)

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FALLBACK_PROVIDER, safeProvider)
            .apply()

        initialize(context)
    }

    fun getFallbackProvider(context: Context): String {
        val savedProvider = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FALLBACK_PROVIDER, ModelProviderType.GOOGLE)
            ?: ModelProviderType.GOOGLE

        return sanitizeProvider(savedProvider)
    }

    fun getFallbackProvider(): String {
        val context = appContext ?: return ModelProviderType.GOOGLE
        return getFallbackProvider(context)
    }

    fun saveFallbackModel(context: Context, modelName: String) {
        val safeModelName = modelName.trim().ifBlank { DEFAULT_FALLBACK_MODEL }

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FALLBACK_MODEL, safeModelName)
            .apply()

        initialize(context)
    }

    fun getFallbackModel(context: Context): String {
        val savedModel = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FALLBACK_MODEL, DEFAULT_FALLBACK_MODEL)
            ?: DEFAULT_FALLBACK_MODEL

        return savedModel.trim().ifBlank { DEFAULT_FALLBACK_MODEL }
    }

    fun getFallbackModel(): String {
        val context = appContext ?: return DEFAULT_FALLBACK_MODEL
        return getFallbackModel(context)
    }

    private fun sanitizeProvider(provider: String): String {
        return when (provider) {
            ModelProviderType.GOOGLE -> ModelProviderType.GOOGLE
            ModelProviderType.NVIDIA -> ModelProviderType.NVIDIA
            ModelProviderType.DUMMY -> ModelProviderType.DUMMY
            else -> ModelProviderType.DUMMY
        }
    }
}