package com.maha.app

import android.content.Context
import java.io.File

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
    }

    private fun getMahaRootDir(): File {
        val externalFilesDir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(externalFilesDir, "MAHA")
    }
}
