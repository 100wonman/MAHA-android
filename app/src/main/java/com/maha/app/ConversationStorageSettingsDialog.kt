package com.maha.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ConversationStorageSettingsDialog(
    modeLabel: String,
    searchEnabled: Boolean,
    storageStatusText: String,
    storageLocationText: String,
    onModeSelected: (String) -> Unit,
    onSearchEnabledChange: (Boolean) -> Unit,
    onSelectStorageFolderClick: () -> Unit,
    onUseFallbackStorageClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "대화 설정")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "응답 모드",
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("일반", "자동", "정밀").forEach { mode ->
                            FilterChip(
                                selected = modeLabel == mode,
                                onClick = { onModeSelected(mode) },
                                label = { Text(text = mode) }
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "검색 사용",
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Switch(
                        checked = searchEnabled,
                        onCheckedChange = onSearchEnabledChange
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "로컬 저장소",
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "상태: $storageStatusText")
                    Text(text = "위치: $storageLocationText")
                    Button(
                        onClick = onSelectStorageFolderClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "저장 폴더 선택")
                    }
                    TextButton(
                        onClick = onUseFallbackStorageClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "기본 앱 저장소 사용")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "닫기")
            }
        }
    )
}
