package com.maha.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal data class SettingsToneColors(
    val background: Color,
    val border: Color,
    val content: Color
)

internal object SettingsStyleTokens {
    /**
     * 색상 정책
     * - 큰 면적(card/button)은 중립 다크 surface를 사용한다.
     * - 상태색은 border/text/chip에만 약하게 사용한다.
     * - 액션 색과 선택 색을 분리해서 버튼/링크 시인성을 유지한다.
     * - base color는 선명하게 두되, 실제 UI 노출은 다크테마용 tint + alpha로 조절한다.
     * - alpha 값은 0.00f~1.00f 범위이며, 0.18f는 18% 농도를 의미한다.
     */
    private fun Color.at(percent: Float): Color = copy(alpha = percent.coerceIn(0f, 1f))

    private fun Color.tintWithWhite(percent: Float): Color {
        val ratio = percent.coerceIn(0f, 1f)
        return Color(
            red = red + (1f - red) * ratio,
            green = green + (1f - green) * ratio,
            blue = blue + (1f - blue) * ratio,
            alpha = alpha
        )
    }

    private val neutralSurfaceBase = Color.White
    private val neutralTextBase = Color.White

    /**
     * 선명한 base color.
     * 화면에 직접 강하게 쓰지 않고 action/status tint를 만들어 사용한다.
     */
    private val blueBase = Color(0xFF2563EB)
    private val greenBase = Color(0xFF16A34A)
    private val yellowBase = Color(0xFFEAB308)
    private val redBase = Color(0xFFDC2626)

    private val actionBlue = blueBase.tintWithWhite(0.34f)
    private val selectedBlue = blueBase.tintWithWhite(0.24f)
    private val infoBlue = blueBase.tintWithWhite(0.40f)
    private val successGreen = greenBase.tintWithWhite(0.30f)
    private val warningYellow = yellowBase.tintWithWhite(0.12f)
    private val dangerRed = redBase.tintWithWhite(0.36f)

    val screenBackground = Color(0xFF050A0F)

    /**
     * 대화모드 설정의 큰 카드 배경은 하나로 통일한다.
     * nav/sub/nested/selected/disabled도 별도 색을 만들지 않고 alias로 둔다.
     */
    val cardBackground = neutralSurfaceBase.at(0.082f)
    val subCardBackground = cardBackground
    val nestedCardBackground = cardBackground
    val navCardBackground = cardBackground
    val selectedBackground = cardBackground
    val unselectedBackground = Color.Transparent
    val disabledBackground = cardBackground

    val cardBorderColor = neutralSurfaceBase.at(0.115f)
    val subtleBorderColor = neutralSurfaceBase.at(0.075f)

    /** 액션/선택/상태 border는 카드 면 색이 아니라 윤곽선 중심으로만 표현한다. */
    val actionBorderColor = actionBlue.at(0.44f)
    val selectedBorderColor = selectedBlue.at(0.46f)
    val infoBorderColor = infoBlue.at(0.34f)
    val successBorderColor = successGreen.at(0.32f)
    val warningBorderColor = warningYellow.at(0.34f)
    val dangerBorderColor = dangerRed.at(0.36f)

    val primaryButtonBackground = neutralSurfaceBase.at(0.032f)
    val dangerButtonBackground = neutralSurfaceBase.at(0.03f)
    val selectedButtonBackground = neutralSurfaceBase.at(0.035f)
    val unselectedButtonBackground = Color.Transparent
    val disabledButtonBackground = Color.Transparent
    const val disabledButtonAlpha = 0.58f

    val titleTextColor = neutralTextBase.at(0.94f)
    val bodyTextColor = neutralTextBase.at(0.78f)
    val mutedTextColor = neutralTextBase.at(0.62f)
    val disabledTextColor = neutralTextBase.at(0.38f)

    val actionTextColor = actionBlue.at(0.96f)
    val selectedTextColor = selectedBlue.at(0.92f)
    val linkTextColor = actionTextColor
    val successTextColor = successGreen.at(0.82f)
    val warningTextColor = warningYellow.at(0.86f)
    val dangerTextColor = dangerRed.at(0.90f)
    val infoTextColor = infoBlue.at(0.86f)

    val neutralChipBackground = neutralSurfaceBase.at(0.045f)
    val infoChipBackground = infoBlue.at(0.085f)
    val successChipBackground = successGreen.at(0.08f)
    val warningChipBackground = warningYellow.at(0.09f)
    val dangerChipBackground = dangerRed.at(0.085f)
    val selectedChipBackground = selectedBlue.at(0.08f)
    val disabledChipBackground = neutralSurfaceBase.at(0.035f)

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
            SettingsChipTone.NEUTRAL -> SettingsToneColors(neutralChipBackground, cardBorderColor, bodyTextColor)
            SettingsChipTone.INFO -> SettingsToneColors(infoChipBackground, infoBorderColor, infoTextColor)
            SettingsChipTone.SUCCESS -> SettingsToneColors(successChipBackground, successBorderColor, successTextColor)
            SettingsChipTone.WARNING -> SettingsToneColors(warningChipBackground, warningBorderColor, warningTextColor)
            SettingsChipTone.DANGER -> SettingsToneColors(dangerChipBackground, dangerBorderColor, dangerTextColor)
            SettingsChipTone.SELECTED -> SettingsToneColors(selectedChipBackground, selectedBorderColor, selectedTextColor)
            SettingsChipTone.DISABLED -> SettingsToneColors(disabledChipBackground, subtleBorderColor, disabledTextColor)
        }
    }

    fun cardColors(tone: SettingsChipTone): SettingsToneColors {
        val border = when (tone) {
            SettingsChipTone.WARNING -> warningBorderColor
            SettingsChipTone.DANGER -> dangerBorderColor
            SettingsChipTone.SUCCESS -> successBorderColor
            SettingsChipTone.INFO -> infoBorderColor
            SettingsChipTone.SELECTED -> selectedBorderColor
            SettingsChipTone.DISABLED -> subtleBorderColor
            SettingsChipTone.NEUTRAL -> cardBorderColor
        }
        val content = when (tone) {
            SettingsChipTone.WARNING -> warningTextColor
            SettingsChipTone.DANGER -> dangerTextColor
            SettingsChipTone.SUCCESS -> successTextColor
            SettingsChipTone.INFO -> infoTextColor
            SettingsChipTone.SELECTED -> selectedTextColor
            SettingsChipTone.DISABLED -> disabledTextColor
            SettingsChipTone.NEUTRAL -> bodyTextColor
        }
        return SettingsToneColors(cardBackground, border, content)
    }
}
