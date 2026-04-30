package com.maha.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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

@Composable
fun ConversationRoomScreen(
    session: ConversationSession,
    inputText: String,
    searchEnabled: Boolean,
    modeLabel: String,
    isRunning: Boolean,
    onInputTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onToggleSearch: () -> Unit,
    onModeChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGlobalSettings: () -> Unit,
    onEditMessage: (String, String) -> Unit,
    onAssistantEditUnsupported: () -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            ConversationInputPanel(
                inputText = inputText,
                searchEnabled = searchEnabled,
                modeLabel = modeLabel,
                isRunning = isRunning,
                onInputTextChange = onInputTextChange,
                onSend = onSend,
                onToggleSearch = onToggleSearch,
                onModeChange = onModeChange,
                onOpenSettings = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConversationHamburgerButton(
                    onClick = onOpenGlobalSettings
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    if (session.messages.isEmpty()) {
                        EmptyConversationCard()
                    }
                }

                items(session.messages) { message ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (message.role == ConversationRole.ASSISTANT) {
                            ConversationRunSummaryPanel(
                                run = session.latestRun ?: createDummyConversationRun(session.sessionId)
                            )
                        }

                        message.blocks.forEach { block ->
                            ConversationOutputBlockCard(
                                block = block,
                                role = message.role,
                                sentAt = message.createdAt,
                                canEdit = message.role == ConversationRole.USER,
                                onEditRequest = {
                                    onEditMessage(message.messageId, block.content)
                                },
                                onUnsupportedEditRequest = onAssistantEditUnsupported
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyConversationCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = conversationUnifiedCardShape(),
        colors = CardDefaults.cardColors(
            containerColor = conversationUnifiedCardColor()
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "아직 메시지가 없습니다.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "아래 입력창에 내용을 입력하면 이곳에 대화가 표시됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
        }
    }
}

@Composable
private fun ConversationInputPanel(
    inputText: String,
    searchEnabled: Boolean,
    modeLabel: String,
    isRunning: Boolean,
    onInputTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onToggleSearch: () -> Unit,
    onModeChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val modeOptions = listOf("자동", "일반", "코드", "검증")
    var isQuickSettingsExpanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = modifier,
        shape = conversationUnifiedCardShape(),
        colors = CardDefaults.cardColors(
            containerColor = conversationUnifiedCardColor()
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                Text(
                    text = "모드: $modeLabel   검색: ${if (searchEnabled) "ON" else "OFF"}   Worker: 추후",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = if (isQuickSettingsExpanded) "v" else ">",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (isQuickSettingsExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "모드 선택",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    )

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
                        Text(
                            text = "검색 사용: ${if (searchEnabled) "ON" else "OFF"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                        )

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
                        Text(
                            text = "Worker 선택",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                        )

                        Text(
                            text = "추후 지원",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp, max = 120.dp)
                        .padding(vertical = 8.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (inputText.isBlank()) {
                                Text(
                                    text = "메시지를 입력하세요.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                                )
                            }

                            innerTextField()
                        }
                    }
                )

                IconButton(
                    onClick = {
                        keyboardController?.hide()
                        onSend()
                    },
                    enabled = inputText.isNotBlank() && !isRunning,
                    modifier = Modifier.size(38.dp)
                ) {
                    Text(
                        text = "➤",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (inputText.isNotBlank()) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}
