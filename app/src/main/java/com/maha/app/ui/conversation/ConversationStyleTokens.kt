package com.maha.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 대화방 전용 정적 style token skeleton.
 *
 * 이 파일은 아직 기존 화면에 연결하지 않는다.
 * SettingsStyleTokens를 그대로 복사하지 않고, 대화방 본문 가독성을 우선하는
 * 시각 layer 후보만 정의한다.
 */
object ConversationStyleTokens {
    val colors: ConversationColors = ConversationColors
    val surfaces: ConversationSurfaces = ConversationSurfaces
    val borders: ConversationBorders = ConversationBorders
    val shapes: ConversationShapes = ConversationShapes
    val spacing: ConversationSpacing = ConversationSpacing
    val typography: ConversationTypography = ConversationTypography
    val message: ConversationMessageTokens = ConversationMessageTokens
    val structuredBlock: ConversationStructuredBlockTokens = ConversationStructuredBlockTokens
    val dialog: ConversationDialogTokens = ConversationDialogTokens
    val notice: ConversationNoticeTokens = ConversationNoticeTokens
}

enum class ConversationVisualTone {
    NEUTRAL,
    ACTION,
    INFO,
    SUCCESS,
    WARNING,
    DANGER,
    SELECTED
}

enum class ConversationMessageVisualRole {
    USER,
    ASSISTANT,
    SYSTEM
}

object ConversationColors {
    val textPrimary: Color = Color.White.copy(alpha = 0.92f)
    val textSecondary: Color = Color.White.copy(alpha = 0.74f)
    val textMuted: Color = Color.White.copy(alpha = 0.56f)

    val actionText: Color = Color(0xFF8EC5FF)
    val selectedText: Color = Color(0xFFAED4FF)
    val infoText: Color = Color(0xFF9DCEFF)
    val successText: Color = Color(0xFF86EFAC)
    val warningText: Color = Color(0xFFFACC15)
    val dangerText: Color = Color(0xFFFFA3A3)
}

object ConversationSurfaces {
    val screenBackground: Color = Color(0xFF080D18)
    val panelBackground: Color = Color(0xFF101827)
    // Compatibility alias for existing conversation visual helpers.
    // Keep this neutral surface stable until full ConversationUiComponents migration.
    val cardBackground: Color = panelBackground
    val messageUserBackground: Color = Color.White.copy(alpha = 0.055f)
    val messageAssistantBackground: Color = Color.White.copy(alpha = 0.038f)
    val messageSystemBackground: Color = Color.White.copy(alpha = 0.032f)
    val structuredBlockBackground: Color = Color.White.copy(alpha = 0.035f)
    val codeBlockBackground: Color = Color.Black.copy(alpha = 0.24f)
    val dialogBackground: Color = Color(0xFF101827)
    val noticeBackground: Color = Color.White.copy(alpha = 0.032f)
}

object ConversationBorders {
    val defaultBorder: Color = Color.White.copy(alpha = 0.10f)
    val subtleBorder: Color = Color.White.copy(alpha = 0.07f)
    val actionBorder: Color = ConversationColors.actionText.copy(alpha = 0.34f)
    val selectedBorder: Color = ConversationColors.selectedText.copy(alpha = 0.44f)
    val infoBorder: Color = ConversationColors.infoText.copy(alpha = 0.28f)
    val successBorder: Color = ConversationColors.successText.copy(alpha = 0.26f)
    val warningBorder: Color = ConversationColors.warningText.copy(alpha = 0.30f)
    val dangerBorder: Color = ConversationColors.dangerText.copy(alpha = 0.34f)
}

object ConversationShapes {
    val messageRadius: Dp = 18.dp
    // Compatibility alias for existing conversation visual helpers.
    val cardRadius: Dp = messageRadius
    val assistantBlockRadius: Dp = 18.dp
    val structuredBlockRadius: Dp = 16.dp
    val codeBlockRadius: Dp = 14.dp
    val inputRadius: Dp = 18.dp
    val buttonRadius: Dp = 14.dp
    val dialogRadius: Dp = 22.dp
    val noticeRadius: Dp = 14.dp
}

