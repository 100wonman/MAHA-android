package com.maha.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ModelRecommendationDialog(
    recommendations: List<ModelRecommendation>,
    onApplyClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val applicableCount = recommendations.count { it.canApply }

    AlertDialog(
        onDismissRequest = onCancelClick,
        title = {
            Text(text = "추천 모델 미리보기")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "적용 가능 Worker: $applicableCount / ${recommendations.size}")
                Text(text = "확인을 누르기 전에는 어떤 Worker도 변경되지 않습니다.")

                SelectionContainer {
                    Text(
                        text = recommendations.joinToString(separator = "\n\n") { recommendation ->
                            buildString {
                                appendLine(recommendation.agentName)
                                appendLine("현재: ${recommendation.currentProviderName} / ${recommendation.currentModelName}")
                                appendLine("추천: ${recommendation.recommendedProviderName} / ${recommendation.recommendedModelName}")
                                appendLine("점수: ${recommendation.score}")
                                appendLine("이유: ${recommendation.reason}")
                                if (recommendation.warning.isNotBlank()) {
                                    appendLine("주의: ${recommendation.warning}")
                                }
                                if (!recommendation.canApply) {
                                    appendLine("상태: 적용 불가")
                                }
                            }
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = applicableCount > 0,
                onClick = onApplyClick
            ) {
                Text(text = "전체 적용")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancelClick
            ) {
                Text(text = "취소")
            }
        }
    )
}

@Composable
fun RunAllPrecheckDialog(
    precheck: RunPrecheckResult,
    onConfirmClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val warningText = if (precheck.warnings.isEmpty()) {
        "경고 대상 모델이 없습니다."
    } else {
        precheck.warnings.joinToString(separator = "\n") { warning ->
            "- ${warning.agentName}: ${warning.providerName} / ${warning.modelName} / ${warning.status}"
        }
    }

    AlertDialog(
        onDismissRequest = onCancelClick,
        title = {
            Text(text = "Run All 실행 전 확인")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "예상 API 호출 수: ${precheck.estimatedApiCallCount}")
                Text(text = "실행 대상 Worker: ${precheck.enabledWorkerCount}")
                Text(text = "비활성 Worker: ${precheck.disabledWorkerCount}")
                Text(text = warningText)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmClick
            ) {
                Text(text = "확인 후 실행")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancelClick
            ) {
                Text(text = "취소")
            }
        }
    )
}

