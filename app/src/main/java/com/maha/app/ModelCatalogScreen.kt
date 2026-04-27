// ModelCatalogScreen.kt

package com.maha.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    selectedProviderName: String,
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
    var runningCapabilityTestType by remember { mutableStateOf("") }
    var capabilityTestMessage by remember { mutableStateOf("") }

    val safeSelectedProviderName = sanitizeCatalogProviderName(selectedProviderName)
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
            recommendedWorker = if (model.isGenerateContentSupported) "직접 확인 필요" else "Worker 선택 불가",
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
        .filter { row -> !isSelectionMode || row.providerName == safeSelectedProviderName }

    val selectableRows = allRows.filter { row ->
        val record = ModelTestManager.getRecord(context, row.providerName, row.modelName)
        row.isGenerateContentSupported && canSelectModelByTestStatus(record.status)
    }

    val visibleRows = if (selectedTabIndex == 0) selectableRows else allRows

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
                        Text("☰", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                        "현재 Worker Provider: $safeSelectedProviderName\n해당 Provider의 모델만 표시됩니다."
                    } else {
                        "무료/유료는 단정하지 않습니다. 실제 테스트 결과 기준으로 호출 가능 여부를 표시합니다."
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
                        status = if (modelSearchMessage.contains("실패") || modelSearchMessage.contains("failed", true)) "FAILED" else "SUCCESS",
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

            if (capabilityTestMessage.isNotBlank()) {
                item {
                    StatusPanel(
                        title = "기능 테스트 결과",
                        status = if (capabilityTestMessage.contains("통과")) "SUCCESS" else "WAITING",
                        message = capabilityTestMessage
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
                        text = { Text("선택 가능 (${selectableRows.size})") }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text("전체 모델 (${allRows.size})") }
                    )
                }
            }

            if (visibleRows.isEmpty()) {
                item {
                    EmptyInfoCard(text = "표시할 모델이 없습니다. API 모델 검색 또는 모델 테스트를 진행하세요.")
                }
            } else {
                items(visibleRows.size) { index ->
                    val row = visibleRows[index]
                    val metadata = ModelMetadataManager.getMetadata(context, row.providerName, row.modelName)
                    val testRecord = ModelTestManager.getRecord(context, row.providerName, row.modelName)

                    ModelSimpleRow(
                        item = row,
                        isSelected = row.providerName == safeSelectedProviderName && row.modelName == safeSelectedModelName,
                        testRecord = testRecord,
                        metadata = metadata,
                        onClick = { selectedModel = row }
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

    selectedModel?.let { currentModel ->
        val currentRecord = ModelTestManager.getRecord(context, currentModel.providerName, currentModel.modelName)
        val currentMetadata = ModelMetadataManager.getMetadata(context, currentModel.providerName, currentModel.modelName)
        val capabilityTestRecords = ModelCapabilityTestManager.getRecordsForModel(
            context = context,
            providerName = currentModel.providerName,
            modelName = currentModel.modelName
        )

        ModelDetailDialog(
            item = currentModel,
            testRecord = currentRecord,
            metadata = currentMetadata,
            capabilityTestRecords = capabilityTestRecords,
            runningCapabilityTestType = runningCapabilityTestType,
            isTesting = testingModelName == currentModel.modelName,
            isSelected = currentModel.providerName == safeSelectedProviderName && currentModel.modelName == safeSelectedModelName,
            isSelectionMode = isSelectionMode,
            onCapabilityTestClick = { testType ->
                scope.launch {
                    runningCapabilityTestType = testType
                    capabilityTestMessage = "${ModelCapabilityTestType.toDisplayName(testType)} 실행 중..."

                    val record = ModelCapabilityTestRunner.runTest(
                        context = context,
                        providerName = currentModel.providerName,
                        modelName = currentModel.modelName,
                        testType = testType
                    )

                    capabilityTestMessage =
                        "${ModelCapabilityTestType.toDisplayName(testType)}: ${ModelCapabilityTestStatus.toKorean(record.status)}"

                    runningCapabilityTestType = ""
                    selectedModel = null
                }
            },
            onTestModelClick = {
                scope.launch {
                    testingModelName = currentModel.modelName
                    testMessage = "모델 테스트 중..."

                    val record = when (currentModel.providerName) {
                        ModelProviderType.NVIDIA -> NvidiaModelProvider.testModel(currentModel.modelName)
                        ModelProviderType.GOOGLE -> GoogleModelProvider.testModel(currentModel.modelName)
                        else -> ModelTestRecord(
                            providerName = currentModel.providerName,
                            modelName = currentModel.modelName,
                            status = NvidiaModelTestStatus.UNSUPPORTED,
                            lastTestedAt = getCurrentTimeText(),
                            httpStatusCode = -1,
                            message = "지원되지 않는 Provider",
                            latencyMs = 0L
                        )
                    }

                    ModelTestManager.saveRecord(context, record)

                    testMessage = "테스트 완료: ${record.message}"
                    testingModelName = ""
                    selectedModel = null
                }
            },
            onSelectModelClick = {
                onSelectModelClick(currentModel.modelName)
                selectedModel = null
            },
            onDismiss = { selectedModel = null }
        )
    }
}

@Composable
private fun ModelSimpleRow(
    item: ModelListRowItem,
    isSelected: Boolean,
    testRecord: ModelTestRecord,
    metadata: ModelMetadata,
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
            Text(
                text = "[${item.providerName}] ${item.modelName}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
            )

            Text(
                text = buildCapabilityText(metadata.finalCapabilities),
                style = MaterialTheme.typography.bodySmall,
                color = androidx.compose.ui.graphics.Color(0xFF93C5FD)
            )

            Text(
                text = "${item.sourceType} · 오늘 ${usage.requestCount}회 · ${toKoreanStatus(testRecord.status)}",
                style = MaterialTheme.typography.bodySmall,
                color = androidx.compose.ui.graphics.Color(0xFFD3DBE7)
            )

            if (testRecord.testCount > 0) {
                Text(
                    text = "평균 ${testRecord.averageLatencyMs}ms · 성공률 ${testRecord.successRate}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = androidx.compose.ui.graphics.Color(0xFFCBD5E1)
                )
            }

            if (isSelected) {
                SmallStatusChip(text = "선택됨", status = "SUCCESS")
            }

            if (isBlocked) {
                SmallStatusChip(text = "일시 제한", status = "FAILED")
            }
        }
    }
}