object ConversationSpacing {
    val screenPadding: Dp = 16.dp
    val messagePadding: Dp = 14.dp
    val messageSpacing: Dp = 10.dp
    val blockPadding: Dp = 12.dp
    val blockSpacing: Dp = 8.dp
    val inputPanelPadding: Dp = 12.dp
    val dialogPadding: Dp = 18.dp
    val noticePadding: Dp = 12.dp
}

object ConversationTypography {
    val messageTextSize: TextUnit = 15.sp
    val messageLineHeight: TextUnit = 22.sp
    val codeTextSize: TextUnit = 13.sp
    val labelTextSize: TextUnit = 12.sp
    val captionTextSize: TextUnit = 11.sp
}

object ConversationMessageTokens {
    val userBackground: Color = ConversationSurfaces.messageUserBackground
    val assistantBackground: Color = ConversationSurfaces.messageAssistantBackground
    val systemBackground: Color = ConversationSurfaces.messageSystemBackground
    val borderColor: Color = ConversationBorders.subtleBorder
    val radius: Dp = ConversationShapes.messageRadius
    val padding: Dp = ConversationSpacing.messagePadding
    val spacing: Dp = ConversationSpacing.messageSpacing
}

object ConversationStructuredBlockTokens {
    val background: Color = ConversationSurfaces.structuredBlockBackground
    val codeBackground: Color = ConversationSurfaces.codeBlockBackground
    val borderColor: Color = ConversationBorders.defaultBorder
    val radius: Dp = ConversationShapes.structuredBlockRadius
    val codeRadius: Dp = ConversationShapes.codeBlockRadius
    val padding: Dp = ConversationSpacing.blockPadding
}

object ConversationDialogTokens {
    val background: Color = ConversationSurfaces.dialogBackground
    val borderColor: Color = ConversationBorders.defaultBorder
    val radius: Dp = ConversationShapes.dialogRadius
    val padding: Dp = ConversationSpacing.dialogPadding
}

object ConversationNoticeTokens {
    val background: Color = ConversationSurfaces.noticeBackground
    val neutralBorder: Color = ConversationBorders.subtleBorder
    val infoBorder: Color = ConversationBorders.infoBorder
    val warningBorder: Color = ConversationBorders.warningBorder
    val dangerBorder: Color = ConversationBorders.dangerBorder
    val successBorder: Color = ConversationBorders.successBorder
    val radius: Dp = ConversationShapes.noticeRadius
    val padding: Dp = ConversationSpacing.noticePadding
}

fun ConversationVisualTone.conversationBorderColor(): Color {
    return when (this) {
        ConversationVisualTone.NEUTRAL -> ConversationBorders.defaultBorder
        ConversationVisualTone.ACTION -> ConversationBorders.actionBorder
        ConversationVisualTone.INFO -> ConversationBorders.infoBorder
        ConversationVisualTone.SUCCESS -> ConversationBorders.successBorder
        ConversationVisualTone.WARNING -> ConversationBorders.warningBorder
        ConversationVisualTone.DANGER -> ConversationBorders.dangerBorder
        ConversationVisualTone.SELECTED -> ConversationBorders.selectedBorder
    }
}

fun ConversationVisualTone.conversationTextColor(): Color {
    return when (this) {
        ConversationVisualTone.NEUTRAL -> ConversationColors.textPrimary
        ConversationVisualTone.ACTION -> ConversationColors.actionText
        ConversationVisualTone.INFO -> ConversationColors.infoText
        ConversationVisualTone.SUCCESS -> ConversationColors.successText
        ConversationVisualTone.WARNING -> ConversationColors.warningText
        ConversationVisualTone.DANGER -> ConversationColors.dangerText
        ConversationVisualTone.SELECTED -> ConversationColors.selectedText
    }
}

fun ConversationMessageVisualRole.conversationMessageBackground(): Color {
    return when (this) {
        ConversationMessageVisualRole.USER -> ConversationMessageTokens.userBackground
        ConversationMessageVisualRole.ASSISTANT -> ConversationMessageTokens.assistantBackground
        ConversationMessageVisualRole.SYSTEM -> ConversationMessageTokens.systemBackground
    }
}
