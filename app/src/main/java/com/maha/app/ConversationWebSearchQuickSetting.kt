package com.maha.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * ConversationWebSearchQuickSetting
 *
 * 쉬운 설명:
 * 대화방 빠른 설정에 붙일 Web Search 토글 UI skeleton이다.
 * 실제 Google Search grounding 호출은 아직 하지 않는다.
 * ConversationRoomScreen.kt에서 기존 RAG 토글 아래에 이 Composable을 배치하면 된다.
 */
@Composable
fun ConversationWebSearchQuickSetting(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Web Search",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFD0D3DA)
            )
            Text(
                text = "외부 웹 검색 grounding용입니다. 현재는 UI skeleton이며 실제 Google Search grounding은 후속 단계에서 지원합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB8BCC6)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
