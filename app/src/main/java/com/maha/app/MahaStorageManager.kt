package com.maha.app

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

class MahaStorageManager(
    private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getRootDir(): File {
        return File(context.getExternalFilesDir(null), ROOT_FOLDER_NAME)
    }

    fun getConversationsDir(): File {
        return File(getRootDir(), CONVERSATIONS_FOLDER_NAME)
    }

    fun ensureDirectories() {
        if (isSafReady()) {
            getSafConversationsDocument()
            return
        }

        getRootDir().mkdirs()
        getConversationsDir().mkdirs()
    }

    fun saveSafRootUri(uri: Uri) {
        prefs.edit()
            .putString(KEY_SAF_ROOT_URI, uri.toString())
            .apply()
    }

    fun clearSafRootUri() {
        prefs.edit()
            .remove(KEY_SAF_ROOT_URI)
            .apply()
    }

    fun getSafRootUri(): Uri? {
        val value = prefs.getString(KEY_SAF_ROOT_URI, null) ?: return null
        return runCatching { Uri.parse(value) }.getOrNull()
    }

    fun isSafReady(): Boolean {
        val uri = getSafRootUri() ?: return false
        return context.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isReadPermission && permission.isWritePermission
        }
    }

    fun getStorageMode(): MahaStorageMode {
        return when {
            isSafReady() -> MahaStorageMode.SAF_READY
            getRootDir().mkdirs() || getRootDir().exists() -> MahaStorageMode.APP_SPECIFIC_READY
            getSafRootUri() != null -> MahaStorageMode.NEED_FOLDER_PERMISSION
            else -> MahaStorageMode.UNAVAILABLE
        }
    }

    fun getStorageStatusText(): String {
        return when (getStorageMode()) {
            MahaStorageMode.SAF_READY -> "SAF 연결됨"
            MahaStorageMode.APP_SPECIFIC_READY -> "기본 앱 저장소 사용 중"
            MahaStorageMode.NEED_FOLDER_PERMISSION -> "저장 폴더 권한 필요"
            MahaStorageMode.UNAVAILABLE -> "저장소 사용 불가"
        }
    }

    fun getStorageLocationText(): String {
        return if (isSafReady()) {
            val root = getSafRootUri()?.toString().orEmpty()
            "선택 폴더 / MAHA"
        } else {
            getRootDir().absolutePath
        }
    }

    fun getSafMahaRootDocument(): DocumentFile? {
        val uri = getSafRootUri() ?: return null
        if (!isSafReady()) return null

        val selectedRoot = DocumentFile.fromTreeUri(context, uri) ?: return null
        if (!selectedRoot.canRead() || !selectedRoot.canWrite()) return null

        if (selectedRoot.name == ROOT_FOLDER_NAME) {
            return selectedRoot
        }

        return selectedRoot.findFile(ROOT_FOLDER_NAME)
            ?: selectedRoot.createDirectory(ROOT_FOLDER_NAME)
    }

    fun getSafConversationsDocument(): DocumentFile? {
        val mahaRoot = getSafMahaRootDocument() ?: return null
        return mahaRoot.findFile(CONVERSATIONS_FOLDER_NAME)
            ?: mahaRoot.createDirectory(CONVERSATIONS_FOLDER_NAME)
    }

    companion object {
        private const val ROOT_FOLDER_NAME = "MAHA"
        private const val CONVERSATIONS_FOLDER_NAME = "conversations"
        private const val PREFS_NAME = "maha_storage_prefs"
        private const val KEY_SAF_ROOT_URI = "saf_root_uri"
    }
}

enum class MahaStorageMode {
    SAF_READY,
    APP_SPECIFIC_READY,
    NEED_FOLDER_PERMISSION,
    UNAVAILABLE
}
