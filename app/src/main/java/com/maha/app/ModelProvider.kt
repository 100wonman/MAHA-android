// ModelProvider.kt

package com.maha.app

interface ModelProvider {
    suspend fun generate(request: ModelRequest): ModelResponse
}