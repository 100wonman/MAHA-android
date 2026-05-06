package com.maha.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.style.TextOverflow
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
        modifier = modifier.fillMaxWidth(),
        shape = conversationUnifiedCardShape(),
        colors = CardDefaults.cardColors(
            containerColor = ConversationSurfaces.cardBackground
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = ConversationBorders.defaultBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(ConversationSpacing.inputPanelPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickSettingsHeader(
                expanded = isQuickSettingsExpanded,
                summary = buildQuickSettingsSummary(
                    modeLabel = modeLabel,
                    searchEnabled = searchEnabled,
                    webSearchEnabled = webSearchEnabled,
                    webSearchFallbackEnabled = webSearchFallbackEnabled
                ),
                onClick = {
                    isQuickSettingsExpanded = !isQuickSettingsExpanded
                }
            )

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
                            fontWeight = FontWeight.SemiBold,
                            color = ConversationColors.textSecondary
                        )

                        Text(
                            text = "Worker: 추후 지원",
                            style = MaterialTheme.typography.labelSmall,
                            color = ConversationColors.textMuted
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        modeOptions.forEach { option ->
                            ModeRadioItem(
                                label = option,
                                selected = modeLabel == option,
                                onClick = {
                                    onModeChange(option)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    ToggleSettingRow(
                        title = "RAG 검색 사용",
                        description = if (searchEnabled) {
                            "앱 내부 RAG 검색 ON"
                        } else {
                            "앱 내부 RAG 검색 OFF"
                        },
                        checked = searchEnabled,
                        onCheckedChange = {
                            onToggleSearch()
                        }
                    )

                    ToggleSettingRow(
                        title = "Web Search",
                        description = if (webSearchEnabled) {
                            "외부 Web Search grounding 요청 ON"
                        } else {
                            "외부 Web Search grounding 요청 OFF"
                        },
                        checked = webSearchEnabled,
                        onCheckedChange = {
                            onToggleWebSearch()
                        }
                    )

                    if (webSearchEnabled) {
                        ToggleSettingRow(
                            title = "검색 실패 시 일반 답변",
                            description = "Web Search 실패 시 검색 없이 일반 답변을 시도합니다.",
                            checked = webSearchFallbackEnabled,
                            onCheckedChange = {
                                onToggleWebSearchFallback()
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                ConversationInputTextBox(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    modifier = Modifier.weight(1f)
                )

                SendActionButton(
                    canSend = canSend,
                    isRunning = isRunning,
                    onClick = {
                        if (canSend) {
                            keyboardController?.hide()
                            onSend()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickSettingsHeader(
    expanded: Boolean,
    summary: String,
    onClick: () -> Unit
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(ConversationShapes.buttonRadius)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = ConversationBorders.subtleBorder,
                shape = shape
            )
            .background(
                color = ConversationSurfaces.messageSystemBackground,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
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
                color = ConversationColors.textSecondary
            )

            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = ConversationColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = if (expanded) "접기" else "펼치기",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = ConversationVisualTone.ACTION.conversationTextColor()
        )
    }
}

@Composable
private fun ModeRadioItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(ConversationShapes.buttonRadius)
    val borderColor = if (selected) {
        ConversationVisualTone.SELECTED.conversationBorderColor()
    } else {
        ConversationBorders.subtleBorder
    }

    Row(
        modifier = modifier
            .heightIn(min = 34.dp)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .background(
                color = ConversationSurfaces.messageSystemBackground,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            modifier = Modifier.size(26.dp),
            colors = RadioButtonDefaults.colors(
                selectedColor = ConversationVisualTone.SELECTED.conversationTextColor(),
                unselectedColor = ConversationColors.textMuted,
                disabledSelectedColor = ConversationColors.textMuted,
                disabledUnselectedColor = ConversationColors.textMuted.copy(alpha = 0.45f)
            )
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                ConversationVisualTone.SELECTED.conversationTextColor()
            } else {
                ConversationColors.textSecondary
            },
            maxLines = 1
        )
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: () -> Unit
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(ConversationShapes.buttonRadius)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (checked) {
                    ConversationVisualTone.SELECTED.conversationBorderColor()
                } else {
                    ConversationBorders.subtleBorder
                },
                shape = shape
            )
            .background(
                color = ConversationSurfaces.messageSystemBackground,
                shape = shape
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = ConversationColors.textSecondary
            )

            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = ConversationColors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = {
                onCheckedChange()
            },
            colors = conversationSwitchColors()
        )
    }
}

@Composable
private fun ConversationInputTextBox(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(ConversationShapes.inputRadius)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .heightIn(min = 48.dp, max = 132.dp)
            .border(
                width = 1.dp,
                color = ConversationBorders.defaultBorder,
                shape = shape
            )
            .background(
                color = ConversationSurfaces.messageAssistantBackground,
                shape = shape
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = ConversationColors.textPrimary,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.18f
        ),
        minLines = 1,
        maxLines = 5,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isBlank()) {
                    Text(
                        text = "메시지를 입력하세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ConversationColors.textMuted
                    )
                }

                innerTextField()
            }
        }
    )
}

@Composable
private fun SendActionButton(
    canSend: Boolean,
    isRunning: Boolean,
    onClick: () -> Unit
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(ConversationShapes.buttonRadius)
    val borderColor = if (canSend) {
        ConversationVisualTone.ACTION.conversationBorderColor()
    } else {
        ConversationBorders.subtleBorder
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .background(
                color = ConversationSurfaces.messageSystemBackground,
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onClick,
            enabled = canSend,
            modifier = Modifier.size(44.dp)
        ) {
            Text(
                text = if (isRunning) "…" else "➤",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (canSend) {
                    ConversationVisualTone.ACTION.conversationTextColor()
                } else {
                    ConversationColors.textMuted.copy(alpha = 0.62f)
                }
            )
        }
    }
}

@Composable
private fun conversationSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = ConversationVisualTone.SELECTED.conversationTextColor(),
    checkedTrackColor = ConversationVisualTone.SELECTED.conversationBorderColor().copy(alpha = 0.34f),
    checkedBorderColor = ConversationVisualTone.SELECTED.conversationBorderColor(),
    uncheckedThumbColor = ConversationColors.textMuted,
    uncheckedTrackColor = ConversationSurfaces.messageSystemBackground,
    uncheckedBorderColor = ConversationBorders.subtleBorder,
    disabledCheckedThumbColor = ConversationColors.textMuted.copy(alpha = 0.58f),
    disabledCheckedTrackColor = ConversationBorders.subtleBorder.copy(alpha = 0.42f),
    disabledUncheckedThumbColor = ConversationColors.textMuted.copy(alpha = 0.42f),
    disabledUncheckedTrackColor = ConversationSurfaces.messageSystemBackground.copy(alpha = 0.58f)
)
