// MainActivity.kt

package com.maha.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme(
                colorScheme = mahaDarkColorScheme()
            ) {
                MAHAApp()
            }
        }
    }
}

@Composable
fun MAHAApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val agentList = remember { mutableStateListOf<Agent>() }
    val runList = remember { mutableStateListOf<Run>() }
    val scenarioList = remember { mutableStateListOf<Scenario>() }
    val executionStateMap = remember { mutableStateMapOf<String, String>() }
    val discoveredModelList = remember { mutableStateListOf<DiscoveredModel>() }
    val executionHistoryLogList = remember { mutableStateListOf<ExecutionHistoryLog>() }
    val conversationSessionList = remember { mutableStateListOf<ConversationSession>() }

    var selectedAgentId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRunId by rememberSaveable { mutableStateOf<String?>(null) }
    var modelSelectionAgentId by rememberSaveable { mutableStateOf<String?>(null) }

    var isScenarioScreenOpen by rememberSaveable { mutableStateOf(false) }
    var isSettingsScreenOpen by rememberSaveable { mutableStateOf(false) }
    var isConversationListScreenOpen by rememberSaveable { mutableStateOf(false) }
    var selectedConversationSessionId by rememberSaveable { mutableStateOf<String?>(null) }
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

        conversationSessionList.clear()
        conversationSessionList.addAll(createDummyConversationSessions())

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
            selectedConversationSessionId != null -> selectedConversationSessionId = null
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
        drawerState = drawerState,
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
                            selectedConversationSessionId = null
                            isScenarioScreenOpen = false
                            isSettingsScreenOpen = false
                            isModelCatalogScreenOpen = false
                            isExecutionLogScreenOpen = false
                            scope.launch { drawerState.close() }
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
                            scope.launch { drawerState.close() }
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

                            scope.launch { drawerState.close() }
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

                            scope.launch { drawerState.close() }
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

                            scope.launch { drawerState.close() }
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
                                scope.launch { drawerState.close() }
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
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

        when {
            selectedConversationSessionId != null -> {
                val session = conversationSessionList.find {
                    it.sessionId == selectedConversationSessionId
                }

                if (session != null) {
                    ConversationRoomScreen(
                        session = session,
                        onBackClick = {
                            selectedConversationSessionId = null
                        },
                        onSendClick = { userInput ->
                            val nowText = getCurrentTimeText()
                            val timestamp = System.currentTimeMillis()

                            val userMessage = ConversationMessage(
                                messageId = "message_${timestamp}_user",
                                sessionId = session.sessionId,
                                role = ConversationRole.USER,
                                createdAt = nowText,
                                blocks = listOf(
                                    ConversationOutputBlock(
                                        blockId = "block_${timestamp}_user_text",
                                        type = ConversationOutputBlockType.TEXT_BLOCK,
                                        title = "사용자 입력",
                                        content = userInput,
                                        collapsed = true
                                    )
                                )
                            )

                            val assistantMessage = ConversationMessage(
                                messageId = "message_${timestamp}_assistant",
                                sessionId = session.sessionId,
                                role = ConversationRole.ASSISTANT,
                                createdAt = nowText,
                                linkedRunId = "conversation_run_${timestamp}",
                                blocks = listOf(
                                    ConversationOutputBlock(
                                        blockId = "block_${timestamp}_assistant_text",
                                        type = ConversationOutputBlockType.TEXT_BLOCK,
                                        title = "더미 응답",
                                        content = "실제 모델 호출 없이 입력 내용을 화면에 반영했습니다: $userInput",
                                        collapsed = false
                                    ),
                                    ConversationOutputBlock(
                                        blockId = "block_${timestamp}_assistant_trace",
                                        type = ConversationOutputBlockType.TRACE_BLOCK,
                                        title = "더미 실행 과정",
                                        content = "입력 수신 → 더미 메시지 생성 → 실행 정보 패널 갱신",
                                        collapsed = true
                                    )
                                )
                            )

                            val updatedRun = createDummyConversationRunForInput(
                                sessionId = session.sessionId,
                                runId = "conversation_run_${timestamp}",
                                userInput = userInput,
                                startedAt = nowText,
                                finishedAt = nowText
                            )

                            val updatedSession = session.copy(
                                lastMessageSummary = "더미 응답 생성 완료",
                                updatedAt = nowText,
                                messages = session.messages + userMessage + assistantMessage,
                                latestRun = updatedRun
                            )

                            val index = conversationSessionList.indexOfFirst {
                                it.sessionId == session.sessionId
                            }

                            if (index != -1) {
                                conversationSessionList[index] = updatedSession
                            }
                        }
                    )
                } else {
                    selectedConversationSessionId = null
                    isConversationListScreenOpen = true
                }
            }

            isConversationListScreenOpen -> {
                ConversationSessionListScreen(
                    sessions = conversationSessionList,
                    onBackClick = {
                        isConversationListScreenOpen = false
                    },
                    onNewConversationClick = {
                        val newSession = ConversationSession(
                            sessionId = "conversation_${System.currentTimeMillis()}",
                            title = "새 대화",
                            lastMessageSummary = "아직 메시지가 없습니다.",
                            updatedAt = getCurrentTimeText(),
                            messages = emptyList(),
                            memorySummary = ""
                        )

                        conversationSessionList.add(0, newSession)
                        selectedConversationSessionId = newSession.sessionId
                    },
                    onSessionClick = { session ->
                        selectedConversationSessionId = session.sessionId
                    }
                )
            }

            selectedRun != null -> {
                RunDetailScreen(
                    run = selectedRun,
                    onMenuClick = {
                        scope.launch { drawerState.open() }
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
                        scope.launch { drawerState.open() }
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
                        scope.launch { drawerState.open() }
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
                        scope.launch { drawerState.open() }
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
                        scope.launch { drawerState.open() }
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
                        scope.launch { drawerState.open() }
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
                            scope.launch { drawerState.open() }
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

@Composable
fun ModeSelectionScreen(
    onWorkModeClick: () -> Unit,
    onConversationModeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "MAHA",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "작업모드 또는 대화모드를 선택하세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            onClick = onWorkModeClick,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "작업모드",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "기존 Worker 체인 실행, Run All, 모델 테스트, 실행 로그를 사용합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onWorkModeClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "작업모드로 이동")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            onClick = onConversationModeClick,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "대화모드",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "더미 데이터 기반 대화 세션 목록과 대화방 UI 초안을 확인합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onConversationModeClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "대화모드로 이동")
                }
            }
        }
    }
}

