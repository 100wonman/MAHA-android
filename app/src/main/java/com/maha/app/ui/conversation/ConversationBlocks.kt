package com.maha.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationOutputBlockCard(
    block: ConversationOutputBlock,
    role: ConversationRole = ConversationRole.ASSISTANT,
    sentAt: String = ""
) {
    val isUserBlock = role == ConversationRole.USER
    val isLongMessage = shouldUseMessagePreview(block.content)
    val blockContainerColor = conversationUnifiedCardColor()
    val blockTextColor = MaterialTheme.colorScheme.onSurface

    var isExpanded by rememberSaveable("${block.blockId}_expanded") {
        mutableStateOf(
            when {
                isLongMessage -> false
                block.collapsed -> false
                else -> true
            }
        )
    }
    var isMenuOpen by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    val showPreview = isLongMessage && !isExpanded

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUserBlock) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier
                .then(
                    if (isUserBlock) {
                        Modifier.widthIn(max = 320.dp)
                    } else {
                        Modifier.widthIn(max = 340.dp)
                    }
                )
                .combinedClickable(
                    onClick = {
                        if (isLongMessage || block.collapsed) {
                            isExpanded = !isExpanded
                        }
                    },
                    onLongClick = {
                        isMenuOpen = true
                    }
                ),
            shape = conversationUnifiedCardShape(),
            colors = CardDefaults.cardColors(containerColor = blockContainerColor)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isUserBlock) {
                    Text(
                        text = buildConversationBlockHeader(block),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = blockTextColor.copy(alpha = 0.76f)
                    )
                }

                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = block.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = blockTextColor,
                        maxLines = if (showPreview) 5 else Int.MAX_VALUE,
                        textAlign = if (isUserBlock) TextAlign.End else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (showPreview) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            blockContainerColor.copy(alpha = 0.00f),
                                            blockContainerColor.copy(alpha = 0.86f),
                                            blockContainerColor
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Text(
                                text = "⌄",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = blockTextColor,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (isMenuOpen) {
        ConversationBlockActionDialog(
            sentAt = sentAt,
            onDismiss = {
                isMenuOpen = false
            },
            onCopy = {
                clipboardManager.setText(AnnotatedString(block.content))
                isMenuOpen = false
            },
            onTextSelect = {
                isMenuOpen = false
            },
            onEdit = {
                isMenuOpen = false
            },
            onShare = {
                isMenuOpen = false
            }
        )
    }
}

@Composable
private fun ConversationBlockActionDialog(
    sentAt: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onTextSelect: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = conversationUnifiedCardShape(),
            colors = CardDefaults.cardColors(
                containerColor = conversationUnifiedCardColor()
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = sentAt.ifBlank { "알 수 없음" },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                )

                ConversationDialogActionText(
                    text = "복사",
                    onClick = onCopy
                )

                ConversationDialogActionText(
                    text = "텍스트 선택",
                    onClick = onTextSelect
                )

                ConversationDialogActionText(
                    text = "메시지 편집",
                    onClick = onEdit
                )

                ConversationDialogActionText(
                    text = "공유",
                    onClick = onShare
                )
            }
        }
    }
}

@Composable
private fun ConversationDialogActionText(
    text: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )
    }
}

@Composable
internal fun conversationUnifiedCardColor() =
    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)

@Composable
internal fun conversationUnifiedCardShape() = RoundedCornerShape(28.dp)

internal fun buildConversationBlockTypeLabel(
    block: ConversationOutputBlock
): String {
    return when (block.type) {
        ConversationOutputBlockType.TEXT_BLOCK -> "TEXT"
        ConversationOutputBlockType.MARKDOWN_BLOCK -> "MD"
        ConversationOutputBlockType.CODE_BLOCK -> {
            if (block.language.isBlank()) {
                "CODE"
            } else {
                "CODE · ${block.language}"
            }
        }

        ConversationOutputBlockType.TABLE_BLOCK -> "TABLE"
        ConversationOutputBlockType.JSON_BLOCK -> "JSON"
        ConversationOutputBlockType.ERROR_BLOCK -> "ERROR"
        ConversationOutputBlockType.TRACE_BLOCK -> "TRACE"
        ConversationOutputBlockType.MEMORY_BLOCK -> "MEMORY"
    }
}

private fun buildConversationBlockHeader(
    block: ConversationOutputBlock
): String {
    val typeLabel = buildConversationBlockTypeLabel(block)

    return if (block.title.isBlank()) {
        typeLabel
    } else {
        "$typeLabel · ${block.title}"
    }
}

private fun shouldUseMessagePreview(
    content: String
): Boolean {
    return content.lines().size > 5 || content.length > 180
}
