package com.maha.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 대화방 전용 UI component skeleton.
 *
 * 기존 ConversationRoomScreen / ConversationBlocks / ConversationRunPanel에는 아직 적용하지 않는다.
 * 이 파일은 LOW~MEDIUM 위험도 visual wrapper 후보만 제공한다.
 */
@Composable
fun ConversationMessageCard(
    modifier: Modifier = Modifier,
    role: ConversationMessageVisualRole = ConversationMessageVisualRole.ASSISTANT,
    tone: ConversationVisualTone = ConversationVisualTone.NEUTRAL,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ConversationMessageTokens.radius),
        colors = CardDefaults.cardColors(
            containerColor = role.conversationMessageBackground()
        ),
        border = BorderStroke(1.dp, tone.conversationBorderColor())
    ) {
        Column(
            modifier = Modifier.padding(ConversationMessageTokens.padding),
            verticalArrangement = Arrangement.spacedBy(ConversationSpacing.blockSpacing),
            content = content
        )
    }
}

@Composable
fun ConversationAssistantBlockCard(
    modifier: Modifier = Modifier,
    tone: ConversationVisualTone = ConversationVisualTone.NEUTRAL,
    content: @Composable ColumnScope.() -> Unit
) {
    ConversationMessageCard(
        modifier = modifier,
        role = ConversationMessageVisualRole.ASSISTANT,
        tone = tone,
        content = content
    )
}

@Composable
fun ConversationMessageText(
    text: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    tone: ConversationVisualTone = ConversationVisualTone.NEUTRAL,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val textColor = when {
        muted -> ConversationColors.textMuted
        tone == ConversationVisualTone.NEUTRAL -> ConversationColors.textPrimary
        else -> tone.conversationTextColor()
    }

    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = textColor
    )
}

@Composable
fun ConversationIconActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: ConversationVisualTone = ConversationVisualTone.ACTION,
    icon: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(ConversationShapes.buttonRadius)
    val borderColor = if (enabled) {
        tone.conversationBorderColor()
    } else {
        ConversationBorders.subtleBorder
    }

    Box(
        modifier = modifier
            .border(width = 1.dp, color = borderColor, shape = shape)
            .background(color = Color.Transparent, shape = shape)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            content = icon
        )
    }
}

@Composable
fun ConversationStructuredBlockCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: ConversationVisualTone = ConversationVisualTone.NEUTRAL,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ConversationStructuredBlockTokens.radius),
        colors = CardDefaults.cardColors(
            containerColor = ConversationStructuredBlockTokens.background
        ),
        border = BorderStroke(1.dp, tone.conversationBorderColor())
    ) {
        Column(
            modifier = Modifier.padding(ConversationStructuredBlockTokens.padding),
            verticalArrangement = Arrangement.spacedBy(ConversationSpacing.blockSpacing)
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tone.conversationTextColor()
                )
            }
            content()
        }
    }
}

@Composable
fun ConversationDialogSurface(
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: ConversationVisualTone = ConversationVisualTone.NEUTRAL,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ConversationDialogTokens.radius),
        colors = CardDefaults.cardColors(
            containerColor = ConversationDialogTokens.background
        ),
        border = BorderStroke(1.dp, tone.conversationBorderColor())
    ) {
        Column(
            modifier = Modifier.padding(ConversationDialogTokens.padding),
            verticalArrangement = Arrangement.spacedBy(ConversationSpacing.blockSpacing)
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ConversationColors.textPrimary
                )
            }
            content()
        }
    }
}

@Composable
fun ConversationNoticeBlock(
    text: String,
    modifier: Modifier = Modifier,
    tone: ConversationVisualTone = ConversationVisualTone.INFO,
    title: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = tone.conversationBorderColor(),
                shape = RoundedCornerShape(ConversationNoticeTokens.radius)
            )
            .background(
                color = ConversationNoticeTokens.background,
                shape = RoundedCornerShape(ConversationNoticeTokens.radius)
            )
            .padding(ConversationNoticeTokens.padding),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = tone.conversationTextColor()
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = ConversationColors.textSecondary
        )
    }
}

@Composable
fun RowScope.ConversationActionText(
    text: String,
    modifier: Modifier = Modifier,
    tone: ConversationVisualTone = ConversationVisualTone.ACTION
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = tone.conversationTextColor()
    )
}