@Composable
fun ConversationSessionListScreen(
    sessions: List<ConversationSession>,
    onBackClick: () -> Unit,
    onNewConversationClick: () -> Unit,
    onSessionClick: (ConversationSession) -> Unit
) {
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
                Text(text = "←")
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "대화",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "더미 세션 목록",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(onClick = onNewConversationClick) {
                Text(text = "+ 새 대화")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sessions) { session ->
                Card(
                    onClick = {
                        onSessionClick(session)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = session.lastMessageSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = session.updatedAt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationRoomScreen(
    session: ConversationSession,
    onBackClick: () -> Unit,
    onSendClick: (String) -> Unit
) {
    var inputText by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(WindowInsets.safeDrawing.asPaddingValues())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(onClick = onBackClick) {
                Text(
                    text = "←",
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Idle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        ConversationRunSummaryPanel(
            run = session.latestRun ?: createDummyConversationRun(session.sessionId)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                if (session.messages.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = Color(0xFF6B7A90),
                                shape = MaterialTheme.shapes.medium
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF202938)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "아직 메시지가 없습니다.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "아래 입력창에 내용을 입력하면 이곳에 대화가 표시됩니다.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(session.messages) { message ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (message.role != ConversationRole.USER) {
                        Text(
                            text = message.role.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    message.blocks.forEach { block ->
                        ConversationOutputBlockCard(
                            block = block,
                            isUserMessage = message.role == ConversationRole.USER,
                            createdAt = message.createdAt
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = Color(0xFF6B7A90),
                        shape = MaterialTheme.shapes.medium
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF202938)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp)
                            .padding(12.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        decorationBox = { innerTextField ->
                            if (inputText.isBlank()) {
                                Text(
                                    text = "메시지를 입력하세요.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            innerTextField()
                        }
                    )

                    TextButton(
                        onClick = {
                            val safeInput = inputText.trim()
                            if (safeInput.isNotBlank()) {
                                onSendClick(safeInput)
                                inputText = ""
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp, end = 8.dp)
                    ) {
                        Text(
                            text = "➤",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (inputText.isBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "모드: 일반",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "검색: OFF",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Worker: 추후",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationOutputBlockCard(
    block: ConversationOutputBlock,
    isUserMessage: Boolean,
    createdAt: String
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isCollapsed by rememberSaveable(block.blockId, isUserMessage) {
        mutableStateOf(if (isUserMessage) true else block.collapsed)
    }
    var isMenuOpen by rememberSaveable(block.blockId) {
        mutableStateOf(false)
    }
    var isSelectionDialogOpen by rememberSaveable(block.blockId) {
        mutableStateOf(false)
    }

    if (isSelectionDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                isSelectionDialogOpen = false
            },
            title = {
                Text(
                    text = "텍스트 선택",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                SelectionContainer {
                    Text(
                        text = block.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isSelectionDialogOpen = false
                    }
                ) {
                    Text(text = "닫기")
                }
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUserMessage) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        Box {
            Card(
                modifier = Modifier
                    .fillMaxWidth(if (isUserMessage) 0.82f else 1f)
                    .border(
                        width = 1.dp,
                        color = if (isUserMessage) {
                            Color(0xFF7C8EA8)
                        } else {
                            Color(0xFF3D4A5D)
                        },
                        shape = MaterialTheme.shapes.medium
                    )
                    .combinedClickable(
                        onClick = {
                            isCollapsed = !isCollapsed
                        },
                        onLongClick = {
                            isMenuOpen = true
                        }
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUserMessage) {
                        Color(0xFF253247)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isUserMessage) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = block.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = buildConversationBlockTypeLabel(block),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            Text(
                                text = "⋯",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!isCollapsed) {
                        Text(
                            text = block.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text(
                            text = if (isUserMessage) {
                                "메시지 접힘"
                            } else {
                                "접힌 블록입니다. 탭하면 펼칠 수 있습니다."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = isMenuOpen,
                onDismissRequest = {
                    isMenuOpen = false
                }
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = createdAt,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        isMenuOpen = false
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text(
                            text = "복사",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(block.content))
                        isMenuOpen = false
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text(
                            text = "텍스트 선택",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        isSelectionDialogOpen = true
                        isMenuOpen = false
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text(
                            text = "메시지 편집",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        Toast.makeText(context, "메시지 편집은 후속 단계에서 연결합니다.", Toast.LENGTH_SHORT).show()
                        isMenuOpen = false
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text(
                            text = "공유",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, block.content)
                        }
                        context.startActivity(
                            Intent.createChooser(shareIntent, "메시지 공유")
                        )
                        isMenuOpen = false
                    }
                )
            }
        }
    }
}
@Composable
fun ConversationRunSummaryPanel(
    run: ConversationRun
) {
    var isExpanded by rememberSaveable(run.runId) {
        mutableStateOf(false)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .border(
                width = 1.dp,
                color = Color(0xFF6B7A90),
                shape = MaterialTheme.shapes.medium
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF202938)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "${run.status.name} · ${run.totalLatencySec}s · ${run.workerResults.size} Worker · 재시도 ${run.totalRetryCount}회",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "더미 실행 정보",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                TextButton(
                    onClick = {
                        isExpanded = !isExpanded
                    }
                ) {
                    Text(
                        text = if (isExpanded) "접기" else "펼치기",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (isExpanded) {
                Text(
                    text = "총 시간: ${run.totalLatencySec}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Worker 수: ${run.workerResults.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "총 재시도: ${run.totalRetryCount}회",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                run.workerResults.forEach { worker ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = worker.workerName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "${worker.providerName} / ${worker.modelName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = "상태: ${worker.status} · 시간: ${worker.latencySec}s · 재시도: ${worker.retryCount}회",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "t/s: ${worker.tokensPerSecond ?: "-"} · 오류: ${worker.errorType.ifBlank { "-" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildConversationBlockTypeLabel(
    block: ConversationOutputBlock
): String {
    return when (block.type) {
        ConversationOutputBlockType.TEXT_BLOCK -> "TEXT"
        ConversationOutputBlockType.MARKDOWN_BLOCK -> "MD"
        ConversationOutputBlockType.CODE_BLOCK -> {
            if (block.language.isBlank()) {
                "CODE"
            } else {
                "CODE · ${block.language}"
            }
        }
        ConversationOutputBlockType.TABLE_BLOCK -> "TABLE"
        ConversationOutputBlockType.JSON_BLOCK -> "JSON"
        ConversationOutputBlockType.ERROR_BLOCK -> "ERROR"
        ConversationOutputBlockType.TRACE_BLOCK -> "TRACE"
        ConversationOutputBlockType.MEMORY_BLOCK -> "MEMORY"
    }
}

@Composable
fun ModelRecommendationDialog(
    recommendations: List<ModelRecommendation>,
    onApplyClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val applicableCount = recommendations.count { it.canApply }

    AlertDialog(
        onDismissRequest = onCancelClick,
        title = {
            Text(text = "추천 모델 미리보기")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "적용 가능 Worker: $applicableCount / ${recommendations.size}")
                Text(text = "확인을 누르기 전에는 어떤 Worker도 변경되지 않습니다.")

                SelectionContainer {
                    Text(
                        text = recommendations.joinToString(separator = "\n\n") { recommendation ->
                            buildString {
                                appendLine(recommendation.agentName)
                                appendLine("현재: ${recommendation.currentProviderName} / ${recommendation.currentModelName}")
                                appendLine("추천: ${recommendation.recommendedProviderName} / ${recommendation.recommendedModelName}")
                                appendLine("점수: ${recommendation.score}")
                                appendLine("이유: ${recommendation.reason}")
                                if (recommendation.warning.isNotBlank()) {
                                    appendLine("주의: ${recommendation.warning}")
                                }
                                if (!recommendation.canApply) {
                                    appendLine("상태: 적용 불가")
                                }
                            }
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = applicableCount > 0,
                onClick = onApplyClick
            ) {
                Text(text = "전체 적용")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancelClick
            ) {
                Text(text = "취소")
            }
        }
    )
}

@Composable
fun RunAllPrecheckDialog(
    precheck: RunPrecheckResult,
    onConfirmClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val warningText = if (precheck.warnings.isEmpty()) {
        "경고 대상 모델이 없습니다."
    } else {
        precheck.warnings.joinToString(separator = "\n") { warning ->
            "- ${warning.agentName}: ${warning.providerName} / ${warning.modelName} / ${warning.status}"
        }
    }

    AlertDialog(
        onDismissRequest = onCancelClick,
        title = {
            Text(text = "Run All 실행 전 확인")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "예상 API 호출 수: ${precheck.estimatedApiCallCount}")
                Text(text = "실행 대상 Worker: ${precheck.enabledWorkerCount}")
                Text(text = "비활성 Worker: ${precheck.disabledWorkerCount}")
                Text(text = warningText)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmClick
            ) {
                Text(text = "확인 후 실행")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancelClick
            ) {
                Text(text = "취소")
            }
        }
    )
}

@Composable
fun DrawerHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "MAHA",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Multi-Agent Harness App",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun DrawerItemTitle(
    title: String,
    subtitle: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun mahaDarkColorScheme() = darkColorScheme(
    primary = Color(0xFFECF3FF),
    onPrimary = Color(0xFF081018),
    secondary = Color(0xFF93A9C3),
    onSecondary = Color(0xFF081018),
    tertiary = Color(0xFF6EE7B7),
    background = Color(0xFF070B12),
    onBackground = Color(0xFFF3F6FB),
    surface = Color(0xFF141A24),
    onSurface = Color(0xFFF3F6FB),
    surfaceVariant = Color(0xFF1C2431),
    onSurfaceVariant = Color(0xFFBFC9D9),
    outline = Color(0xFF3D4A5D)
)

private fun createDefaultAgents(): List<Agent> {
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

private fun createNewAgent(existingAgents: List<Agent>): Agent {
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

private fun generateUniqueAgentId(existingAgents: List<Agent>): String {
    val existingIds = existingAgents.map { it.id }.toSet()
    var index = System.currentTimeMillis()
    var candidate = "agent_$index"

    while (candidate in existingIds) {
        index += 1
        candidate = "agent_$index"
    }

    return candidate
}

private fun generateUniqueScenarioId(existingScenarios: List<Scenario>): String {
    val existingIds = existingScenarios.map { it.id }.toSet()
    var index = System.currentTimeMillis()
    var candidate = "scenario_$index"

    while (candidate in existingIds) {
        index += 1
        candidate = "scenario_$index"
    }

    return candidate
}

private fun normalizeAgents(source: List<Agent>): List<Agent> {
    return source
        .map { sanitizeAgent(it) }
        .filter { it.id.isNotBlank() }
        .distinctBy { it.id }
}

private fun sanitizeAgent(agent: Agent): Agent {
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

private fun sanitizeProviderName(providerName: String): String {
    return when (providerName) {
        ModelProviderType.GOOGLE -> ModelProviderType.GOOGLE
        ModelProviderType.NVIDIA -> ModelProviderType.NVIDIA
        ModelProviderType.DUMMY -> ModelProviderType.DUMMY
        else -> ModelProviderType.DUMMY
    }
}

private fun sanitizeModelForProviderInMain(
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

private fun sanitizeRunResult(result: RunResult): RunResult? {
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

private fun sanitizeRunWithAgents(
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

private fun sanitizeRunsWithAgents(
    runs: List<Run>,
    validAgents: List<Agent>
): List<Run> {
    return runs
        .mapNotNull { sanitizeRunWithAgents(it, validAgents) }
        .distinctBy { it.runId }
}

private fun sanitizeScenariosWithAgents(
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

private fun syncExecutionStateMap(
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

private fun moveAgentUp(
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

private fun moveAgentDown(
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

enum class ConversationRole {
    USER,
    ASSISTANT,
    SYSTEM,
    WORKER
}

enum class ConversationOutputBlockType {
    TEXT_BLOCK,
    MARKDOWN_BLOCK,
    CODE_BLOCK,
    TABLE_BLOCK,
    JSON_BLOCK,
    ERROR_BLOCK,
    TRACE_BLOCK,
    MEMORY_BLOCK
}

enum class ConversationRunStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    PARTIAL_FAILED,
    FAILED
}

data class ConversationSession(
    val sessionId: String,
    val title: String,
    val lastMessageSummary: String,
    val updatedAt: String,
    val messages: List<ConversationMessage>,
    val memorySummary: String = "",
    val latestRun: ConversationRun? = null
)

data class ConversationMessage(
    val messageId: String,
    val sessionId: String,
    val role: ConversationRole,
    val createdAt: String,
    val blocks: List<ConversationOutputBlock>,
    val linkedRunId: String? = null
)

data class ConversationOutputBlock(
    val blockId: String,
    val type: ConversationOutputBlockType,
    val title: String,
    val content: String,
    val language: String = "",
    val collapsed: Boolean,
    val copyable: Boolean = true,
    val metadata: Map<String, String> = emptyMap()
)

data class ConversationRun(
    val runId: String,
    val sessionId: String,
    val userInput: String,
    val orchestratorPlan: String,
    val status: ConversationRunStatus,
    val startedAt: String,
    val finishedAt: String,
    val totalLatencySec: Double,
    val totalRetryCount: Int,
    val workerResults: List<ConversationWorkerResult>
)

data class ConversationWorkerResult(
    val workerName: String,
    val providerName: String,
    val modelName: String,
    val status: String,
    val latencySec: Double,
    val retryCount: Int,
    val tokensPerSecond: Double?,
    val errorType: String,
    val outputSummary: String,
    val rawOutput: String
)

private fun createDummyConversationSessions(): List<ConversationSession> {
    val firstSessionId = "conversation_001"
    val secondSessionId = "conversation_002"

    return listOf(
        ConversationSession(
            sessionId = firstSessionId,
            title = "MAHA 대화모드 설계",
            lastMessageSummary = "OutputBlock 기반 대화방 UI 초안을 정리했습니다.",
            updatedAt = "2026-04-27 10:30:00",
            memorySummary = "대화모드는 작업모드와 분리하고, 블록 기반 출력 구조를 사용한다.",
            messages = listOf(
                ConversationMessage(
                    messageId = "message_001_user",
                    sessionId = firstSessionId,
                    role = ConversationRole.USER,
                    createdAt = "2026-04-27 10:28:00",
                    blocks = listOf(
                        ConversationOutputBlock(
                            blockId = "block_001_user_text",
                            type = ConversationOutputBlockType.TEXT_BLOCK,
                            title = "사용자 입력",
                            content = "대화모드 UI 초안을 설명해줘.",
                            collapsed = false
                        )
                    )
                ),
                ConversationMessage(
                    messageId = "message_002_assistant",
                    sessionId = firstSessionId,
                    role = ConversationRole.ASSISTANT,
                    createdAt = "2026-04-27 10:30:00",
                    linkedRunId = "conversation_run_001",
                    blocks = listOf(
                        ConversationOutputBlock(
                            blockId = "block_002_summary",
                            type = ConversationOutputBlockType.TEXT_BLOCK,
                            title = "요약",
                            content = "대화모드는 세션 목록, 대화방, OutputBlock, 접이식 실행 정보 패널로 구성됩니다.",
                            collapsed = false
                        ),
                        ConversationOutputBlock(
                            blockId = "block_003_markdown",
                            type = ConversationOutputBlockType.MARKDOWN_BLOCK,
                            title = "설계 메모",
                            content = "- 상단은 세션명과 상태만 표시\n- 중앙은 메시지 리스트\n- 하단은 입력창과 실행 버튼 중심",
                            collapsed = false
                        ),
                        ConversationOutputBlock(
                            blockId = "block_004_code",
                            type = ConversationOutputBlockType.CODE_BLOCK,
                            title = "예시 코드 블록",
                            content = "data class ConversationSession(\n    val sessionId: String,\n    val title: String\n)",
                            language = "kotlin",
                            collapsed = false
                        ),
                        ConversationOutputBlock(
                            blockId = "block_005_trace",
                            type = ConversationOutputBlockType.TRACE_BLOCK,
                            title = "실행 과정",
                            content = "Orchestrator → Main Worker → Synthesis Worker 순서로 처리됨",
                            collapsed = true
                        ),
                        ConversationOutputBlock(
                            blockId = "block_006_memory",
                            type = ConversationOutputBlockType.MEMORY_BLOCK,
                            title = "저장 후보 기억",
                            content = "사용자는 대화모드와 작업모드를 분리하기를 원한다.",
                            collapsed = true
                        )
                    )
                )
            ),
            latestRun = createDummyConversationRun(firstSessionId)
        ),
        ConversationSession(
            sessionId = secondSessionId,
            title = "로컬 기억 저장소 구상",
            lastMessageSummary = "RAG와 메모리 저장은 후속 단계로 분리했습니다.",
            updatedAt = "2026-04-27 11:05:00",
            memorySummary = "1차 구현에서는 실제 RAG, 검색, 검증, 메모리 저장을 제외한다.",
            messages = listOf(
                ConversationMessage(
                    messageId = "message_003_user",
                    sessionId = secondSessionId,
                    role = ConversationRole.USER,
                    createdAt = "2026-04-27 11:03:00",
                    blocks = listOf(
                        ConversationOutputBlock(
                            blockId = "block_007_user_text",
                            type = ConversationOutputBlockType.TEXT_BLOCK,
                            title = "사용자 입력",
                            content = "로컬 기억 저장소는 이번에 구현해?",
                            collapsed = false
                        )
                    )
                ),
                ConversationMessage(
                    messageId = "message_004_assistant",
                    sessionId = secondSessionId,
                    role = ConversationRole.ASSISTANT,
                    createdAt = "2026-04-27 11:05:00",
                    linkedRunId = "conversation_run_002",
                    blocks = listOf(
                        ConversationOutputBlock(
                            blockId = "block_008_text",
                            type = ConversationOutputBlockType.TEXT_BLOCK,
                            title = "답변",
                            content = "이번 단계에서는 구현하지 않습니다. 먼저 더미 데이터 기반 UI와 UX를 확인합니다.",
                            collapsed = false
                        ),
                        ConversationOutputBlock(
                            blockId = "block_009_json",
                            type = ConversationOutputBlockType.JSON_BLOCK,
                            title = "JSON 예시",
                            content = "{\n  \"ragEnabled\": false,\n  \"memoryWriteEnabled\": false,\n  \"mode\": \"dummy-ui\"\n}",
                            language = "json",
                            collapsed = false
                        ),
                        ConversationOutputBlock(
                            blockId = "block_010_error",
                            type = ConversationOutputBlockType.ERROR_BLOCK,
                            title = "제외 항목",
                            content = "실제 API 호출, 검색 Worker, 검증 Worker, VectorDB는 이번 단계에서 제외합니다.",
                            collapsed = false
                        )
                    )
                )
            ),
            latestRun = createDummyConversationRun(secondSessionId)
        )
    )
}

private fun createDummyConversationRunForInput(
    sessionId: String,
    runId: String,
    userInput: String,
    startedAt: String,
    finishedAt: String
): ConversationRun {
    return ConversationRun(
        runId = runId,
        sessionId = sessionId,
        userInput = userInput,
        orchestratorPlan = "입력 내용을 더미 응답으로 변환",
        status = ConversationRunStatus.COMPLETED,
        startedAt = startedAt,
        finishedAt = finishedAt,
        totalLatencySec = 1.1,
        totalRetryCount = 0,
        workerResults = listOf(
            ConversationWorkerResult(
                workerName = "Dummy Conversation Worker",
                providerName = "DUMMY",
                modelName = "dummy",
                status = "COMPLETED",
                latencySec = 1.1,
                retryCount = 0,
                tokensPerSecond = null,
                errorType = "",
                outputSummary = "더미 응답 생성 완료",
                rawOutput = "Input: $userInput"
            )
        )
    )
}

private fun createDummyConversationRun(
    sessionId: String
): ConversationRun {
    return ConversationRun(
        runId = "conversation_run_$sessionId",
        sessionId = sessionId,
        userInput = "더미 대화 입력",
        orchestratorPlan = "더미 UI 확인용 실행 계획",
        status = ConversationRunStatus.COMPLETED,
        startedAt = "2026-04-27 10:28:00",
        finishedAt = "2026-04-27 10:30:00",
        totalLatencySec = 3.2,
        totalRetryCount = 1,
        workerResults = listOf(
            ConversationWorkerResult(
                workerName = "Orchestrator",
                providerName = "DUMMY",
                modelName = "dummy",
                status = "COMPLETED",
                latencySec = 0.8,
                retryCount = 0,
                tokensPerSecond = null,
                errorType = "",
                outputSummary = "사용자 의도 분석 완료",
                rawOutput = "Dummy orchestrator output"
            ),
            ConversationWorkerResult(
                workerName = "Main Worker",
                providerName = "DUMMY",
                modelName = "dummy",
                status = "COMPLETED",
                latencySec = 1.6,
                retryCount = 1,
                tokensPerSecond = 18.4,
                errorType = "",
                outputSummary = "본문 답변 생성 완료",
                rawOutput = "Dummy main worker output"
            ),
            ConversationWorkerResult(
                workerName = "Synthesis Worker",
                providerName = "DUMMY",
                modelName = "dummy",
                status = "COMPLETED",
                latencySec = 0.8,
                retryCount = 0,
                tokensPerSecond = 22.1,
                errorType = "",
                outputSummary = "최종 답변 정리 완료",
                rawOutput = "Dummy synthesis output"
            )
        )
    )
}
