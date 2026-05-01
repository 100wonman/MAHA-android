package com.maha.app

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RagStorageManager(
    private val context: Context
) {
    fun getRagRootDir(): File {
        return File(getMahaRootDir(), "rag")
    }

    fun getDocumentsDir(): File {
        return File(getRagRootDir(), "documents")
    }

    fun getChunksDir(): File {
        return File(getRagRootDir(), "chunks")
    }

    fun getIndexesDir(): File {
        return File(getRagRootDir(), "indexes")
    }

    fun getIndexMetadataFile(): File {
        return File(getRagRootDir(), "index_metadata.json")
    }

    fun ensureRagDirectories() {
        getRagRootDir().mkdirs()
        getDocumentsDir().mkdirs()
        getChunksDir().mkdirs()
        getIndexesDir().mkdirs()
        ensureIndexMetadata()
    }

    private fun ensureIndexMetadata() {
        val metadataFile = getIndexMetadataFile()
        if (metadataFile.exists()) return

        metadataFile.parentFile?.mkdirs()
        val now = currentIsoText()
        metadataFile.writeText(
            """
            {
              "indexVersion": 1,
              "createdAt": "$now",
              "updatedAt": "$now",
              "documentCount": 0,
              "chunkCount": 0,
              "status": "EMPTY"
            }
            """.trimIndent()
        )
    }

    private fun getMahaRootDir(): File {
        val externalFilesDir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(externalFilesDir, "MAHA")
    }

    private fun currentIsoText(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault()).format(Date())
    }
}
