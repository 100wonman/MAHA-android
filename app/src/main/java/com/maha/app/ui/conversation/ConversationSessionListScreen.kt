package com.maha.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationSessionListScreen(
    sessions: List<ConversationSession>,
    favoriteSessionIds: List<String>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onNewConversationClick: () -> Unit,
    onSessionClick: (ConversationSession) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDeleteSession: (String) -> Unit
) {
    var menuSession by remember { mutableStateOf<ConversationSession?>(null) }
    var renameTargetSession by remember { mutableStateOf<ConversationSession?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTargetSession by remember { mutableStateOf<ConversationSession?>(null) }

    val normalizedQuery = searchQuery.trim()
    val visibleSessions = sessions
        .filter { session ->
            if (normalizedQuery.isBlank()) {
                true
            } else {
                session.title.contains(normalizedQuery, ignoreCase = true) ||
                        session.lastMessageSummary.contains(normalizedQuery, ignoreCase = true)
            }
        }
        .sortedWith(
            compareByDescending<ConversationSession> { session ->
                favoriteSessionIds.contains(session.sessionId)
            }.thenByDescending { session ->
                session.updatedAt
            }
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(onClick = onBackClick) {
                Text(
                    text = "←",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "대화",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "대화 세션 목록",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Card(
                onClick = onNewConversationClick,
                shape = conversationUnifiedCardShape(),
                colors = CardDefaults.cardColors(
                    containerColor = conversationUnifiedCardColor()
                )
            ) {
                Text(
                    text = "+ 새 대화",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = {
                Text(text = "대화 검색")
            },
            placeholder = {
                Text(text = "제목 또는 마지막 메시지 검색")
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (visibleSessions.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = conversationUnifiedCardShape(),
                colors = CardDefaults.cardColors(
                    containerColor = conversationUnifiedCardColor()
                )
            ) {
                Text(
                    text = "검색 결과가 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = visibleSessions,
                    key = { session -> session.sessionId }
                ) { session ->
                    val isFavorite = favoriteSessionIds.contains(session.sessionId)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    onSessionClick(session)
                                },
                                onLongClick = {
                                    menuSession = session
                                }
                            ),
                        shape = conversationUnifiedCardShape(),
                        colors = CardDefaults.cardColors(
                            containerColor = conversationUnifiedCardColor()
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (isFavorite) "★" else "☆",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = session.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Text(
                                text = session.lastMessageSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = session.updatedAt,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                            )
                        }
                    }
                }
            }
        }
    }

    val activeMenuSession = menuSession
    if (activeMenuSession != null) {
        AlertDialog(
            onDismissRequest = {
                menuSession = null
            },
            title = {
                Text(text = "대화방 관리")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = activeMenuSession.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "이 대화방에 적용할 작업을 선택하세요.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            renameTargetSession = activeMenuSession
                            renameText = activeMenuSession.title
                            menuSession = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "이름 변경")
                    }

                    Button(
                        onClick = {
                            onToggleFavorite(activeMenuSession.sessionId)
                            menuSession = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (favoriteSessionIds.contains(activeMenuSession.sessionId)) {
                                "즐겨찾기 해제"
                            } else {
                                "즐겨찾기 추가"
                            }
                        )
                    }

                    Button(
                        onClick = {
                            deleteTargetSession = activeMenuSession
                            menuSession = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "삭제")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        menuSession = null
                    }
                ) {
                    Text(text = "취소")
                }
            }
        )
    }

    val activeRenameSession = renameTargetSession
    if (activeRenameSession != null) {
        AlertDialog(
            onDismissRequest = {
                renameTargetSession = null
            },
            title = {
                Text(text = "대화 이름 변경")
            },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { value ->
                        renameText = value
                    },
                    singleLine = true,
                    label = {
                        Text(text = "대화 이름")
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameSession(activeRenameSession.sessionId, renameText)
                        renameTargetSession = null
                    }
                ) {
                    Text(text = "저장")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        renameTargetSession = null
                    }
                ) {
                    Text(text = "취소")
                }
            }
        )
    }

    val activeDeleteSession = deleteTargetSession
    if (activeDeleteSession != null) {
        AlertDialog(
            onDismissRequest = {
                deleteTargetSession = null
            },
            title = {
                Text(text = "대화 삭제")
            },
            text = {
                Text(text = "이 대화 세션을 삭제합니다. 저장된 session.json과 messages.jsonl도 함께 삭제됩니다.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSession(activeDeleteSession.sessionId)
                        deleteTargetSession = null
                    }
                ) {
                    Text(text = "삭제")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deleteTargetSession = null
                    }
                ) {
                    Text(text = "취소")
                }
            }
        )
    }
}
