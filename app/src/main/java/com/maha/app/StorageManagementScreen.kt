package com.maha.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.io.File
import java.text.DecimalFormat

@Composable
fun StorageManagementScreen(
    modifier: Modifier = Modifier,
    onSessionDeleted: (String) -> Unit,
    onStorageChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    val storageManager = remember { MahaStorageManager(context.applicationContext) }
    val conversationFileStore = remember {
        ConversationFileStore(
            context = context.applicationContext,
            storageManager = storageManager
        )
    }
    val ragStorageManager = remember { RagStorageManager(context.applicationContext) }
    val ragIndexStore = remember { RagIndexStore(ragStorageManager) }
    val conversationChunkIndexer = remember {
        ConversationChunkIndexer(
            context = context.applicationContext,
            ragStorageManager = ragStorageManager,
            ragIndexStore = ragIndexStore
        )
    }
    val ragSearchEngine = remember {
        RagKeywordSearchEngine(
            ragStorageManager = ragStorageManager,
            ragIndexStore = ragIndexStore
        )
    }

    var snapshot by remember { mutableStateOf(loadAppSpecificStorageSnapshot(context)) }
    var fileViewerState by remember { mutableStateOf<StorageFileViewerState?>(null) }
    var deleteTarget by remember { mutableStateOf<StorageSessionFileItem?>(null) }
    var backupResultMessage by remember { mutableStateOf<String?>(null) }
    var restoreResultMessage by remember { mutableStateOf<String?>(null) }
    var chunkIndexResultMessage by remember { mutableStateOf<String?>(null) }
    var isSafReady by remember { mutableStateOf(storageManager.isSafReady()) }
    var backupSessions by remember { mutableStateOf<List<SafBackupSessionInfo>>(emptyList()) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var ragSearchQuery by remember { mutableStateOf("") }
    var ragSearchResults by remember { mutableStateOf<List<RagSearchResult>>(emptyList()) }
    var ragSearchMessage by remember { mutableStateOf("인덱싱된 chunk가 있으면 keyword 검색을 실행할 수 있습니다.") }

    fun refreshSnapshot() {
        snapshot = loadAppSpecificStorageSnapshot(context)
        isSafReady = storageManager.isSafReady()
        backupSessions = if (storageManager.isSafReady()) {
            conversationFileStore.loadSafBackupSessions()
        } else {
            emptyList()
        }
    }

    fun showBackupResult(result: ConversationBackupResult) {
        backupResultMessage = buildString {
            append("백업 결과\n")
            append("copied: ${result.copiedCount}\n")
            append("skipped: ${result.skippedCount}\n")
            append("failed: ${result.failedCount}")
        }
    }

    fun showRestoreResult(result: ConversationRestoreResult) {
        restoreResultMessage = buildString {
            append("복원 결과\n")
            append("restored: ${result.restoredCount}\n")
            append("skipped: ${result.skippedCount}\n")
            append("failed: ${result.failedCount}")
        }
    }

    fun showChunkIndexResult(result: ConversationChunkIndexResult) {
        chunkIndexResultMessage = buildString {
            append("인덱싱 결과\n")
            append("chunks: ${result.createdChunkCount}\n")
            append("messages: ${result.processedMessageCount}\n")
            append("failed: ${result.failedCount}\n")
            append(result.message)
        }
    }

    fun runRagKeywordSearch() {
        val query = ragSearchQuery.trim()
        if (query.isBlank()) {
            ragSearchResults = emptyList()
            ragSearchMessage = "검색어를 입력하세요."
            return
        }

        val results = ragSearchEngine.search(query = query, topK = 10, maxLoadedChunks = 50)
        ragSearchResults = results
        ragSearchMessage = if (results.isEmpty()) {
            "검색 결과가 없습니다."
        } else {
            "검색 결과 ${results.size}개"
        }
    }

    LaunchedEffect(Unit) {
        refreshSnapshot()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        StorageStatusCard(
            snapshot = snapshot,
            isSafReady = isSafReady,
            backupSessionCount = backupSessions.size,
            onBackupAll = {
                showBackupResult(conversationFileStore.backupAllSessionsToSaf())
                refreshSnapshot()
            },
            onOpenRestore = {
                refreshSnapshot()
                showRestoreDialog = true
            }
        )

        RagSearchTestCard(
            query = ragSearchQuery,
            onQueryChange = { ragSearchQuery = it },
            resultMessage = ragSearchMessage,
            results = ragSearchResults,
            onSearch = ::runRagKeywordSearch,
            onClear = {
                ragSearchQuery = ""
                ragSearchResults = emptyList()
                ragSearchMessage = "인덱싱된 chunk가 있으면 keyword 검색을 실행할 수 있습니다."
            }
        )

        Text(
            text = "대화 세션 파일",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        if (snapshot.sessions.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3F49)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "앱 전용 저장소에 저장된 대화 세션이 없습니다.",
                    modifier = Modifier.padding(18.dp),
                    color = Color(0xFFD0D3DA)
                )
            }
        } else {
            snapshot.sessions.forEach { item ->
                StorageSessionCard(
                    item = item,
                    isBackupEnabled = isSafReady,
                    onOpenSessionJson = {
                        fileViewerState = StorageFileViewerState(
                            title = "session.json",
                            fileName = "${item.folderName}/session.json",
                            content = item.sessionJsonFile.readTextSafely()
                        )
                    },
                    onOpenMessages = {
                        fileViewerState = StorageFileViewerState(
                            title = "messages.jsonl",
                            fileName = "${item.folderName}/messages.jsonl",
                            content = item.messagesFile.readTextSafely()
                        )
                    },
                    onBackup = {
                        showBackupResult(conversationFileStore.backupSessionToSaf(item.sessionId))
                        refreshSnapshot()
                    },
                    onIndexSession = {
                        showChunkIndexResult(
                            conversationChunkIndexer.indexConversationSession(
                                sessionId = item.sessionId,
                                title = item.title
                            )
                        )
                    },
                    onDelete = {
                        deleteTarget = item
                    }
                )
            }
        }
    }

    fileViewerState?.let { viewerState ->
        AlertDialog(
            onDismissRequest = { fileViewerState = null },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = viewerState.title)
                    Text(
                        text = viewerState.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .background(Color(0xFF050A0F))
                        .padding(12.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = viewerState.content.ifBlank { "파일 내용이 없습니다." },
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            color = Color(0xFFD0D3DA),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(viewerState.content))
                    }
                ) {
                    Text(text = "전체 복사")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileViewerState = null }) {
                    Text(text = "닫기")
                }
            }
        )
    }

    backupResultMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { backupResultMessage = null },
            title = { Text(text = "백업 결과") },
            text = { Text(text = message) },
            confirmButton = {
                TextButton(onClick = { backupResultMessage = null }) {
                    Text(text = "확인")
                }
            }
        )
    }


    restoreResultMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { restoreResultMessage = null },
            title = { Text(text = "복원 결과") },
            text = { Text(text = message) },
            confirmButton = {
                TextButton(onClick = { restoreResultMessage = null }) {
                    Text(text = "확인")
                }
            }
        )
    }

    chunkIndexResultMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { chunkIndexResultMessage = null },
            title = { Text(text = "인덱싱 결과") },
            text = { Text(text = message) },
            confirmButton = {
                TextButton(onClick = { chunkIndexResultMessage = null }) {
                    Text(text = "확인")
                }
            }
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text(text = "백업에서 복원") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                ) {
                    if (!isSafReady) {
                        Text(text = "SAF 백업 폴더가 연결되어 있지 않습니다.")
                    } else if (backupSessions.isEmpty()) {
                        Text(text = "복원 가능한 백업 세션이 없습니다.")
                    } else {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            backupSessions.forEach { backupItem ->
                                SafBackupSessionCard(
                                    item = backupItem,
                                    onRestore = {
                                        val result = conversationFileStore.restoreSafBackupSession(backupItem.sessionId)
                                        showRestoreResult(result)
                                        refreshSnapshot()
                                        if (result.restoredCount > 0) {
                                            onStorageChanged()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text(text = "닫기")
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(text = "세션 삭제") },
            text = {
                Text(
                    text = "앱 전용 저장소에서 '${target.title}' 세션 폴더를 삭제합니다. SAF 백업 폴더는 삭제하지 않습니다."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        target.folder.deleteRecursively()
                        onSessionDeleted(target.sessionId)
                        deleteTarget = null
                        refreshSnapshot()
                    }
                ) {
                    Text(text = "삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(text = "취소")
                }
            }
        )
    }
}

@Composable
private fun RagSearchTestCard(
    query: String,
    onQueryChange: (String) -> Unit,
    resultMessage: String,
    results: List<RagSearchResult>,
    onSearch: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3F49)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "RAG 검색 테스트",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "앱 전용 저장소의 index_metadata와 chunk 파일을 keyword로 검색합니다.",
                color = Color(0xFFB8BCC6),
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(text = "검색어 입력") }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSearch,
                    enabled = query.trim().isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "검색")
                }
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "초기화")
                }
            }
            Text(
                text = resultMessage,
                color = Color(0xFFD0D3DA),
                style = MaterialTheme.typography.bodySmall
            )
            results.forEach { result ->
                RagSearchResultCard(result = result)
            }
        }
    }
}

