// ApiKeyManager.kt

package com.maha.app

object ApiKeyManager {

    fun getGoogleApiKey(): String {
        return ""
    }

    fun hasGoogleApiKey(): Boolean {
        return getGoogleApiKey().isNotBlank()
    }
}