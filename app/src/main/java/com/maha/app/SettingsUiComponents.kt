package com.maha.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
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
fun SettingsDivider(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SettingsStyleTokens.dividerColor)
    )
}

@Composable
fun SettingsInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelWidth: androidx.compose.ui.unit.Dp = 96.dp,
    maxLines: Int = 3
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = SettingsStyleTokens.mutedTextColor,
            modifier = Modifier.width(labelWidth)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = SettingsStyleTokens.bodyTextColor,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun SettingsInlineNotice(
    text: String,
    modifier: Modifier = Modifier,
    tone: SettingsChipTone = SettingsChipTone.INFO
) {
    val colors = SettingsStyleTokens.cardColors(tone)
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = colors.content,
        modifier = modifier
            .fillMaxWidth()
            .border(SettingsStyleTokens.cardBorderWidth, colors.border, RoundedCornerShape(SettingsStyleTokens.nestedCornerRadius))
            .padding(SettingsStyleTokens.compactCardPadding)
    )
}

@Composable
fun SettingsInfoPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: SettingsChipTone = SettingsChipTone.NEUTRAL,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = SettingsStyleTokens.cardColors(tone)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SettingsStyleTokens.infoPanelBackground, RoundedCornerShape(SettingsStyleTokens.nestedCornerRadius))
            .border(SettingsStyleTokens.cardBorderWidth, colors.border, RoundedCornerShape(SettingsStyleTokens.nestedCornerRadius))
            .padding(SettingsStyleTokens.compactCardPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = colors.content
            )
            SettingsDivider()
        }
        content()
    }
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
        shape = RoundedCornerShape(SettingsStyleTokens.cardCornerRadius),
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
    val background = SettingsStyleTokens.cardBackground
    val border = if (enabled) SettingsStyleTokens.actionBorderColor else SettingsStyleTokens.subtleBorderColor
    Card(
        shape = RoundedCornerShape(SettingsStyleTokens.cardCornerRadius),
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
                color = if (enabled) SettingsStyleTokens.actionTextColor else SettingsStyleTokens.disabledTextColor
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
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(SettingsStyleTokens.nestedCornerRadius),
        border = BorderStroke(
            SettingsStyleTokens.cardBorderWidth,
            if (enabled) SettingsStyleTokens.actionBorderColor else SettingsStyleTokens.subtleBorderColor
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = SettingsStyleTokens.primaryButtonBackground,
            contentColor = if (enabled) SettingsStyleTokens.actionTextColor else SettingsStyleTokens.disabledTextColor,
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
        shape = RoundedCornerShape(SettingsStyleTokens.nestedCornerRadius),
        border = BorderStroke(SettingsStyleTokens.cardBorderWidth, if (selected) SettingsStyleTokens.selectedBorderColor else SettingsStyleTokens.cardBorderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) SettingsStyleTokens.selectedButtonBackground else SettingsStyleTokens.unselectedButtonBackground,
            contentColor = when {
                !enabled -> SettingsStyleTokens.disabledTextColor
                selected -> SettingsStyleTokens.selectedTextColor
                else -> SettingsStyleTokens.mutedTextColor
            },
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
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(SettingsStyleTokens.nestedCornerRadius),
        border = BorderStroke(
            SettingsStyleTokens.cardBorderWidth,
            if (enabled) SettingsStyleTokens.dangerBorderColor else SettingsStyleTokens.subtleBorderColor
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = SettingsStyleTokens.dangerButtonBackground,
            contentColor = if (enabled) SettingsStyleTokens.dangerTextColor else SettingsStyleTokens.disabledTextColor,
            disabledContentColor = SettingsStyleTokens.disabledTextColor
        )
    ) {
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}


@Composable
fun SettingsTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            contentColor = when {
                !enabled -> SettingsStyleTokens.disabledTextColor
                danger -> SettingsStyleTokens.dangerTextColor
                else -> SettingsStyleTokens.actionTextColor
            },
            disabledContentColor = SettingsStyleTokens.disabledTextColor
        )
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SettingsRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    RadioButton(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = RadioButtonDefaults.colors(
            selectedColor = SettingsStyleTokens.selectedTextColor,
            unselectedColor = SettingsStyleTokens.mutedTextColor,
            disabledSelectedColor = SettingsStyleTokens.disabledTextColor,
            disabledUnselectedColor = SettingsStyleTokens.disabledTextColor
        )
    )
}

@Composable
fun SettingsSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = SettingsStyleTokens.switchCheckedThumbColor,
            checkedTrackColor = SettingsStyleTokens.switchCheckedTrackColor,
            checkedBorderColor = SettingsStyleTokens.switchCheckedBorderColor,
            uncheckedThumbColor = SettingsStyleTokens.switchUncheckedThumbColor,
            uncheckedTrackColor = SettingsStyleTokens.switchUncheckedTrackColor,
            uncheckedBorderColor = SettingsStyleTokens.switchUncheckedBorderColor,
            disabledCheckedThumbColor = SettingsStyleTokens.switchDisabledThumbColor,
            disabledCheckedTrackColor = SettingsStyleTokens.switchDisabledTrackColor,
            disabledCheckedBorderColor = SettingsStyleTokens.switchDisabledBorderColor,
            disabledUncheckedThumbColor = SettingsStyleTokens.switchDisabledThumbColor,
            disabledUncheckedTrackColor = SettingsStyleTokens.switchDisabledTrackColor,
            disabledUncheckedBorderColor = SettingsStyleTokens.switchDisabledBorderColor
        )
    )
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