@Composable
private fun RagSearchResultCard(result: RagSearchResult) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF050A0F)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = result.title.ifBlank { result.sourceId },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            StorageInfoRow(label = "sourceType", value = result.sourceType)
            StorageInfoRow(label = "score", value = result.score.toString())
            StorageInfoRow(label = "filePath", value = result.filePath)
            Text(
                text = result.matchedTextSnippet.ifBlank { result.textPreview },
                color = Color(0xFFD0D3DA),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StorageStatusCard(
    snapshot: AppSpecificStorageSnapshot,
    isSafReady: Boolean,
    backupSessionCount: Int,
    onBackupAll: () -> Unit,
    onOpenRestore: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3F49)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "저장소 상태",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            StorageInfoRow(label = "기본 저장소", value = "앱 전용 저장소")
            StorageInfoRow(label = "위치", value = snapshot.rootPath)
            StorageInfoRow(label = "세션 수", value = "${snapshot.sessionCount}개")
            StorageInfoRow(label = "파일 수", value = "${snapshot.fileCount}개")
            StorageInfoRow(label = "대략적 용량", value = formatBytes(snapshot.totalBytes))
            StorageInfoRow(
                label = "SAF 백업",
                value = if (isSafReady) "연결됨" else "미연결"
            )
            StorageInfoRow(label = "백업 세션", value = "${backupSessionCount}개")
            Button(
                onClick = onBackupAll,
                enabled = isSafReady && snapshot.sessions.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "전체 세션 백업")
            }
            Button(
                onClick = onOpenRestore,
                enabled = isSafReady,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "백업에서 복원")
            }
            if (!isSafReady) {
                Text(
                    text = "SAF 백업 폴더 연결 후 백업/복원을 사용할 수 있습니다.",
                    color = Color(0xFFB8BCC6),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun StorageSessionCard(
    item: StorageSessionFileItem,
    isBackupEnabled: Boolean,
    onOpenSessionJson: () -> Unit,
    onOpenMessages: () -> Unit,
    onBackup: () -> Unit,
    onIndexSession: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3F49)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            StorageInfoRow(label = "sessionId", value = item.sessionId)
            StorageInfoRow(label = "폴더명", value = item.folderName)
            StorageInfoRow(label = "메시지 수", value = "${item.messageCount}개")
            StorageInfoRow(label = "마지막 수정", value = item.updatedAt.ifBlank { "알 수 없음" })
            StorageInfoRow(label = "파일 크기", value = formatBytes(item.totalBytes))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpenSessionJson,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "session.json 보기")
                }

                Button(
                    onClick = onOpenMessages,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "messages.jsonl 보기")
                }

                Button(
                    onClick = onBackup,
                    enabled = isBackupEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "선택 세션 백업")
                }

                Button(
                    onClick = onIndexSession,
                    enabled = item.messageCount > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "선택 세션 인덱싱")
                }

                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "삭제")
                }
            }
        }
    }
}