@Composable
private fun ModelDetailDialog(
    item: ModelListRowItem,
    testRecord: ModelTestRecord,
    metadata: ModelMetadata,
    capabilityTestRecords: List<ModelCapabilityTestRecord>,
    runningCapabilityTestType: String,
    isTesting: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onCapabilityTestClick: (String) -> Unit,
    onTestModelClick: () -> Unit,
    onSelectModelClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val usage = ModelUsageManager.getTodayUsage(item.modelName)
    val isBlocked = ModelUsageManager.isModelBlocked(item.modelName)
    val blockedUntilText = ModelUsageManager.getBlockedUntilText(item.modelName)
    val canSelect = item.isGenerateContentSupported && canSelectModelByTestStatus(testRecord.status)
    val isCapabilityTestRunning = runningCapabilityTestType.isNotBlank()

    AlertDialog(
        containerColor = androidx.compose.ui.graphics.Color(0xFF1A2230),
        titleContentColor = androidx.compose.ui.graphics.Color(0xFFF8FAFC),
        textContentColor = androidx.compose.ui.graphics.Color(0xFFE5ECF6),
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = isSelectionMode && canSelect && !isSelected && !isCapabilityTestRunning && !isTesting,
                onClick = onSelectModelClick
            ) {
                Text(
                    text = when {
                        !isSelectionMode -> "Agent Detail에서 선택 가능"
                        isSelected -> "이미 선택됨"
                        else -> getSelectionButtonText(testRecord.status, canSelect)
                    },
                    color = androidx.compose.ui.graphics.Color(0xFF93C5FD)
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled = (item.providerName == ModelProviderType.NVIDIA || item.providerName == ModelProviderType.GOOGLE) &&
                        !isTesting &&
                        !isCapabilityTestRunning,
                onClick = onTestModelClick
            ) {
                Text(text = if (isTesting) "테스트 중..." else "이 모델 테스트")
            }
        },
        title = { Text("모델 상세 정보", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

                item { DetailText("Provider", item.providerName) }
                item { DetailText("기능 태그", buildCapabilityText(metadata.finalCapabilities)) }
                item { DetailText("신뢰도", "${metadata.confidenceLevel} / ${toKoreanConfidence(metadata.confidenceLevel)}") }
                item { DetailText("API 기반 태그", buildCapabilityText(metadata.apiCapabilities)) }
                item { DetailText("실제 테스트 기반 태그", buildCapabilityText(metadata.testedCapabilities)) }
                item { DetailText("Self-reported 기반 태그", buildCapabilityText(metadata.selfReportedCapabilities)) }
                item { DetailText("Manual 태그", buildCapabilityText(metadata.manualCapabilities)) }

                item {
                    Text(
                        text = "기능 테스트",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                    )
                }

                items(capabilityTestRecords.size) { index ->
                    CapabilityTestRow(
                        record = capabilityTestRecords[index],
                        isRunning = runningCapabilityTestType == capabilityTestRecords[index].testType,
                        isAnyCapabilityTestRunning = isCapabilityTestRunning,
                        isModelTestRunning = isTesting,
                        onRunClick = {
                            onCapabilityTestClick(capabilityTestRecords[index].testType)
                        }
                    )
                }

                item {
                    Text(
                        text = "※ 기능 테스트는 버튼을 누른 항목만 1회 실행됩니다. 전체 자동 테스트는 수행하지 않습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = androidx.compose.ui.graphics.Color(0xFFFCA5A5)
                    )
                }

                item { HorizontalDivider(color = androidx.compose.ui.graphics.Color(0xFF334155)) }

                item { DetailText("모델 특징 (참고)", buildSelfReportedSummary(metadata.selfReportedRawText)) }

                item {
                    Text(
                        text = "※ 모델이 스스로 제공한 정보로 정확하지 않을 수 있습니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = androidx.compose.ui.graphics.Color(0xFFFCA5A5)
                    )
                }

                item { HorizontalDivider(color = androidx.compose.ui.graphics.Color(0xFF334155)) }

                item { DetailText("테스트 상태", toKoreanStatus(testRecord.status)) }
                item { DetailText("테스트 메시지", testRecord.message.ifBlank { "없음" }) }
                item { DetailText("마지막 테스트", testRecord.lastTestedAt.ifBlank { "없음" }) }
                item { DetailText("최근 응답 시간", "${testRecord.latencyMs}ms") }
                item { DetailText("평균 latency", "${testRecord.averageLatencyMs}ms") }
                item { DetailText("테스트 횟수", testRecord.testCount.toString()) }
                item { DetailText("성공 횟수", testRecord.successCount.toString()) }
                item { DetailText("성공률", "${testRecord.successRate}%") }
                item { DetailText("HTTP 코드", testRecord.httpStatusCode.toString()) }

                item { DetailText("표시 이름", item.displayName) }
                item { DetailText("설명", item.description.ifBlank { "설명이 없습니다." }) }
                item {
                    DetailText(
                        "지원 메서드",
                        item.supportedGenerationMethods.joinToString(", ").ifBlank { "알 수 없음" }
                    )
                }
                item { DetailText("안정성 상태", item.stabilityStatus) }
                item { DetailText("권장 Worker", item.recommendedWorker) }
                item { DetailText("현재 선택 여부", if (isSelected) "선택됨" else "선택되지 않음") }
                item { DetailText("오늘 사용량", "${usage.requestCount}회") }
                item { DetailText("성공 / 실패", "${usage.successCount} / ${usage.failureCount}") }
                item { DetailText("Rate Limit 발생", "${usage.rateLimitCount}회") }
                item { DetailText("추정 일일 한도", "${item.estimatedDailyLimit}회") }
                item {
                    val estimatedRemaining = (item.estimatedDailyLimit - usage.requestCount).coerceAtLeast(0)
                    DetailText("추정 잔량", "${estimatedRemaining}회")
                }

                if (usage.lastUsedAt.isNotBlank()) {
                    item { DetailText("마지막 사용", usage.lastUsedAt) }
                }

                item {
                    DetailText(
                        "일시 제한 여부",
                        if (isBlocked) "제한 중 · $blockedUntilText 이후 재시도 가능" else "제한 없음"
                    )
                }

                item { DetailText("태그", item.tags.joinToString(", ").ifBlank { "없음" }) }

                item { HorizontalDivider(color = androidx.compose.ui.graphics.Color(0xFF334155)) }

                item {
                    Text(
                        text = "Self-reported 모델 정보",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                    )
                }

                item {
                    Text(
                        text = "주의: 아래 정보는 모델이 스스로 응답한 내용입니다. 신뢰 가능한 공식 스펙이 아니며 참고용으로만 사용하세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = androidx.compose.ui.graphics.Color(0xFFFCA5A5)
                    )
                }

                item { DetailText("Self Display Name", testRecord.selfReportedInfo.displayName.ifBlank { "없음" }) }
                item { DetailText("Self Model Family", testRecord.selfReportedInfo.modelFamily.ifBlank { "없음" }) }
                item { DetailText("Self Strengths", testRecord.selfReportedInfo.strengths.ifBlank { "없음" }) }
                item { DetailText("Self Limitations", testRecord.selfReportedInfo.limitations.ifBlank { "없음" }) }
                item { DetailText("Self Recommended Use", testRecord.selfReportedInfo.recommendedUse.ifBlank { "없음" }) }
            }
        }
    )
}

