// ModelCatalogScreen.kt

package com.maha.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class ModelListRowItem(
    val modelName: String,
    val displayName: String,
    val description: String,
    val supportedGenerationMethods: List<String>,
    val stabilityStatus: String,
    val recommendedWorker: String,
    val estimatedDailyLimit: Int,
    val isGenerateContentSupported: Boolean,
    val tags: List<String>,
    val sourceType: String,
    val providerName: String,
    val isFreeCandidate: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelCatalogScreen(
    discoveredModels: List<DiscoveredModel>,
    selectedModelName: String,
    isSelectionMode: Boolean,
    isSearchingModels: Boolean,
    modelSearchMessage: String,
    onSearchApiModelsClick: () -> Unit,
    onSelectModelClick: (String) -> Unit,
    onMenuClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedModel by remember { mutableStateOf<ModelListRowItem?>(null) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var testingModelName by remember { mutableStateOf("") }
    var testMessage by remember { mutableStateOf("") }

    val safeSelectedModelName = selectedModelName.trim().removePrefix("models/")

    val manualRows = GeminiModelType.getCatalog().map { item ->
        ModelListRowItem(
            modelName = item.modelName,
            displayName = item.displayName,
            description = item.description,
            supportedGenerationMethods = listOf("generateContent"),
            stabilityStatus = item.stabilityStatus,
            recommendedWorker = item.recommendedWorker,
            estimatedDailyLimit = item.estimatedDailyLimit,
            isGenerateContentSupported = true,
            tags = listOf(item.providerName, "수동 등록", "텍스트 생성"),
            sourceType = "수동",
            providerName = item.providerName,
            isFreeCandidate = true
        )
    }

    val discoveredRows = discoveredModels.map { model ->
        ModelListRowItem(
            modelName = model.modelName,
            displayName = model.displayName.ifBlank { model.modelName },
            description = model.description,
            supportedGenerationMethods = model.supportedGenerationMethods,
            stabilityStatus = "API 검색",
            recommendedWorker = if (model.isGenerateContentSupported) {
                "직접 확인 필요"
            } else {
                "Worker 선택 불가"
            },
            estimatedDailyLimit = 100,
            isGenerateContentSupported = model.isGenerateContentSupported,
            tags = model.tags,
            sourceType = "API",
            providerName = model.providerName,
            isFreeCandidate = model.isFreeCandidate
        )
    }

    val allRows = (manualRows + discoveredRows)
        .filter { it.modelName.isNotBlank() }
        .distinctBy { "${it.providerName}:${it.modelName}" }

    val availableRows = allRows.filter { row ->
        if (row.providerName != ModelProviderType.NVIDIA) {
            row.isGenerateContentSupported
        } else {
            ModelTestManager.getRecord(
                context = context,
                providerName = row.providerName,
                modelName = row.modelName
            ).status == NvidiaModelTestStatus.AVAILABLE
        }
    }

    val visibleRows = if (selectedTabIndex == 0) {
        availableRows
    } else {
        allRows
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isSelectionMode) "Worker 모델 선택" else "Model Catalog",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Text(
                            text = "☰",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF111827),
                    titleContentColor = androidx.compose.ui.graphics.Color(0xFFF8FAFC),
                    navigationIconContentColor = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                )
            )
        },
        containerColor = androidx.compose.ui.graphics.Color(0xFF070B12)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color(0xFF070B12))
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                StatusPanel(
                    title = if (isSelectionMode) "Worker 모델 선택" else "모델 사용량 안내",
                    status = "WAITING",
                    message = if (isSelectionMode) {
                        "NVIDIA API 모델은 테스트 결과가 '호출 가능'인 경우에만 선택할 수 있습니다."
                    } else {
                        "무료/유료는 단정하지 않습니다. 실제 테스트 결과로 호출 가능 여부를 표시합니다."
                    }
                )
            }

            item {
                PrimaryActionButton(
                    text = if (isSearchingModels) "API 모델 검색 중..." else "API 모델 검색",
                    enabled = !isSearchingModels,
                    onClick = onSearchApiModelsClick
                )
            }

            if (modelSearchMessage.isNotBlank()) {
                item {
                    StatusPanel(
                        title = "API 모델 검색 결과",
                        status = if (modelSearchMessage.contains("실패") || modelSearchMessage.contains("failed", ignoreCase = true)) {
                            "FAILED"
                        } else {
                            "SUCCESS"
                        },
                        message = modelSearchMessage
                    )
                }
            }

            if (testMessage.isNotBlank()) {
                item {
                    StatusPanel(
                        title = "모델 테스트 결과",
                        status = if (testMessage.contains("호출 가능")) "SUCCESS" else "WAITING",
                        message = testMessage
                    )
                }
            }

            item {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = androidx.compose.ui.graphics.Color(0xFF111827),
                    contentColor = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = {
                            Text(text = "호출 가능 (${availableRows.size})")
                        }
                    )

                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = {
                            Text(text = "전체 모델 (${allRows.size})")
                        }
                    )
                }
            }

            item {
                Text(
                    text = if (selectedTabIndex == 0) "호출 가능 모델" else "전체 모델",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color(0xFFF8FAFC),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (visibleRows.isEmpty()) {
                item {
                    EmptyInfoCard(text = "표시할 모델이 없습니다. 전체 모델에서 NVIDIA 모델을 테스트하세요.")
                }
            } else {
                items(visibleRows) { row ->
                    ModelSimpleRow(
                        item = row,
                        isSelected = row.modelName == safeSelectedModelName,
                        testRecord = ModelTestManager.getRecord(
                            context = context,
                            providerName = row.providerName,
                            modelName = row.modelName
                        ),
                        onClick = {
                            selectedModel = row
                        }
                    )
                }
            }

            item {
                SecondaryActionButton(
                    text = "Back",
                    enabled = true,
                    onClick = onBackClick
                )
            }
        }
    }

    if (selectedModel != null) {
        val currentModel = selectedModel!!
        val currentRecord = ModelTestManager.getRecord(
            context = context,
            providerName = currentModel.providerName,
            modelName = currentModel.modelName
        )

        ModelDetailDialog(
            item = currentModel,
            testRecord = currentRecord,
            isTesting = testingModelName == currentModel.modelName,
            isSelected = currentModel.modelName == safeSelectedModelName,
            isSelectionMode = isSelectionMode,
            onTestModelClick = {
                if (currentModel.providerName != ModelProviderType.NVIDIA) {
                    testMessage = "NVIDIA 모델만 테스트할 수 있습니다."
                    return@ModelDetailDialog
                }

                scope.launch {
                    testingModelName = currentModel.modelName
                    testMessage = "모델 테스트 중: ${currentModel.modelName}"

                    val record = NvidiaModelProvider.testModel(currentModel.modelName)
                    ModelTestManager.saveRecord(context, record)

                    testMessage = "테스트 완료: ${toKoreanStatus(record.status)} / ${record.latencyMs}ms"
                    testingModelName = ""
                    selectedModel = null
                }
            },
            onSelectModelClick = {
                onSelectModelClick(currentModel.modelName)
                selectedModel = null
            },
            onDismiss = {
                selectedModel = null
            }
        )
    }
}

