package com.maha.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class SettingsChipTone {
    NEUTRAL,
    INFO,
    SUCCESS,
    WARNING,
    DANGER,
    SELECTED,
    DISABLED
}

@Composable
fun SettingsStatusChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: SettingsChipTone = SettingsChipTone.NEUTRAL
) {
    val colors = SettingsStyleTokens.chipColors(tone)
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = colors.content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(colors.background, RoundedCornerShape(SettingsStyleTokens.chipCornerRadius))
            .border(SettingsStyleTokens.cardBorderWidth, colors.border, RoundedCornerShape(SettingsStyleTokens.chipCornerRadius))
            .padding(horizontal = SettingsStyleTokens.chipHorizontalPadding, vertical = SettingsStyleTokens.chipVerticalPadding)
    )
}

@Composable
fun SettingsChipRow(
    values: List<Pair<String, SettingsChipTone>>,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        values.chunked(2).forEach { rowValues ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowValues.forEach { (label, tone) ->
                    SettingsStatusChip(text = label, tone = tone)
                }
            }
        }
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    chips: List<Pair<String, SettingsChipTone>> = emptyList(),
    tone: SettingsChipTone = SettingsChipTone.NEUTRAL,
    content: @Composable (() -> Unit)? = null
) {
    val colors = SettingsStyleTokens.cardColors(tone)

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.background),
        border = BorderStroke(SettingsStyleTokens.cardBorderWidth, colors.border),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(SettingsStyleTokens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsStyleTokens.cardSpacing)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SettingsStyleTokens.titleTextColor
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsStyleTokens.bodyTextColor
                )
            }
            SettingsChipRow(values = chips)
            content?.invoke()
        }
    }
}

@Composable
fun SettingsNavCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    chips: List<Pair<String, SettingsChipTone>> = emptyList(),
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val background = if (enabled) SettingsStyleTokens.navCardBackground else SettingsStyleTokens.disabledBackground
    val border = if (enabled) SettingsStyleTokens.infoBorderColor else SettingsStyleTokens.subtleBorderColor
    Card(
        colors = CardDefaults.cardColors(containerColor = background),
        border = BorderStroke(SettingsStyleTokens.cardBorderWidth, border),
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(SettingsStyleTokens.cardPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) SettingsStyleTokens.titleTextColor else SettingsStyleTokens.disabledTextColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) SettingsStyleTokens.bodyTextColor else SettingsStyleTokens.disabledTextColor
                )
                SettingsChipRow(values = chips)
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (enabled) SettingsStyleTokens.linkTextColor else SettingsStyleTokens.disabledTextColor
            )
        }
    }
}

@Composable
fun SettingsPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = SettingsStyleTokens.primaryButtonBackground,
            contentColor = SettingsStyleTokens.titleTextColor,
            disabledContainerColor = SettingsStyleTokens.disabledButtonBackground,
            disabledContentColor = SettingsStyleTokens.disabledTextColor
        )
    ) {
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingsSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        border = BorderStroke(SettingsStyleTokens.cardBorderWidth, if (selected) SettingsStyleTokens.selectedBorderColor else SettingsStyleTokens.cardBorderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) SettingsStyleTokens.selectedButtonBackground else SettingsStyleTokens.unselectedButtonBackground,
            contentColor = if (enabled) SettingsStyleTokens.linkTextColor else SettingsStyleTokens.disabledTextColor,
            disabledContentColor = SettingsStyleTokens.disabledTextColor
        )
    ) {
        Text(text = text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun SettingsDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = SettingsStyleTokens.dangerButtonBackground,
            contentColor = SettingsStyleTokens.titleTextColor,
            disabledContainerColor = SettingsStyleTokens.disabledButtonBackground,
            disabledContentColor = SettingsStyleTokens.disabledTextColor
        )
    ) {
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingsExpandableCard(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    chips: List<Pair<String, SettingsChipTone>> = emptyList(),
    content: @Composable () -> Unit
) {
    SettingsSectionCard(
        title = title,
        subtitle = subtitle,
        chips = chips,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            SettingsSecondaryButton(
                text = if (expanded) "상세 닫기" else "상세 보기",
                onClick = { onExpandedChange(!expanded) }
            )
        }
        if (expanded) {
            content()
        }
    }
}