@Composable
private fun CapabilityTestRow(
    record: ModelCapabilityTestRecord,
    isRunning: Boolean,
    isAnyCapabilityTestRunning: Boolean,
    isModelTestRunning: Boolean,
    onRunClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF101722))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = ModelCapabilityTestType.toDisplayName(record.testType),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
            )

            DetailText("상태", ModelCapabilityTestStatus.toKorean(record.status))
            DetailText("마지막 테스트", record.testedAt.ifBlank { "없음" })
            DetailText("Latency", "${record.latencyMs}ms")
            DetailText("메시지", record.message.ifBlank { "없음" })

            if (record.sampleOutput.isNotBlank()) {
                DetailText("샘플 출력", record.sampleOutput.take(300))
            }

            TextButton(
                enabled = !isAnyCapabilityTestRunning && !isModelTestRunning,
                onClick = onRunClick
            ) {
                Text(if (isRunning) "실행 중..." else "이 기능 테스트 실행")
            }
        }
    }
}

@Composable
private fun DetailText(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
private fun SmallStatusChip(text: String, status: String) {
    val color = getStatusColor(status)

    Surface(color = color.copy(alpha = 0.22f)) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

private fun buildCapabilityText(capabilities: List<String>): String {
    return capabilities
        .filter { it.isNotBlank() }
        .distinct()
        .ifEmpty { listOf(ModelCapability.UNKNOWN) }
        .joinToString(" · ")
}

private fun buildSelfReportedSummary(rawText: String): String {
    val text = rawText.lowercase()
    val features = mutableListOf<String>()

    if (text.contains("image") || text.contains("vision")) features.add("이미지 처리 가능")
    if (text.contains("code") || text.contains("programming") || text.contains("coding")) features.add("코딩 특화 가능성")
    if (text.contains("reasoning") || text.contains("logic") || text.contains("math")) features.add("추론 특화 가능성")
    if (text.contains("summary") || text.contains("summarization")) features.add("요약 특화 가능성")
    if (text.contains("audio")) features.add("오디오 처리 가능성")
    if (text.contains("video")) features.add("영상 처리 가능성")
    if (text.contains("search") || text.contains("browse") || text.contains("web")) features.add("웹 검색 가능성")
    if (text.contains("text")) features.add("텍스트 처리 가능")

    return if (features.isEmpty()) {
        "확인된 참고 특징 없음"
    } else {
        features.joinToString(separator = "\n") { "- $it" }
    }
}

private fun toKoreanConfidence(confidenceLevel: String): String {
    return when (confidenceLevel) {
        ModelMetadataConfidence.HIGH -> "높음"
        ModelMetadataConfidence.MEDIUM_HIGH -> "중간 이상"
        ModelMetadataConfidence.MEDIUM -> "중간"
        ModelMetadataConfidence.LOW -> "낮음"
        else -> "알 수 없음"
    }
}

private fun sanitizeCatalogProviderName(providerName: String): String {
    return when (providerName) {
        ModelProviderType.GOOGLE -> ModelProviderType.GOOGLE
        ModelProviderType.NVIDIA -> ModelProviderType.NVIDIA
        ModelProviderType.DUMMY -> ModelProviderType.DUMMY
        else -> ModelProviderType.DUMMY
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

private fun canSelectModelByTestStatus(status: String): Boolean {
    return status == NvidiaModelTestStatus.AVAILABLE ||
            status == NvidiaModelTestStatus.UNTESTED ||
            status == NvidiaModelTestStatus.RATE_LIMITED
}

private fun getSelectionButtonText(status: String, canSelect: Boolean): String {
    if (canSelect) {
        return when (status) {
            NvidiaModelTestStatus.UNTESTED -> "테스트 필요 - 그래도 선택"
            NvidiaModelTestStatus.RATE_LIMITED -> "제한됨 - 그래도 선택"
            else -> "이 모델 선택"
        }
    }

    return when (status) {
        NvidiaModelTestStatus.FAILED -> "선택 불가 - 실패"
        NvidiaModelTestStatus.AUTH_REQUIRED -> "선택 불가 - 권한 필요"
        NvidiaModelTestStatus.UNSUPPORTED -> "선택 불가 - 지원 안 됨"
        else -> "선택 불가"
    }
}