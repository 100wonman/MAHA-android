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
import androidx.compose.ui.graphics.Color
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

private data class SettingsToneColors(
    val background: Color,
    val border: Color,
    val content: Color
)

private fun settingsChipColors(tone: SettingsChipTone): SettingsToneColors {
    return when (tone) {
        SettingsChipTone.NEUTRAL -> SettingsToneColors(Color(0xFF202733), Color(0xFF3B4556), Color(0xFFD0D3DA))
        SettingsChipTone.INFO -> SettingsToneColors(Color(0xFF162A3B), Color(0xFF3B82F6), Color(0xFFBFD7FF))
        SettingsChipTone.SUCCESS -> SettingsToneColors(Color(0xFF163224), Color(0xFF22C55E), Color(0xFFBBF7D0))
        SettingsChipTone.WARNING -> SettingsToneColors(Color(0xFF332B1F), Color(0xFFF59E0B), Color(0xFFFFD9A8))
        SettingsChipTone.DANGER -> SettingsToneColors(Color(0xFF3A1F24), Color(0xFFEF4444), Color(0xFFFFC2C2))
        SettingsChipTone.SELECTED -> SettingsToneColors(Color(0xFF33445C), Color(0xFF86B7FF), Color.White)
        SettingsChipTone.DISABLED -> SettingsToneColors(Color(0xFF1A202A), Color(0xFF2C3340), Color(0xFF7D8796))
    }
}

@Composable
fun SettingsStatusChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: SettingsChipTone = SettingsChipTone.NEUTRAL
) {
    val colors = settingsChipColors(tone)
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = colors.content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(colors.background, RoundedCornerShape(999.dp))
            .border(1.dp, colors.border, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
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
    val borderColor = when (tone) {
        SettingsChipTone.WARNING -> Color(0xFFF59E0B)
        SettingsChipTone.DANGER -> Color(0xFFEF4444)
        SettingsChipTone.SUCCESS -> Color(0xFF22C55E)
        SettingsChipTone.INFO -> Color(0xFF3B82F6)
        else -> Color(0xFF3B4556)
    }
    val background = when (tone) {
        SettingsChipTone.WARNING -> Color(0xFF332B1F)
        SettingsChipTone.DANGER -> Color(0xFF3A1F24)
        SettingsChipTone.INFO -> Color(0xFF172231)
        else -> Color(0xFF202733)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = background),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD0D3DA)
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
    val background = if (enabled) Color(0xFF263244) else Color(0xFF1A202A)
    val border = if (enabled) Color(0xFF3B82F6) else Color(0xFF2C3340)
    Card(
        colors = CardDefaults.cardColors(containerColor = background),
        border = BorderStroke(1.dp, border),
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
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
                    color = if (enabled) Color.White else Color(0xFF7D8796)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) Color(0xFFD0D3DA) else Color(0xFF7D8796)
                )
                SettingsChipRow(values = chips)
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color(0xFFBFD7FF) else Color(0xFF7D8796)
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
            containerColor = Color(0xFF2563EB),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF263244),
            disabledContentColor = Color(0xFF7D8796)
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
        border = BorderStroke(1.dp, if (selected) Color(0xFF86B7FF) else Color(0xFF3B4556)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) Color(0xFF33445C) else Color.Transparent,
            contentColor = if (enabled) Color(0xFFBFD7FF) else Color(0xFF7D8796),
            disabledContentColor = Color(0xFF7D8796)
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
            containerColor = Color(0xFFB91C1C),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF263244),
            disabledContentColor = Color(0xFF7D8796)
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
                text = if (expanded) "접기" else "펼치기",
                onClick = { onExpandedChange(!expanded) }
            )
        }
        if (expanded) {
            content()
        }
    }
}
