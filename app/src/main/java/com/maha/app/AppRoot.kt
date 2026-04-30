package com.maha.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val workDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val conversationDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val conversationViewModel: ConversationViewModel = viewModel()

    val agentList = remember { mutableStateListOf<Agent>() }
    val runList = remember { mutableStateListOf<Run>() }
    val scenarioList = remember { mutableStateListOf<Scenario>() }
    val executionStateMap = remember { mutableStateMapOf<String, String>() }
    val discoveredModelList = remember { mutableStateListOf<DiscoveredModel>() }
    val executionHistoryLogList = remember { mutableStateListOf<ExecutionHistoryLog>() }
    val conversationSessions = conversationViewModel.conversationSessions
    val selectedConversationSessionId = conversationViewModel.selectedConversationSessionId
    val conversationInputText = conversationViewModel.inputText
    val conversationSearchEnabled = conversationViewModel.searchEnabled
    val conversationModeLabel = conversationViewModel.modeLabel

    var selectedAgentId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRunId by rememberSaveable { mutableStateOf<String?>(null) }
    var modelSelectionAgentId by rememberSaveable { mutableStateOf<String?>(null) }

    var isScenarioScreenOpen by rememberSaveable { mutableStateOf(false) }
    var isSettingsScreenOpen by rememberSaveable { mutableStateOf(false) }
    var isConversationListScreenOpen by rememberSaveable { mutableStateOf(false) }
    var conversationIsRunning by rememberSaveable { mutableStateOf(false) }
    var showConversationSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var selectedConversationSettingsPage by rememberSaveable { mutableStateOf<String?>(null) }
    var isAppStorageMigrationDialogOpen by rememberSaveable { mutableStateOf(false) }
    var editingMessageId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingText by rememberSaveable { mutableStateOf("") }
    var isWorkModeOpen by rememberSaveable { mutableStateOf(false) }
    var isModelCatalogScreenOpen by rememberSaveable { mutableStateOf(false) }
    var isExecutionLogScreenOpen by rememberSaveable { mutableStateOf(false) }

    var isDataLoaded by remember { mutableStateOf(false) }
    var isRunAllRunning by remember { mutableStateOf(false) }
    var runningAgentId by remember { mutableStateOf<String?>(null) }

    var savedGoogleApiKey by remember { mutableStateOf("") }
    var savedProvider by remember { mutableStateOf(ModelProviderType.DUMMY) }

    var isSearchingModels by remember { mutableStateOf(false) }
    var modelSearchMessage by remember { mutableStateOf("") }

    var pendingRunPrecheck by remember { mutableStateOf<RunPrecheckResult?>(null) }
    var isApplyFallbackModelDialogOpen by rememberSaveable { mutableStateOf(false) }
    var isRecommendationDialogOpen by rememberSaveable { mutableStateOf(false) }
    var pendingRecommendations by remember { mutableStateOf<List<ModelRecommendation>>(emptyList()) }

    var lastBackPressedTime by remember { mutableStateOf(0L) }

    var promptText by rememberSaveable {
        mutableStateOf("Create a simple MAHA workflow summary.")
    }

    val storageFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        val permissionFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, permissionFlags)
        }.onSuccess {
            conversationViewModel.connectSafStorage(uri)
            Toast.makeText(context, "저장 폴더가 연결되었습니다.", Toast.LENGTH_SHORT).show()
        }.onFailure { exception ->
            Toast.makeText(
                context,
                "저장 폴더 권한 유지 실패: ${exception.message ?: "알 수 없는 오류"}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        ApiKeyManager.initialize(context)
        ModelUsageManager.initialize(context)
        ModelCatalogManager.initialize(context)

        savedGoogleApiKey = ApiKeyManager.getGoogleApiKey(context)
        savedProvider = ApiKeyManager.getSelectedProvider(context)

        discoveredModelList.clear()
        discoveredModelList.addAll(ModelCatalogManager.getDiscoveredModels(context))

        executionHistoryLogList.clear()
        executionHistoryLogList.addAll(StorageManager.loadExecutionHistoryLogs(context))

        val loadedAgents = StorageManager.loadAgents(context)
        val safeAgents = if (loadedAgents.isEmpty()) {
            createDefaultAgents()
        } else {
            normalizeAgents(loadedAgents)
        }

        val loadedRuns = StorageManager.loadRuns(context)
        val safeRuns = sanitizeRunsWithAgents(
            runs = loadedRuns,
            validAgents = safeAgents
        )

        val loadedScenarios = StorageManager.loadScenarios(context)
        val safeScenarios = sanitizeScenariosWithAgents(
            scenarios = loadedScenarios,
            fallbackAgents = safeAgents
        )

        agentList.clear()
        agentList.addAll(safeAgents)

        runList.clear()
        runList.addAll(safeRuns)

        scenarioList.clear()
        scenarioList.addAll(safeScenarios)

        syncExecutionStateMap(
            agents = agentList,
            executionStateMap = executionStateMap
        )

        isRunAllRunning = false
        runningAgentId = null
        isSearchingModels = false
        modelSearchMessage = ""
        pendingRunPrecheck = null
        pendingRecommendations = emptyList()

        isDataLoaded = true
    }

    LaunchedEffect(
        isDataLoaded,
        agentList.toList(),
        runList.toList(),
        scenarioList.toList()
    ) {
        if (!isDataLoaded) return@LaunchedEffect

        val safeAgents = normalizeAgents(agentList)
        val safeRuns = sanitizeRunsWithAgents(
            runs = runList,
            validAgents = safeAgents
        )
        val safeScenarios = sanitizeScenariosWithAgents(
            scenarios = scenarioList,
            fallbackAgents = safeAgents
        )

        if (agentList.toList() != safeAgents) {
            agentList.clear()
            agentList.addAll(safeAgents)
        }

        if (runList.toList() != safeRuns) {
            runList.clear()
            runList.addAll(safeRuns)
        }

        if (scenarioList.toList() != safeScenarios) {
            scenarioList.clear()
            scenarioList.addAll(safeScenarios)
        }

        syncExecutionStateMap(
            agents = agentList,
            executionStateMap = executionStateMap
        )

        StorageManager.saveAgents(context, agentList.toList())
        StorageManager.saveRuns(context, runList.toList())
        StorageManager.saveScenarios(context, scenarioList.toList())
    }

    LaunchedEffect(agentList.toList()) {
        syncExecutionStateMap(
            agents = agentList,
            executionStateMap = executionStateMap
        )

        if (selectedAgentId != null && agentList.none { it.id == selectedAgentId }) {
            selectedAgentId = null
        }

        if (modelSelectionAgentId != null && agentList.none { it.id == modelSelectionAgentId }) {
            modelSelectionAgentId = null
        }

        if (runningAgentId != null && agentList.none { it.id == runningAgentId }) {
            runningAgentId = null
        }

        if (selectedRunId != null && runList.none { it.runId == selectedRunId }) {
            selectedRunId = null
        }
    }

    val selectedAgent = agentList.find { it.id == selectedAgentId }
    val selectedRun = runList.find { it.runId == selectedRunId }
    val modelSelectionAgent = agentList.find { it.id == modelSelectionAgentId }

    BackHandler {
        if (isRunAllRunning || runningAgentId != null || isSearchingModels) {
            Toast.makeText(context, "실행 중에는 이동할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return@BackHandler
        }

        when {
            conversationDrawerState.isOpen && selectedConversationSettingsPage != null -> {
                selectedConversationSettingsPage = null
            }

            conversationDrawerState.isOpen -> {
                scope.launch { conversationDrawerState.close() }
                selectedConversationSettingsPage = null
            }
            selectedConversationSessionId != null -> conversationViewModel.clearSelectedSession()
            isConversationListScreenOpen -> isConversationListScreenOpen = false
            isWorkModeOpen && selectedAgentId == null && selectedRunId == null && !isScenarioScreenOpen && !isSettingsScreenOpen && !isModelCatalogScreenOpen && !isExecutionLogScreenOpen -> isWorkModeOpen = false
            pendingRunPrecheck != null -> pendingRunPrecheck = null
            isRecommendationDialogOpen -> isRecommendationDialogOpen = false
            isApplyFallbackModelDialogOpen -> isApplyFallbackModelDialogOpen = false
            selectedRunId != null -> selectedRunId = null
            selectedAgentId != null -> selectedAgentId = null

            isModelCatalogScreenOpen -> {
                isModelCatalogScreenOpen = false
                if (modelSelectionAgentId != null) {
                    selectedAgentId = modelSelectionAgentId
                    modelSelectionAgentId = null
                }
            }

            isScenarioScreenOpen -> isScenarioScreenOpen = false
            isSettingsScreenOpen -> isSettingsScreenOpen = false
            isExecutionLogScreenOpen -> isExecutionLogScreenOpen = false

            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressedTime <= 2000L) {
                    (context as? ComponentActivity)?.finish()
                } else {
                    lastBackPressedTime = now
                    Toast.makeText(context, "한 번 더 누르면 종료됩니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = workDrawerState,
        gesturesEnabled = isWorkModeOpen &&
                !isConversationListScreenOpen &&
                selectedConversationSessionId == null &&
                !conversationDrawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(20.dp))

                DrawerHeader()

                NavigationDrawerItem(
                    label = {
                        DrawerItemTitle(
                            title = "Agent List",
                            subtitle = "워커 목록과 실행 관리"
                        )
                    },
                    selected = !isScenarioScreenOpen &&
                            !isSettingsScreenOpen &&
                            !isModelCatalogScreenOpen &&
                            !isExecutionLogScreenOpen &&
                            selectedAgent == null &&
                            selectedRun == null,
                    onClick = {
                        if (!isRunAllRunning && runningAgentId == null && !isSearchingModels) {
                            selectedAgentId = null
                            selectedRunId = null
                            modelSelectionAgentId = null
                            isWorkModeOpen = true
                            isConversationListScreenOpen = false
                            conversationViewModel.clearSelectedSession()
                            isScenarioScreenOpen = false
                            isSettingsScreenOpen = false
                            isModelCatalogScreenOpen = false
                            isExecutionLogScreenOpen = false
                            scope.launch { workDrawerState.close() }
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = {
                        DrawerItemTitle(
                            title = "Scenarios",
                            subtitle = "저장된 시나리오 불러오기"
                        )
                    },
                    selected = isScenarioScreenOpen,
                    onClick = {
                        if (!isRunAllRunning && runningAgentId == null && !isSearchingModels) {
                            selectedAgentId = null
                            selectedRunId = null
                            modelSelectionAgentId = null
                            isScenarioScreenOpen = true
                            isSettingsScreenOpen = false
                            isModelCatalogScreenOpen = false
                            isExecutionLogScreenOpen = false
                            scope.launch { workDrawerState.close() }
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = {
                        DrawerItemTitle(
                            title = "Models",
                            subtitle = "모델 목록과 사용량 확인"
                        )
                    },
                    selected = isModelCatalogScreenOpen && modelSelectionAgent == null,
                    onClick = {
                        if (!isRunAllRunning && runningAgentId == null && !isSearchingModels) {
                            selectedAgentId = null
                            selectedRunId = null
                            modelSelectionAgentId = null
                            isScenarioScreenOpen = false
                            isSettingsScreenOpen = false
                            isModelCatalogScreenOpen = true
                            isExecutionLogScreenOpen = false

                            discoveredModelList.clear()
                            discoveredModelList.addAll(ModelCatalogManager.getDiscoveredModels(context))

                            scope.launch { workDrawerState.close() }
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = {
                        DrawerItemTitle(
                            title = "Logs",
                            subtitle = "실행 이력 확인"
                        )
                    },
                    selected = isExecutionLogScreenOpen,
                    onClick = {
                        if (!isRunAllRunning && runningAgentId == null && !isSearchingModels) {
                            selectedAgentId = null
                            selectedRunId = null
                            modelSelectionAgentId = null
                            isScenarioScreenOpen = false
                            isSettingsScreenOpen = false
                            isModelCatalogScreenOpen = false
                            isExecutionLogScreenOpen = true

                            executionHistoryLogList.clear()
                            executionHistoryLogList.addAll(StorageManager.loadExecutionHistoryLogs(context))

                            scope.launch { workDrawerState.close() }
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = {
                        DrawerItemTitle(
                            title = "Settings",
                            subtitle = "Provider / API Key 설정"
                        )
                    },
                    selected = isSettingsScreenOpen,
                    onClick = {
                        if (!isRunAllRunning && runningAgentId == null && !isSearchingModels) {
                            selectedAgentId = null
                            selectedRunId = null
                            modelSelectionAgentId = null
                            isScenarioScreenOpen = false
                            isSettingsScreenOpen = true
                            isModelCatalogScreenOpen = false
                            isExecutionLogScreenOpen = false

                            savedGoogleApiKey = ApiKeyManager.getGoogleApiKey(context)
                            savedProvider = ApiKeyManager.getSelectedProvider(context)

                            scope.launch { workDrawerState.close() }
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                if (runList.isNotEmpty()) {
                    NavigationDrawerItem(
                        label = {
                            DrawerItemTitle(
                                title = "Latest Run",
                                subtitle = "가장 최근 실행 결과 보기"
                            )
                        },
                        selected = selectedRunId == runList.firstOrNull()?.runId,
                        onClick = {
                            if (!isRunAllRunning && runningAgentId == null && !isSearchingModels) {
                                selectedAgentId = null
                                modelSelectionAgentId = null
                                isScenarioScreenOpen = false
                                isSettingsScreenOpen = false
                                isModelCatalogScreenOpen = false
                                isExecutionLogScreenOpen = false
                                selectedRunId = runList.firstOrNull()?.runId
                                scope.launch { workDrawerState.close() }
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        ModalNavigationDrawer(
            drawerState = conversationDrawerState,
            gesturesEnabled = (isConversationListScreenOpen || selectedConversationSessionId != null) &&
                    !workDrawerState.isOpen,
            drawerContent = {
                ModalDrawerSheet {
                    ConversationGlobalSettingsScreen(
                        selectedPage = selectedConversationSettingsPage,
                        storageStatusText = conversationViewModel.storageStatusText,
                        storageLocationText = conversationViewModel.storageLocationText,
                        appSpecificSessionCount = conversationViewModel.appSpecificSessionCount,
                        canMigrateAppSpecificSessions = conversationViewModel.storageStatusText == "SAF 연결됨",
                        lastMigrationResultText = conversationViewModel.lastMigrationResultText,
                        onPageSelected = { page ->
                            selectedConversationSettingsPage = page
                        },
                        onSelectStorageFolderClick = {
                            storageFolderLauncher.launch(null)
                        },
                        onUseFallbackStorageClick = {
                            conversationViewModel.useFallbackStorage()
                            Toast.makeText(context, "기본 앱 저장소를 사용합니다.", Toast.LENGTH_SHORT).show()
                        },
                        onImportAppSpecificStorageClick = {
                            isAppStorageMigrationDialogOpen = true
                        },
                        onBackClick = {
                            if (selectedConversationSettingsPage != null) {
                                selectedConversationSettingsPage = null
                            } else {
                                scope.launch { conversationDrawerState.close() }
                            }
                        }
                    )
                }
            }
        ) {
        pendingRunPrecheck?.let { precheck ->
            RunAllPrecheckDialog(
                precheck = precheck,
                onConfirmClick = {
                    if (isRunAllRunning || runningAgentId != null || isSearchingModels) {
                        pendingRunPrecheck = null
                        return@RunAllPrecheckDialog
                    }

                    pendingRunPrecheck = null

                    scope.launch {
                        isRunAllRunning = true

                        try {
                            syncExecutionStateMap(
                                agents = agentList,
                                executionStateMap = executionStateMap
                            )

                            val newRun = ExecutionEngine.runAllAgents(
                                agents = agentList.toList(),
                                existingRuns = runList.toList(),
                                inputPrompt = promptText,
                                onStateChange = { agentId, state ->
                                    executionStateMap[agentId] = state
                                },
                                onExecutionHistoryLog = { log ->
                                    executionHistoryLogList.add(0, log)
                                    StorageManager.saveExecutionHistoryLogs(
                                        context = context,
                                        logs = executionHistoryLogList.toList()
                                    )
                                }
                            )

                            runList.removeAll { it.runId == newRun.runId }
                            runList.add(0, newRun)
                        } catch (e: Exception) {
                            agentList.forEach { agent ->
                                if (executionStateMap[agent.id] == "RUNNING") {
                                    executionStateMap[agent.id] = "FAILED"
                                }
                            }
                        } finally {
                            isRunAllRunning = false
                        }
                    }
                },
                onCancelClick = {
                    pendingRunPrecheck = null
                }
            )
        }

        if (isApplyFallbackModelDialogOpen) {
            val fallbackProvider = ApiKeyManager.getFallbackProvider(context)
            val fallbackModel = ApiKeyManager.getFallbackModel(context)

            AlertDialog(
                onDismissRequest = {
                    isApplyFallbackModelDialogOpen = false
                },
                title = {
                    Text(text = "전체 Worker 모델 변경")
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "모든 Worker의 Provider / Model을 Fallback 설정값으로 변경합니다.")
                        Text(text = "Provider: $fallbackProvider")
                        Text(text = "Model: $fallbackModel")
                        Text(text = "기존 Worker별 Provider / Model 설정은 덮어쓰기됩니다.")
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val updatedAgents = agentList.map { agent ->
                                agent.copy(
                                    providerName = fallbackProvider,
                                    modelName = fallbackModel
                                )
                            }

                            agentList.clear()
                            agentList.addAll(updatedAgents)

                            StorageManager.saveAgents(context, agentList.toList())

                            syncExecutionStateMap(
                                agents = agentList,
                                executionStateMap = executionStateMap
                            )

                            isApplyFallbackModelDialogOpen = false
                        }
                    ) {
                        Text(text = "확인 후 변경")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            isApplyFallbackModelDialogOpen = false
                        }
                    ) {
                        Text(text = "취소")
                    }
                }
            )
        }

        if (isRecommendationDialogOpen) {
            ModelRecommendationDialog(
                recommendations = pendingRecommendations,
                onApplyClick = {
                    val applicableRecommendations = pendingRecommendations.filter { it.canApply }

                    val updatedAgents = agentList.map { agent ->
                        val recommendation = applicableRecommendations.firstOrNull {
                            it.agentId == agent.id
                        }

                        if (recommendation == null) {
                            agent
                        } else {
                            agent.copy(
                                providerName = recommendation.recommendedProviderName,
                                modelName = recommendation.recommendedModelName
                            )
                        }
                    }

                    agentList.clear()
                    agentList.addAll(updatedAgents)

                    StorageManager.saveAgents(context, agentList.toList())

                    syncExecutionStateMap(
                        agents = agentList,
                        executionStateMap = executionStateMap
                    )

                    isRecommendationDialogOpen = false
                    pendingRecommendations = emptyList()
                },
                onCancelClick = {
                    isRecommendationDialogOpen = false
                    pendingRecommendations = emptyList()
                }
            )
        }

        if (showConversationSettingsDialog) {
            ConversationSettingsDialog(
                modeLabel = conversationModeLabel,
                searchEnabled = conversationSearchEnabled,
                onModeSelected = conversationViewModel::updateModeLabel,
                onSearchEnabledChange = conversationViewModel::updateSearchEnabled,
                onDismiss = {
                    showConversationSettingsDialog = false
                }
            )
        }


        editingMessageId?.let { targetMessageId ->
            ConversationMessageEditDialog(
                messageText = editingText,
                onMessageTextChange = { newText ->
                    editingText = newText
                },
                onSave = {
                    conversationViewModel.updateUserMessage(
                        messageId = targetMessageId,
                        newText = editingText
                    )

                    editingMessageId = null
                    editingText = ""
                },
                onDismiss = {
                    editingMessageId = null
                    editingText = ""
                }
            )
        }

        if (isAppStorageMigrationDialogOpen) {
            AlertDialog(
                onDismissRequest = {
                    isAppStorageMigrationDialogOpen = false
                },
                title = {
                    Text(text = "기존 앱 저장소에서 가져오기")
                },
                text = {
                    Text(
                        text = "기존 앱 저장소에 저장된 대화 세션을 현재 선택한 MAHA 폴더로 복사합니다. 기존 파일은 삭제하지 않습니다."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val result = conversationViewModel.migrateAppSpecificSessionsToSaf()
                            Toast.makeText(
                                context,
                                "복사 ${result.copiedCount}개 / 건너뜀 ${result.skippedCount}개 / 실패 ${result.failedCount}개",
                                Toast.LENGTH_SHORT
                            ).show()
                            isAppStorageMigrationDialogOpen = false
                        }
                    ) {
                        Text(text = "가져오기")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            isAppStorageMigrationDialogOpen = false
                        }
                    ) {
                        Text(text = "취소")
                    }
                }
            )
        }

        when {
            selectedConversationSessionId != null -> {
                val session = conversationSessions.find {
                    it.sessionId == selectedConversationSessionId
                }

                if (session != null) {
                    ConversationRoomScreen(
                            session = session,
                            inputText = conversationInputText,
                            searchEnabled = conversationSearchEnabled,
                            modeLabel = conversationModeLabel,
                            isRunning = conversationIsRunning,
                            onInputTextChange = conversationViewModel::updateInputText,
                            onSend = conversationViewModel::sendMessage,
                            onToggleSearch = conversationViewModel::toggleSearchEnabled,
                            onModeChange = conversationViewModel::updateModeLabel,
                            onBack = {
                                conversationViewModel.clearSelectedSession()
                            },
                            onOpenSettings = {
                                showConversationSettingsDialog = true
                            },
                            onOpenGlobalSettings = {
                                selectedConversationSettingsPage = null
                                scope.launch {
                                    workDrawerState.close()
                                    conversationDrawerState.open()
                                }
                            },
                            onEditMessage = { messageId, currentText ->
                                editingMessageId = messageId
                                editingText = currentText
                            },
                            onAssistantEditUnsupported = {
                                Toast.makeText(
                                    context,
                                    "ASSISTANT 메시지 편집은 추후 지원합니다.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )

                } else {
                    ConversationMissingSessionFallback(
                        onBackClick = {
                            conversationViewModel.clearSelectedSession()
                            isConversationListScreenOpen = true
                        }
                    )
                }
            }

            isConversationListScreenOpen -> {
                ConversationSessionListScreen(
                        sessions = conversationSessions,
                        favoriteSessionIds = conversationViewModel.favoriteSessionIds,
                        searchQuery = conversationViewModel.sessionSearchQuery,
                        onSearchQueryChange = conversationViewModel::updateSessionSearchQuery,
                        onOpenGlobalSettings = {
                            selectedConversationSettingsPage = null
                            scope.launch {
                                workDrawerState.close()
                                conversationDrawerState.open()
                            }
                        },
                        onNewConversationClick = {
                            conversationViewModel.createNewSession()
                        },
                        onSessionClick = { session ->
                            conversationViewModel.selectSession(session.sessionId)
                        },
                        onRenameSession = conversationViewModel::renameSession,
                        onToggleFavorite = conversationViewModel::toggleFavorite,
                        onDeleteSession = conversationViewModel::deleteSession
                    )

            }

            selectedRun != null -> {
                RunDetailScreen(
                    run = selectedRun,
                    onMenuClick = {
                        scope.launch { workDrawerState.open() }
                    },
                    onBackClick = {
                        selectedRunId = null
                    }
                )
            }

            isExecutionLogScreenOpen -> {
                ExecutionLogScreen(
                    logs = executionHistoryLogList,
                    onMenuClick = {
                        scope.launch { workDrawerState.open() }
                    },
                    onBackClick = {
                        isExecutionLogScreenOpen = false
                    },
                    onClearLogsClick = {
                        executionHistoryLogList.clear()
                        StorageManager.clearExecutionHistoryLogs(context)
                    }
                )
            }

            isModelCatalogScreenOpen -> {
                ModelCatalogScreen(
                    discoveredModels = discoveredModelList,
                    selectedProviderName = modelSelectionAgent?.providerName ?: savedProvider,
                    selectedModelName = modelSelectionAgent?.modelName ?: GeminiModelType.DEFAULT,
                    isSelectionMode = modelSelectionAgent != null,
                    isSearchingModels = isSearchingModels,
                    modelSearchMessage = modelSearchMessage,
                    onSearchApiModelsClick = {
                        if (isSearchingModels) return@ModelCatalogScreen

                        scope.launch {
                            isSearchingModels = true
                            modelSearchMessage = "Google + NVIDIA 모델을 검색하는 중입니다..."

                            try {
                                val googleModels = GoogleModelDiscoveryProvider.fetchModels()
                                val nvidiaModels = NvidiaModelDiscoveryProvider.fetchModels()

                                val allModels = googleModels + nvidiaModels

                                if (allModels.isEmpty()) {
                                    modelSearchMessage = "모델 검색 실패 또는 결과 없음"
                                } else {
                                    ModelCatalogManager.saveDiscoveredModels(context, allModels)

                                    discoveredModelList.clear()
                                    discoveredModelList.addAll(ModelCatalogManager.getDiscoveredModels(context))

                                    val usableCount = allModels.count { it.isGenerateContentSupported }
                                    val googleCount = googleModels.size
                                    val nvidiaCount = nvidiaModels.size

                                    modelSearchMessage =
                                        "검색 완료\n" +
                                                "Google: $googleCount\n" +
                                                "NVIDIA: $nvidiaCount\n" +
                                                "사용 가능: $usableCount"
                                }
                            } catch (exception: Exception) {
                                modelSearchMessage =
                                    "모델 검색 실패: ${exception.message ?: "알 수 없는 오류"}"
                            } finally {
                                isSearchingModels = false
                            }
                        }
                    },
                    onSelectModelClick = { modelName ->
                        val targetAgentId = modelSelectionAgentId

                        if (targetAgentId == null) {
                            modelSearchMessage = "에이전트 선택 상태가 없습니다"
                            return@ModelCatalogScreen
                        }

                        val safeModelName = modelName.trim().removePrefix("models/")

                        val index = agentList.indexOfFirst { it.id == targetAgentId }
                        if (index != -1) {
                            val targetAgent = agentList[index]

                            val updatedAgent = targetAgent.copy(
                                modelName = safeModelName
                            )

                            agentList[index] = updatedAgent
                            StorageManager.saveAgents(context, agentList.toList())

                            selectedAgentId = targetAgentId
                            modelSearchMessage = "모델 적용 완료: ${updatedAgent.providerName} / $safeModelName"
                        } else {
                            modelSearchMessage = "에이전트를 찾지 못했습니다"
                        }

                        modelSelectionAgentId = null
                        isModelCatalogScreenOpen = false
                    },
                    onMenuClick = {
                        scope.launch { workDrawerState.open() }
                    },
                    onBackClick = {
                        isModelCatalogScreenOpen = false
                        if (modelSelectionAgentId != null) {
                            selectedAgentId = modelSelectionAgentId
                            modelSelectionAgentId = null
                        }
                    }
                )
            }

            isSettingsScreenOpen -> {
                SettingsScreen(
                    savedGoogleApiKey = savedGoogleApiKey,
                    savedProvider = savedProvider,
                    onMenuClick = {
                        scope.launch { workDrawerState.open() }
                    },
                    onSaveGoogleApiKeyClick = { apiKey ->
                        ApiKeyManager.saveGoogleApiKey(context, apiKey)
                        savedGoogleApiKey = ApiKeyManager.getGoogleApiKey(context)
                    },
                    onSaveProviderClick = { provider ->
                        ApiKeyManager.saveSelectedProvider(context, provider)
                        savedProvider = ApiKeyManager.getSelectedProvider(context)
                    },
                    onBackClick = {
                        isSettingsScreenOpen = false
                    }
                )
            }

            isScenarioScreenOpen -> {
                ScenarioListScreen(
                    scenarioList = scenarioList,
                    onMenuClick = {
                        scope.launch { workDrawerState.open() }
                    },
                    onScenarioClick = { scenario ->
                        if (isRunAllRunning || runningAgentId != null || isSearchingModels) {
                            return@ScenarioListScreen
                        }

                        val safeScenarioAgents = normalizeAgents(scenario.agents)
                        val finalAgents = if (safeScenarioAgents.isEmpty()) {
                            createDefaultAgents()
                        } else {
                            safeScenarioAgents
                        }

                        agentList.clear()
                        agentList.addAll(finalAgents)

                        selectedAgentId = null
                        selectedRunId = null
                        modelSelectionAgentId = null
                        isSettingsScreenOpen = false
                        isModelCatalogScreenOpen = false
                        isExecutionLogScreenOpen = false

                        syncExecutionStateMap(
                            agents = agentList,
                            executionStateMap = executionStateMap
                        )

                        isScenarioScreenOpen = false
                    },
                    onBackClick = {
                        isScenarioScreenOpen = false
                    }
                )
            }

            selectedAgent != null -> {
                AgentDetailScreen(
                    agent = selectedAgent,
                    runList = sanitizeRunsWithAgents(
                        runs = runList.filter { run ->
                            run.results.any { it.agentId == selectedAgent.id }
                        },
                        validAgents = agentList
                    ),
                    isAnyExecutionRunning = isRunAllRunning || runningAgentId != null || isSearchingModels,
                    isCurrentAgentRunning = runningAgentId == selectedAgent.id,
                    onMenuClick = {
                        scope.launch { workDrawerState.open() }
                    },
                    onSaveClick = { updatedAgent ->
                        if (isRunAllRunning || runningAgentId != null || isSearchingModels) {
                            return@AgentDetailScreen
                        }

                        val safeUpdatedAgent = sanitizeAgent(updatedAgent)
                        val index = agentList.indexOfFirst { it.id == safeUpdatedAgent.id }

                        if (index != -1) {
                            agentList[index] = safeUpdatedAgent
                            executionStateMap[safeUpdatedAgent.id] = "WAITING"
                        }
                    },
                    onDeleteClick = { agentToDelete ->
                        if (isRunAllRunning || runningAgentId != null || isSearchingModels) {
                            return@AgentDetailScreen
                        }

                        val remainingAgents = agentList.filter { it.id != agentToDelete.id }

                        agentList.clear()
                        agentList.addAll(
                            if (remainingAgents.isEmpty()) {
                                createDefaultAgents()
                            } else {
                                normalizeAgents(remainingAgents)
                            }
                        )

                        selectedAgentId = null
                        selectedRunId = null
                        modelSelectionAgentId = null

                        syncExecutionStateMap(
                            agents = agentList,
                            executionStateMap = executionStateMap
                        )

                        val safeRuns = sanitizeRunsWithAgents(
                            runs = runList,
                            validAgents = agentList
                        )
                        runList.clear()
                        runList.addAll(safeRuns)

                        val safeScenarios = sanitizeScenariosWithAgents(
                            scenarios = scenarioList,
                            fallbackAgents = agentList
                        )
                        scenarioList.clear()
                        scenarioList.addAll(safeScenarios)
                    },
                    onRunClick = { agentToRun ->
                        if (isRunAllRunning || runningAgentId != null || isSearchingModels) {
                            return@AgentDetailScreen
                        }

                        scope.launch {
                            runningAgentId = agentToRun.id
                            executionStateMap[agentToRun.id] = "RUNNING"

                            try {
                                val run = ExecutionEngine.runSingleAgent(
                                    agent = agentToRun,
                                    validAgents = agentList.toList(),
                                    existingRuns = runList.toList(),
                                    inputPrompt = promptText,
                                    onStateChange = { agentId, state ->
                                        executionStateMap[agentId] = state
                                    },
                                    onExecutionHistoryLog = { log ->
                                        executionHistoryLogList.add(0, log)
                                        StorageManager.saveExecutionHistoryLogs(
                                            context = context,
                                            logs = executionHistoryLogList.toList()
                                        )
                                    }
                                )

                                runList.removeAll { it.runId == run.runId }
                                runList.add(0, run)
                            } catch (e: Exception) {
                                executionStateMap[agentToRun.id] = "FAILED"
                            } finally {
                                runningAgentId = null
                            }
                        }
                    },
                    onRunItemClick = { run ->
                        selectedRunId = run.runId
                    },
                    onOpenModelCatalogClick = { agentToEdit ->
                        if (isRunAllRunning || runningAgentId != null || isSearchingModels) {
                            return@AgentDetailScreen
                        }

                        val safeAgent = sanitizeAgent(agentToEdit)
                        val index = agentList.indexOfFirst { it.id == safeAgent.id }

                        if (index != -1) {
                            agentList[index] = safeAgent
                        }

                        modelSelectionAgentId = safeAgent.id
                        selectedAgentId = null
                        selectedRunId = null
                        isScenarioScreenOpen = false
                        isSettingsScreenOpen = false
                        isExecutionLogScreenOpen = false
                        isModelCatalogScreenOpen = true

                        discoveredModelList.clear()
                        discoveredModelList.addAll(ModelCatalogManager.getDiscoveredModels(context))
                    },
                    onBackClick = {
                        if (isRunAllRunning || runningAgentId != null || isSearchingModels) {
                            return@AgentDetailScreen
                        }
                        selectedAgentId = null
                    }
                )
            }

            else -> {
                if (!isWorkModeOpen) {
                    ModeSelectionScreen(
                        onWorkModeClick = {
                            isWorkModeOpen = true
                        },
                        onConversationModeClick = {
                            isConversationListScreenOpen = true
                        }
                    )
                } else {
                    AgentListScreen(
                    agentList = agentList,
                    runList = runList,
                    executionStateMap = executionStateMap,
                    isRunAllRunning = isRunAllRunning || isSearchingModels,
                    promptText = promptText,
                    onPromptTextChange = { newPrompt ->
                        promptText = newPrompt
                    },
                    onMenuClick = {
                        scope.launch { workDrawerState.open() }
                    },
                    onAgentClick = { agent ->
                        if (
                            agent.id.isNotBlank() &&
                            agentList.any { it.id == agent.id } &&
                            !isRunAllRunning &&
                            runningAgentId == null &&
                            !isSearchingModels
                        ) {
                            selectedAgentId = agent.id
                        }
                    },
                    onAddAgentClick = {
                        if (isRunAllRunning || runningAgentId != null || isSearchingModels) {
                            return@AgentListScreen
                        }

                        val newAgent = createNewAgent(existingAgents = agentList)
                        agentList.add(newAgent)
                        executionStateMap[newAgent.id] = "WAITING"
                        selectedAgentId = newAgent.id
                    },
                    onSaveScenarioClick = {
                        if (isRunAllRunning || runningAgentId != null || isSearchingModels) {
                            return@AgentListScreen
                        }

                        val safeAgents = normalizeAgents(agentList)
                        val newScenario = Scenario(
                            id = generateUniqueScenarioId(scenarioList),
                            name = "Scenario ${scenarioList.size + 1}",
                            agents = safeAgents.map { it.copy() },
                            savedAt = getCurrentTimeText()
                        )

                        scenarioList.removeAll { it.id == newScenario.id }
                        scenarioList.add(0, newScenario)
                    },
                    onOpenScenarioListClick = {
                        if (isRunAllRunning || runningAgentId != null || isSearchingModels) {
                            return@AgentListScreen
                        }
                        isScenarioScreenOpen = true
                        isSettingsScreenOpen = false
                        isModelCatalogScreenOpen = false
                        isExecutionLogScreenOpen = false
                    },
                    onApplyGemini31FlashLiteToAllClick = {
                        if (isRunAllRunning || runningAgentId != null || isSearchingModels) {
                            return@AgentListScreen
                        }

                        isApplyFallbackModelDialogOpen = true
                    },
                    onShowModelRecommendationsClick = {
                        if (isRunAllRunning || runningAgentId != null || isSearchingModels) {
                            return@AgentListScreen
                        }

                        val recommendations = ModelRecommendationManager.buildRecommendations(
                            agents = agentList.toList(),
                            logs = executionHistoryLogList.toList(),
                            testRecords = ModelTestManager.getAllRecords(context)
                        )

                        pendingRecommendations = recommendations
                        isRecommendationDialogOpen = true
                    },
                    onMoveUpClick = { agent ->
                        if (isRunAllRunning || runningAgentId != null || isSearchingModels) {
                            return@AgentListScreen
                        }
                        moveAgentUp(
                            targetAgent = agent,
                            agentList = agentList
                        )
                    },
                    onMoveDownClick = { agent ->
                        if (isRunAllRunning || runningAgentId != null || isSearchingModels) {
                            return@AgentListScreen
                        }
                        moveAgentDown(
                            targetAgent = agent,
                            agentList = agentList
                        )
                    },
                    onRunItemClick = { run ->
                        if (isRunAllRunning || runningAgentId != null || isSearchingModels) {
                            return@AgentListScreen
                        }
                        selectedRunId = run.runId
                    },
                    onRunAllClick = {
                        if (isRunAllRunning || runningAgentId != null || isSearchingModels) {
                            return@AgentListScreen
                        }

                        pendingRunPrecheck = RunPrecheckManager.buildRunAllPrecheck(
                            context = context,
                            agents = agentList.toList(),
                            fallbackProviderName = savedProvider
                        )
                    }
                )
                }
            }
        }
        }
    }
}


@Composable
fun ConversationMissingSessionFallback(
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1020))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "대화 세션을 찾을 수 없습니다.",
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "뒤로 가기를 눌러 세션 목록으로 돌아가세요.",
            color = Color(0xFF94A3B8)
        )

        Button(
            onClick = onBackClick
        ) {
            Text(text = "세션 목록으로")
        }
    }
}

internal fun createDefaultAgents(): List<Agent> {
    return listOf(
        Agent(
            id = "agent_001",
            name = "Planner",
            description = "작업 순서를 먼저 정리하는 에이전트",
            status = "Enabled",
            inputFormat = "User Request",
            outputFormat = "Plan Text",
            isEnabled = true,
            providerName = ModelProviderType.GOOGLE,
            modelName = GeminiModelType.FLASH
        ),
        Agent(
            id = "agent_002",
            name = "Researcher",
            description = "필요한 정보를 조사하고 정리하는 에이전트",
            status = "Enabled",
            inputFormat = "Plan Text",
            outputFormat = "Research Notes",
            isEnabled = true,
            providerName = ModelProviderType.GOOGLE,
            modelName = GeminiModelType.FLASH
        ),
        Agent(
            id = "agent_003",
            name = "Writer",
            description = "최종 결과를 문장 형태로 작성하는 에이전트",
            status = "Enabled",
            inputFormat = "Research Notes",
            outputFormat = "Final Answer",
            isEnabled = true,
            providerName = ModelProviderType.GOOGLE,
            modelName = GeminiModelType.FLASH_LITE
        )
    )
}

internal fun createNewAgent(existingAgents: List<Agent>): Agent {
    return sanitizeAgent(
        Agent(
            id = generateUniqueAgentId(existingAgents),
            name = "New Agent",
            description = "새 워커 설명",
            status = "Enabled",
            inputFormat = "Input Text",
            outputFormat = "Output Text",
            isEnabled = true,
            providerName = ModelProviderType.DUMMY,
            modelName = "dummy"
        )
    )
}

internal fun generateUniqueAgentId(existingAgents: List<Agent>): String {
    val existingIds = existingAgents.map { it.id }.toSet()
    var index = System.currentTimeMillis()
    var candidate = "agent_$index"

    while (candidate in existingIds) {
        index += 1
        candidate = "agent_$index"
    }

    return candidate
}

internal fun generateUniqueScenarioId(existingScenarios: List<Scenario>): String {
    val existingIds = existingScenarios.map { it.id }.toSet()
    var index = System.currentTimeMillis()
    var candidate = "scenario_$index"

    while (candidate in existingIds) {
        index += 1
        candidate = "scenario_$index"
    }

    return candidate
}

internal fun normalizeAgents(source: List<Agent>): List<Agent> {
    return source
        .map { sanitizeAgent(it) }
        .filter { it.id.isNotBlank() }
        .distinctBy { it.id }
}

internal fun sanitizeAgent(agent: Agent): Agent {
    val safeId = agent.id.ifBlank { "agent_${System.currentTimeMillis()}" }
    val safeName = agent.name.ifBlank { "Unnamed Agent" }
    val safeDescription = agent.description.ifBlank { "설명이 없습니다." }
    val safeStatus = agent.status.ifBlank { "Enabled" }
    val safeInputFormat = agent.inputFormat.ifBlank { "Input Text" }
    val safeOutputFormat = agent.outputFormat.ifBlank { "Output Text" }
    val safeProviderName = sanitizeProviderName(agent.providerName)
    val safeModelName = sanitizeModelForProviderInMain(
        providerName = safeProviderName,
        modelName = agent.modelName
    )

    return agent.copy(
        id = safeId,
        name = safeName,
        description = safeDescription,
        status = safeStatus,
        inputFormat = safeInputFormat,
        outputFormat = safeOutputFormat,
        providerName = safeProviderName,
        modelName = safeModelName
    )
}

internal fun sanitizeProviderName(providerName: String): String {
    return when (providerName) {
        ModelProviderType.GOOGLE -> ModelProviderType.GOOGLE
        ModelProviderType.NVIDIA -> ModelProviderType.NVIDIA
        ModelProviderType.DUMMY -> ModelProviderType.DUMMY
        else -> ModelProviderType.DUMMY
    }
}

internal fun sanitizeModelForProviderInMain(
    providerName: String,
    modelName: String
): String {
    val safeModelName = modelName.trim().removePrefix("models/")

    return when (providerName) {
        ModelProviderType.GOOGLE -> {
            if (
                safeModelName.startsWith("gemini") ||
                safeModelName.startsWith("gemma")
            ) {
                GeminiModelType.sanitize(safeModelName)
            } else {
                GeminiModelType.DEFAULT
            }
        }

        ModelProviderType.NVIDIA -> {
            if (safeModelName.contains("/")) {
                safeModelName
            } else {
                "meta/llama-3.1-8b-instruct"
            }
        }

        else -> "dummy"
    }
}

internal fun sanitizeRunResult(result: RunResult): RunResult? {
    val safeAgentId = result.agentId.ifBlank { return null }

    return result.copy(
        agentId = safeAgentId,
        agentName = result.agentName.ifBlank { "Unknown Agent" },
        status = result.status.ifBlank { "UNKNOWN" },
        inputText = result.inputText.ifBlank { "(empty input)" },
        outputText = result.outputText.ifBlank { "(empty output)" },
        timestamp = result.timestamp.ifBlank { getCurrentTimeText() },
        order = if (result.order <= 0) 1 else result.order
    )
}

internal fun sanitizeRunWithAgents(
    run: Run,
    validAgents: List<Agent>
): Run? {
    val validAgentMap = normalizeAgents(validAgents).associateBy { it.id }
    val safeRunId = run.runId.ifBlank { return null }
    val safeTitle = run.title.ifBlank { "Untitled Run" }
    val safeTimestamp = run.timestamp.ifBlank { getCurrentTimeText() }

    val safeResults = run.results
        .mapNotNull { sanitizeRunResult(it) }
        .filter { it.agentId in validAgentMap.keys }
        .map { result ->
            val matchedAgent = validAgentMap[result.agentId]
            if (matchedAgent != null) {
                result.copy(agentName = matchedAgent.name)
            } else {
                result
            }
        }
        .sortedBy { it.order }

    val safeLogs = run.logs.map { log ->
        ExecutionLog(
            message = log.message.ifBlank { "(empty log)" },
            timestamp = log.timestamp.ifBlank { getCurrentTimeText() }
        )
    }

    return Run(
        runId = safeRunId,
        title = safeTitle,
        timestamp = safeTimestamp,
        results = safeResults,
        logs = safeLogs
    )
}

internal fun sanitizeRunsWithAgents(
    runs: List<Run>,
    validAgents: List<Agent>
): List<Run> {
    return runs
        .mapNotNull { sanitizeRunWithAgents(it, validAgents) }
        .distinctBy { it.runId }
}

internal fun sanitizeScenariosWithAgents(
    scenarios: List<Scenario>,
    fallbackAgents: List<Agent>
): List<Scenario> {
    val normalizedFallbackAgents = normalizeAgents(fallbackAgents)

    return scenarios
        .mapNotNull { scenario ->
            val safeId = scenario.id.ifBlank { return@mapNotNull null }
            val safeName = scenario.name.ifBlank { "Unnamed Scenario" }
            val safeAgents = normalizeAgents(scenario.agents)

            Scenario(
                id = safeId,
                name = safeName,
                agents = if (safeAgents.isEmpty()) normalizedFallbackAgents else safeAgents,
                savedAt = scenario.savedAt.ifBlank { getCurrentTimeText() }
            )
        }
        .distinctBy { it.id }
}

internal fun syncExecutionStateMap(
    agents: List<Agent>,
    executionStateMap: MutableMap<String, String>
) {
    val validIds = agents.map { it.id }.toSet()

    val keysToRemove = executionStateMap.keys.filter { it !in validIds }
    keysToRemove.forEach { executionStateMap.remove(it) }

    agents.forEach { agent ->
        val currentState = executionStateMap[agent.id]
        if (currentState.isNullOrBlank()) {
            executionStateMap[agent.id] = "WAITING"
        }
    }
}

internal fun moveAgentUp(
    targetAgent: Agent,
    agentList: MutableList<Agent>
) {
    val index = agentList.indexOfFirst { it.id == targetAgent.id }
    if (index <= 0) return

    val current = agentList[index]
    val previous = agentList[index - 1]
    agentList[index - 1] = current
    agentList[index] = previous
}

internal fun moveAgentDown(
    targetAgent: Agent,
    agentList: MutableList<Agent>
) {
    val index = agentList.indexOfFirst { it.id == targetAgent.id }
    if (index == -1 || index >= agentList.lastIndex) return

    val current = agentList[index]
    val next = agentList[index + 1]
    agentList[index + 1] = current
    agentList[index] = next
}

fun getCurrentTimeText(): String {
    return SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss",
        Locale.getDefault()
    ).format(Date())
}

fun buildDummyOutput(
    agentName: String,
    stepNumber: Int,
    input: String
): String {
    return "Step $stepNumber - $agentName output based on: $input"
}


@Composable
private fun ConversationGlobalSettingsScreen(
    selectedPage: String?,
    storageStatusText: String,
    storageLocationText: String,
    appSpecificSessionCount: Int,
    canMigrateAppSpecificSessions: Boolean,
    lastMigrationResultText: String,
    onPageSelected: (String) -> Unit,
    onSelectStorageFolderClick: () -> Unit,
    onUseFallbackStorageClick: () -> Unit,
    onImportAppSpecificStorageClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050A0F))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        TextButton(onClick = onBackClick) {
            Text(text = "←", color = Color.White)
        }

        if (selectedPage == null) {
            Text(
                text = "대화모드 설정",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "전역 설정 메인",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFB8BCC6)
            )

            ConversationGlobalSettingsCard(
                title = "일반 설정",
                subtitle = "테마, 폰트, 색상 설정",
                onClick = { onPageSelected("general") }
            )

            ConversationGlobalSettingsCard(
                title = "대화 설정",
                subtitle = "모드, 검색, Worker 기본값",
                onClick = { onPageSelected("conversation") }
            )

            ConversationGlobalSettingsCard(
                title = "출력 블록 설정",
                subtitle = "블록 표시, 복사, 접기 정책",
                onClick = { onPageSelected("output") }
            )

            ConversationGlobalSettingsCard(
                title = "메모리 / RAG",
                subtitle = "후속 단계에서 연결 예정",
                onClick = { onPageSelected("rag") }
            )
        } else {
            Text(
                text = when (selectedPage) {
                    "conversation" -> "대화 설정"
                    "general" -> "일반 설정"
                    "output" -> "출력 블록 설정"
                    "rag" -> "메모리 / RAG"
                    else -> "대화 설정"
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "상세 설정 placeholder",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFB8BCC6)
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF3A3F49)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = when (selectedPage) {
                            "conversation" -> "대화 설정 상세 페이지"
                            "general" -> "일반 설정 상세 페이지"
                            "output" -> "출력 블록 설정 상세 페이지"
                            "rag" -> "메모리 / RAG 상세 페이지"
                            else -> "대화 설정 상세 페이지"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Text(
                        text = "이번 단계에서는 2단 슬라이딩 구조 확인용 placeholder입니다. 실제 설정 적용은 다음 단계에서 연결합니다.",
                        color = Color(0xFFD0D3DA)
                    )
                }
            }

            if (selectedPage == "rag") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF3A3F49)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "로컬 저장소",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = "상태: $storageStatusText",
                            color = Color(0xFFD0D3DA)
                        )

                        Text(
                            text = "위치: $storageLocationText",
                            color = Color(0xFFD0D3DA)
                        )

                        Text(
                            text = "기존 앱 저장소 세션: ${appSpecificSessionCount}개",
                            color = Color(0xFFD0D3DA)
                        )

                        if (lastMigrationResultText.isNotBlank()) {
                            Text(
                                text = "마이그레이션 결과: $lastMigrationResultText",
                                color = Color(0xFFD0D3DA)
                            )
                        }

                        Button(
                            onClick = onSelectStorageFolderClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "저장 폴더 선택")
                        }

                        if (canMigrateAppSpecificSessions) {
                            Button(
                                onClick = onImportAppSpecificStorageClick,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = "기존 앱 저장소에서 가져오기")
                            }
                        } else {
                            Button(
                                onClick = onImportAppSpecificStorageClick,
                                enabled = false,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = "기존 앱 저장소에서 가져오기")
                            }

                            Text(
                                text = "SAF 저장소 연결 후 사용할 수 있습니다.",
                                color = Color(0xFFD0D3DA)
                            )
                        }

                        TextButton(
                            onClick = onUseFallbackStorageClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "기본 앱 저장소 사용")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationGlobalSettingsCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF3A3F49)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFD0D3DA)
            )
        }
    }
}
