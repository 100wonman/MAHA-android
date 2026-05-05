package com.maha.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal data class SettingsToneColors(
    val background: Color,
    val border: Color,
    val content: Color
)

internal object SettingsStyleTokens {
    val screenBackground = Color(0xFF050A0F)

    val cardBackground = Color(0xFF202733)
    val subCardBackground = Color(0xFF252E3B)
    val nestedCardBackground = Color(0xFF111A26)
    val navCardBackground = Color(0xFF263244)
    val selectedBackground = Color(0xFF33445C)
    val unselectedBackground = Color(0xFF1A202A)
    val disabledBackground = Color(0xFF1A202A)

    val cardBorderColor = Color(0xFF3B4556)
    val subtleBorderColor = Color(0xFF2C3340)
    val selectedBorderColor = Color(0xFF86B7FF)
    val infoBorderColor = Color(0xFF3B82F6)
    val successBorderColor = Color(0xFF22C55E)
    val warningBorderColor = Color(0xFFF59E0B)
    val dangerBorderColor = Color(0xFFEF4444)

    val primaryButtonBackground = Color(0xFF2563EB)
    val dangerButtonBackground = Color(0xFFB91C1C)
    val selectedButtonBackground = selectedBackground
    val unselectedButtonBackground = Color.Transparent
    val disabledButtonBackground = navCardBackground
    const val disabledButtonAlpha = 0.58f

    val titleTextColor = Color.White
    val bodyTextColor = Color(0xFFD0D3DA)
    val mutedTextColor = Color(0xFFB8BCC6)
    val disabledTextColor = Color(0xFF7D8796)
    val linkTextColor = Color(0xFFBFD7FF)
    val successTextColor = Color(0xFFB7F7CB)
    val warningTextColor = Color(0xFFFFD9A8)
    val dangerTextColor = Color(0xFFFFC2C2)
    val infoTextColor = Color(0xFFBFD7FF)

    val cardBorderWidth = 1.dp
    val cardCornerRadius = 16.dp
    val nestedCornerRadius = 10.dp
    val chipCornerRadius = 999.dp
    val cardPadding = 16.dp
    val compactCardPadding = 12.dp
    val cardSpacing = 10.dp
    val sectionSpacing = 14.dp
    val chipHorizontalPadding = 10.dp
    val chipVerticalPadding = 5.dp

    fun chipColors(tone: SettingsChipTone): SettingsToneColors {
        return when (tone) {
            SettingsChipTone.NEUTRAL -> SettingsToneColors(cardBackground, cardBorderColor, bodyTextColor)
            SettingsChipTone.INFO -> SettingsToneColors(Color(0xFF162A3B), infoBorderColor, infoTextColor)
            SettingsChipTone.SUCCESS -> SettingsToneColors(Color(0xFF163224), successBorderColor, successTextColor)
            SettingsChipTone.WARNING -> SettingsToneColors(Color(0xFF332B1F), warningBorderColor, warningTextColor)
            SettingsChipTone.DANGER -> SettingsToneColors(Color(0xFF3A1F24), dangerBorderColor, dangerTextColor)
            SettingsChipTone.SELECTED -> SettingsToneColors(selectedBackground, selectedBorderColor, titleTextColor)
            SettingsChipTone.DISABLED -> SettingsToneColors(disabledBackground, subtleBorderColor, disabledTextColor)
        }
    }

    fun cardColors(tone: SettingsChipTone): SettingsToneColors {
        return when (tone) {
            SettingsChipTone.WARNING -> SettingsToneColors(Color(0xFF332B1F), warningBorderColor, warningTextColor)
            SettingsChipTone.DANGER -> SettingsToneColors(Color(0xFF3A1F24), dangerBorderColor, dangerTextColor)
            SettingsChipTone.SUCCESS -> SettingsToneColors(Color(0xFF163224), successBorderColor, successTextColor)
            SettingsChipTone.INFO -> SettingsToneColors(Color(0xFF172231), infoBorderColor, infoTextColor)
            SettingsChipTone.SELECTED -> SettingsToneColors(selectedBackground, selectedBorderColor, titleTextColor)
            SettingsChipTone.DISABLED -> SettingsToneColors(disabledBackground, subtleBorderColor, disabledTextColor)
            SettingsChipTone.NEUTRAL -> SettingsToneColors(cardBackground, cardBorderColor, bodyTextColor)
        }
    }
}