@Composable
private fun ModelSimpleRow(
    item: ModelListRowItem,
    isSelected: Boolean,
    testRecord: ModelTestRecord,
    onClick: () -> Unit
) {
    val usage = ModelUsageManager.getTodayUsage(item.modelName)
    val isBlocked = ModelUsageManager.isModelBlocked(item.modelName)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                androidx.compose.ui.graphics.Color(0xFF24324A)
            } else {
                androidx.compose.ui.graphics.Color(0xFF1A2230)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "[${item.providerName}] ${item.modelName}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                    )

                    Text(
                        text = "${item.sourceType} · 오늘 ${usage.requestCount}회 · ${toKoreanStatus(testRecord.status)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = androidx.compose.ui.graphics.Color(0xFFD3DBE7)
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isSelected) {
                        SmallStatusChip(text = "선택됨", status = "SUCCESS")
                    }

                    SmallStatusChip(
                        text = toKoreanStatus(testRecord.status),
                        status = statusToUiStatus(testRecord.status)
                    )

                    if (isBlocked) {
                        SmallStatusChip(text = "일시 제한", status = "FAILED")
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelDetailDialog(
    item: ModelListRowItem,
    testRecord: ModelTestRecord,
    isTesting: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onTestModelClick: () -> Unit,
    onSelectModelClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val usage = ModelUsageManager.getTodayUsage(item.modelName)
    val isBlocked = ModelUsageManager.isModelBlocked(item.modelName)
    val blockedUntilText = ModelUsageManager.getBlockedUntilText(item.modelName)
    val estimatedRemaining = (item.estimatedDailyLimit - usage.requestCount).coerceAtLeast(0)
    val canSelect = if (item.providerName == ModelProviderType.NVIDIA) {
        testRecord.status == NvidiaModelTestStatus.AVAILABLE
    } else {
        item.isGenerateContentSupported
    }

    AlertDialog(
        containerColor = androidx.compose.ui.graphics.Color(0xFF1A2230),
        titleContentColor = androidx.compose.ui.graphics.Color(0xFFF8FAFC),
        textContentColor = androidx.compose.ui.graphics.Color(0xFFE5ECF6),
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = isSelectionMode && canSelect && !isSelected,
                onClick = onSelectModelClick
            ) {
                Text(
                    text = when {
                        !isSelectionMode -> "Agent Detail에서 선택 가능"
                        isSelected -> "이미 선택됨"
                        !canSelect -> "테스트 후 선택 가능"
                        else -> "이 모델 선택"
                    },
                    color = if (isSelectionMode && canSelect && !isSelected) {
                        androidx.compose.ui.graphics.Color(0xFF93C5FD)
                    } else {
                        androidx.compose.ui.graphics.Color(0xFF94A3B8)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled = item.providerName == ModelProviderType.NVIDIA && !isTesting,
                onClick = onTestModelClick
            ) {
                Text(
                    text = if (isTesting) "테스트 중..." else "이 모델 테스트",
                    color = if (item.providerName == ModelProviderType.NVIDIA) {
                        androidx.compose.ui.graphics.Color(0xFF93C5FD)
                    } else {
                        androidx.compose.ui.graphics.Color(0xFF94A3B8)
                    }
                )
            }
        },
        title = {
            Text(
                text = "모델 상세 정보",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    SelectionContainer {
                        Text(
                            text = "[${item.providerName}] ${item.modelName}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                        )
                    }
                }

                item {
                    DetailText(label = "Provider", value = item.providerName)
                }

                item {
                    DetailText(label = "테스트 상태", value = toKoreanStatus(testRecord.status))
                }

                item {
                    DetailText(label = "테스트 메시지", value = testRecord.message.ifBlank { "없음" })
                }

                item {
                    DetailText(label = "마지막 테스트", value = testRecord.lastTestedAt.ifBlank { "없음" })
                }

                item {
                    DetailText(label = "응답 시간", value = "${testRecord.latencyMs}ms")
                }

                item {
                    DetailText(label = "HTTP 코드", value = testRecord.httpStatusCode.toString())
                }

                item {
                    DetailText(label = "표시 이름", value = item.displayName)
                }

                item {
                    DetailText(
                        label = "설명",
                        value = item.description.ifBlank { "설명이 없습니다." }
                    )
                }

                item {
                    DetailText(
                        label = "지원 메서드",
                        value = item.supportedGenerationMethods.joinToString(", ").ifBlank { "알 수 없음" }
                    )
                }

                item {
                    DetailText(label = "안정성 상태", value = item.stabilityStatus)
                }

                item {
                    DetailText(label = "권장 Worker", value = item.recommendedWorker)
                }

                item {
                    DetailText(
                        label = "현재 선택 여부",
                        value = if (isSelected) "선택됨" else "선택되지 않음"
                    )
                }

                item {
                    DetailText(label = "오늘 사용량", value = "${usage.requestCount}회")
                }

                item {
                    DetailText(label = "성공 / 실패", value = "${usage.successCount} / ${usage.failureCount}")
                }

                item {
                    DetailText(label = "Rate Limit 발생", value = "${usage.rateLimitCount}회")
                }

                item {
                    DetailText(label = "추정 일일 한도", value = "${item.estimatedDailyLimit}회")
                }

                item {
                    DetailText(label = "추정 잔량", value = "${estimatedRemaining}회")
                }

                if (usage.lastUsedAt.isNotBlank()) {
                    item {
                        DetailText(label = "마지막 사용", value = usage.lastUsedAt)
                    }
                }

                item {
                    DetailText(
                        label = "일시 제한 여부",
                        value = if (isBlocked) {
                            "제한 중 · $blockedUntilText 이후 재시도 가능"
                        } else {
                            "제한 없음"
                        }
                    )
                }

                item {
                    DetailText(
                        label = "태그",
                        value = item.tags.joinToString(", ").ifBlank { "없음" }
                    )
                }

                item {
                    HorizontalDivider(color = androidx.compose.ui.graphics.Color(0xFF334155))
                }

                item {
                    Text(
                        text = "무료/유료는 단정하지 않습니다. 이 화면은 실제 테스트 결과 기준으로 호출 가능 여부만 표시합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = androidx.compose.ui.graphics.Color(0xFFCBD5E1)
                    )
                }
            }
        }
    )
}

@Composable
private fun DetailText(
    label: String,
    value: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
        )

        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = androidx.compose.ui.graphics.Color(0xFFE5ECF6)
            )
        }
    }
}

@Composable
private fun SmallStatusChip(
    text: String,
    status: String
) {
    val color = getStatusColor(status)

    Surface(
        color = color.copy(alpha = 0.22f)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

private fun toKoreanStatus(status: String): String {
    return when (status) {
        NvidiaModelTestStatus.AVAILABLE -> "호출 가능"
        NvidiaModelTestStatus.FAILED -> "실패"
        NvidiaModelTestStatus.RATE_LIMITED -> "제한됨"
        NvidiaModelTestStatus.AUTH_REQUIRED -> "권한 필요"
        NvidiaModelTestStatus.UNSUPPORTED -> "지원 안 됨"
        else -> "테스트 필요"
    }
}

private fun statusToUiStatus(status: String): String {
    return when (status) {
        NvidiaModelTestStatus.AVAILABLE -> "SUCCESS"
        NvidiaModelTestStatus.RATE_LIMITED -> "RUNNING"
        NvidiaModelTestStatus.UNTESTED -> "WAITING"
        else -> "FAILED"
    }
}