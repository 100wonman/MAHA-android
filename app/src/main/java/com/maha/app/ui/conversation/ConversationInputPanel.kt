package com.maha.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private fun buildQuickSettingsSummary(
    modeLabel: String,
    searchEnabled: Boolean,
    webSearchEnabled: Boolean,
    webSearchFallbackEnabled: Boolean
): String {
    val webLabel = if (webSearchEnabled) {
        "Web ON · Fallback ${if (webSearchFallbackEnabled) "ON" else "OFF"}"
    } else {
        "Web OFF"
    }
    return "모드 $modeLabel · RAG ${if (searchEnabled) "ON" else "OFF"} · $webLabel"
}

@Composable
internal fun ConversationInputPanel(
    inputText: String,
    searchEnabled: Boolean,
    webSearchEnabled: Boolean,
    webSearchFallbackEnabled: Boolean,
    modeLabel: String,
    isRunning: Boolean,
    onInputTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onToggleSearch: () -> Unit,
    onToggleWebSearch: () -> Unit,
    onToggleWebSearchFallback: () -> Unit,
    onModeChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val modeOptions = listOf("자동", "일반", "코드", "검증")
    var isQuickSettingsExpanded by rememberSaveable { mutableStateOf(false) }
    val trimmedInput = inputText.trim()
    val canSend = trimmedInput.isNotEmpty() && !isRunning

    Card(
        modifier = modifier,
        shape = conversationUnifiedCardShape(),
        colors = CardDefaults.cardColors(
            containerColor = conversationUnifiedCardColor()
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        isQuickSettingsExpanded = !isQuickSettingsExpanded
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "빠른 설정",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                    )

                    Text(
                        text = buildQuickSettingsSummary(
                            modeLabel = modeLabel,
                            searchEnabled = searchEnabled,
                            webSearchEnabled = webSearchEnabled,
                            webSearchFallbackEnabled = webSearchFallbackEnabled
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        maxLines = 1
                    )
                }

                Text(
                    text = if (isQuickSettingsExpanded) "⌃" else "⌄",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (isQuickSettingsExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "모드 선택",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                        )

                        Text(
                            text = "Worker: 추후 지원",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        modeOptions.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .clickable {
                                        onModeChange(option)
                                    }
                                    .padding(end = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = modeLabel == option,
                                    onClick = {
                                        onModeChange(option)
                                    },
                                    modifier = Modifier.size(28.dp)
                                )

                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "RAG 검색 사용",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                            )

                            Text(
                                text = if (searchEnabled) "앱 내부 RAG 검색 ON" else "앱 내부 RAG 검색 OFF",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f)
                            )
                        }

                        Switch(
                            checked = searchEnabled,
                            onCheckedChange = {
                                onToggleSearch()
                            }
                        )
                    }


                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Web Search",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                            )

                            Text(
                                text = if (webSearchEnabled) {
                                    "외부 Web Search grounding 요청 ON"
                                } else {
                                    "외부 Web Search grounding 요청 OFF"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f)
                            )
                        }

                        Switch(
                            checked = webSearchEnabled,
                            onCheckedChange = {
                                onToggleWebSearch()
                            }
                        )
                    }

                    if (webSearchEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "검색 실패 시 일반 답변",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                                )

                                Text(
                                    text = "Web Search grounding 실패 시 검색 없이 일반 Gemini 답변을 시도합니다. 최신정보 질문에서는 부정확할 수 있습니다.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f)
                                )
                            }

                            Switch(
                                checked = webSearchFallbackEnabled,
                                onCheckedChange = {
                                    onToggleWebSearchFallback()
                                }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 132.dp)
                        .padding(vertical = 8.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.18f
                    ),
                    minLines = 1,
                    maxLines = 5,
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (inputText.isBlank()) {
                                Text(
                                    text = "메시지를 입력하세요.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                                )
                            }

                            innerTextField()
                        }
                    }
                )

                IconButton(
                    onClick = {
                        if (canSend) {
                            keyboardController?.hide()
                            onSend()
                        }
                    },
                    enabled = canSend,
                    modifier = Modifier.size(44.dp)
                ) {
                    Text(
                        text = if (isRunning) "…" else "➤",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (canSend) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
                        }
                    )
                }
            }
        }
    }
}
