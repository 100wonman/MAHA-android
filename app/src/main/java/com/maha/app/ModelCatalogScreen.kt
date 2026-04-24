// ModelCatalogScreen.kt

package com.maha.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelCatalogScreen(
    discoveredModels: List<DiscoveredModel>,
    isSearchingModels: Boolean,
    modelSearchMessage: String,
    onSearchApiModelsClick: () -> Unit,
    onMenuClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val manualCatalog = GeminiModelType.getCatalog()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Model Catalog",
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StatusPanel(
                    title = "Usage Tracking",
                    status = "WAITING",
                    message = "표시되는 사용량과 잔량은 앱 내부 추정값입니다. 공식 quota는 Google AI Studio 기준으로 확인해야 합니다."
                )
            }

            item {
                PrimaryActionButton(
                    text = if (isSearchingModels) {
                        "Searching API Models..."
                    } else {
                        "Search API Models"
                    },
                    enabled = !isSearchingModels,
                    onClick = onSearchApiModelsClick
                )
            }

            if (modelSearchMessage.isNotBlank()) {
                item {
                    StatusPanel(
                        title = "API Model Search Result",
                        status = if (modelSearchMessage.contains("failed", ignoreCase = true)) {
                            "FAILED"
                        } else {
                            "SUCCESS"
                        },
                        message = modelSearchMessage
                    )
                }
            }

            item {
                Text(
                    text = "Manual Models",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                )
            }

            items(manualCatalog) { item ->
                ModelCatalogCard(item = item)
            }

            item {
                Text(
                    text = "Discovered API Models",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                )
            }

            if (discoveredModels.isEmpty()) {
                item {
                    EmptyInfoCard(text = "No API models discovered yet. Press Search API Models.")
                }
            } else {
                items(discoveredModels) { model ->
                    DiscoveredModelCard(model = model)
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
}

@Composable
fun ModelCatalogCard(
    item: ModelCatalogItem
) {
    val usage = ModelUsageManager.getTodayUsage(item.modelName)
    val isBlocked = ModelUsageManager.isModelBlocked(item.modelName)
    val blockedUntilText = ModelUsageManager.getBlockedUntilText(item.modelName)
    val estimatedRemaining = (item.estimatedDailyLimit - usage.requestCount).coerceAtLeast(0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color(0xFF1A2230)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
            )

            SelectionContainer {
                Text(
                    text = item.modelName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.ui.graphics.Color(0xFFD3DBE7)
                )
            }

            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = androidx.compose.ui.graphics.Color(0xFFE5ECF6)
            )

            InfoRow(label = "Stability", value = item.stabilityStatus)
            InfoRow(label = "Recommended Worker", value = item.recommendedWorker)
            InfoRow(label = "Estimated Daily Limit", value = item.estimatedDailyLimit.toString())
            InfoRow(label = "Today Requests", value = usage.requestCount.toString())
            InfoRow(label = "Today Success", value = usage.successCount.toString())
            InfoRow(label = "Today Failure", value = usage.failureCount.toString())
            InfoRow(label = "Rate Limited", value = usage.rateLimitCount.toString())
            InfoRow(label = "Estimated Remaining", value = estimatedRemaining.toString())

            if (usage.lastUsedAt.isNotBlank()) {
                InfoRow(label = "Last Used At", value = usage.lastUsedAt)
            }

            StatusPanel(
                title = "Model Status",
                status = if (isBlocked) "FAILED" else "SUCCESS",
                message = if (isBlocked) {
                    "일시 제한 상태입니다. 재시도 가능 예상 시각: $blockedUntilText"
                } else {
                    "사용 가능 상태입니다."
                }
            )
        }
    }
}

@Composable
fun DiscoveredModelCard(
    model: DiscoveredModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color(0xFF1A2230)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = model.displayName.ifBlank { model.modelName },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
            )

            SelectionContainer {
                Text(
                    text = model.modelName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.ui.graphics.Color(0xFFD3DBE7)
                )
            }

            if (model.description.isNotBlank()) {
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.ui.graphics.Color(0xFFE5ECF6)
                )
            }

            InfoRow(
                label = "generateContent",
                value = if (model.isGenerateContentSupported) "SUPPORTED" else "NOT SUPPORTED"
            )

            InfoRow(
                label = "Methods",
                value = model.supportedGenerationMethods.joinToString(", ").ifBlank { "Unknown" }
            )

            InfoRow(
                label = "Input Token Limit",
                value = model.inputTokenLimit.toString()
            )

            InfoRow(
                label = "Output Token Limit",
                value = model.outputTokenLimit.toString()
            )

            InfoRow(
                label = "Tags",
                value = model.tags.joinToString(", ")
            )

            InfoRow(
                label = "Last Fetched At",
                value = model.lastFetchedAt
            )

            StatusPanel(
                title = "Selection Status",
                status = if (model.isGenerateContentSupported) "SUCCESS" else "WAITING",
                message = if (model.isGenerateContentSupported) {
                    "이 모델은 generateContent를 지원합니다. 다음 단계에서 Worker 선택 후보에 연결할 수 있습니다."
                } else {
                    "이 모델은 현재 Worker 텍스트 생성 후보로 사용하지 않습니다."
                }
            )
        }
    }
}