@Composable
private fun SafBackupSessionCard(
    item: SafBackupSessionInfo,
    onRestore: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3F49)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            StorageInfoRow(label = "sessionId", value = item.sessionId)
            StorageInfoRow(label = "백업 폴더", value = item.folderName)
            StorageInfoRow(label = "메시지 수", value = "${item.messageCount}개")
            StorageInfoRow(label = "마지막 수정", value = item.updatedAt.ifBlank { "알 수 없음" })
            Button(
                onClick = onRestore,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "복원")
            }
        }
    }
}

@Composable
private fun StorageInfoRow(
    label: String,
    value: String
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.width(96.dp),
            color = Color(0xFFB8BCC6),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            color = Color(0xFFD0D3DA),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun loadAppSpecificStorageSnapshot(context: Context): AppSpecificStorageSnapshot {
    val rootDir = File(context.getExternalFilesDir(null), "MAHA")
    val conversationsDir = File(rootDir, "conversations")

    val sessionItems = conversationsDir
        .listFiles()
        ?.filter { folder ->
            folder.isDirectory &&
                    folder.name.startsWith("session_") &&
                    File(folder, "session.json").exists()
        }
        ?.mapNotNull { folder -> loadStorageSessionItem(folder) }
        ?.sortedWith(
            compareByDescending<StorageSessionFileItem> { it.updatedAt }
                .thenBy { it.folderName }
        )
        ?: emptyList()

    return AppSpecificStorageSnapshot(
        rootPath = rootDir.absolutePath,
        sessionCount = sessionItems.size,
        fileCount = rootDir.countFilesRecursively(),
        totalBytes = rootDir.sizeRecursively(),
        sessions = sessionItems
    )
}

private fun loadStorageSessionItem(folder: File): StorageSessionFileItem? {
    val sessionJsonFile = File(folder, "session.json")
    val messagesFile = File(folder, "messages.jsonl")

    return runCatching {
        val json = JSONObject(sessionJsonFile.readText())
        val sessionId = json.optString("sessionId")
        if (sessionId.isBlank()) return null

        StorageSessionFileItem(
            sessionId = sessionId,
            title = json.optString("title", "제목 없음"),
            folderName = folder.name,
            updatedAt = json.optString("updatedAt"),
            messageCount = if (messagesFile.exists()) {
                messagesFile.useLines { lines -> lines.count { it.isNotBlank() } }
            } else {
                json.optInt("messageCount", 0)
            },
            totalBytes = folder.sizeRecursively(),
            folder = folder,
            sessionJsonFile = sessionJsonFile,
            messagesFile = messagesFile
        )
    }.getOrNull()
}

private fun File.readTextSafely(): String {
    if (!exists()) return "파일이 없습니다."
    return runCatching { readText() }.getOrElse { error ->
        "파일을 읽을 수 없습니다: ${error.message ?: "알 수 없는 오류"}"
    }
}

private fun File.sizeRecursively(): Long {
    if (!exists()) return 0L
    if (isFile) return length()
    return listFiles()?.sumOf { child -> child.sizeRecursively() } ?: 0L
}

private fun File.countFilesRecursively(): Int {
    if (!exists()) return 0
    if (isFile) return 1
    return listFiles()?.sumOf { child -> child.countFilesRecursively() } ?: 0
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "${bytes} B"

    val units = listOf("KB", "MB", "GB")
    var value = bytes / 1024.0
    var unitIndex = 0

    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }

    return "${DecimalFormat("#,##0.#").format(value)} ${units[unitIndex]}"
}

private data class AppSpecificStorageSnapshot(
    val rootPath: String,
    val sessionCount: Int,
    val fileCount: Int,
    val totalBytes: Long,
    val sessions: List<StorageSessionFileItem>
)

private data class StorageSessionFileItem(
    val sessionId: String,
    val title: String,
    val folderName: String,
    val updatedAt: String,
    val messageCount: Int,
    val totalBytes: Long,
    val folder: File,
    val sessionJsonFile: File,
    val messagesFile: File
)

private data class StorageFileViewerState(
    val title: String,
    val fileName: String,
    val content: String
)
