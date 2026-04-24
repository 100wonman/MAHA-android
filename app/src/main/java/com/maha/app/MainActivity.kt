// MainActivity.kt

package com.maha.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

    var selectedAgentId by remember { mutableStateOf<String?>(null) }
    var selectedRun by remember { mutableStateOf<Run?>(null) }
    var modelSelectionAgentId by remember { mutableStateOf<String?>(null) }

    var isScenarioScreenOpen by remember { mutableStateOf(false) }
    var isSettingsScreenOpen by remember { mutableStateOf(false) }
    var isModelCatalogScreenOpen by remember { mutableStateOf(false) }
    var isDataLoaded by remember { mutableStateOf(false) }
    var isRunAllRunning by remember { mutableStateOf(false) }
    var runningAgentId by remember { mutableStateOf<String?>(null) }
    var savedGoogleApiKey by remember { mutableStateOf("") }
    var savedProvider by remember { mutableStateOf(ModelProviderType.DUMMY) }
    var isSearchingModels by remember { mutableStateOf(false) }
    var modelSearchMessage by remember { mutableStateOf("") }
    var promptText by remember {
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

        selectedAgentId = null
        selectedRun = null
        modelSelectionAgentId = null
        isScenarioScreenOpen = false
        isSettingsScreenOpen = false
        isModelCatalogScreenOpen = false
        isRunAllRunning = false
        runningAgentId = null
        isSearchingModels = false
        modelSearchMessage = ""
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

        if (selectedRun != null && runList.none { it.runId == selectedRun?.runId }) {
            selectedRun = null
        }
    }

    LaunchedEffect(runList.toList()) {
        if (selectedRun != null) {
            selectedRun = runList.find { it.runId == selectedRun?.runId }
        }
    }

    val selectedAgent = agentList.find { it.id == selectedAgentId }
    val modelSelectionAgent = agentList.find { it.id == modelSelectionAgentId }

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
                            selectedAgent == null &&
                            selectedRun == null,
                    onClick = {
                        if (!isRunAllRunning && runningAgentId == null && !isSearchingModels) {
                            selectedAgentId = null
                            selectedRun = null
                            modelSelectionAgentId = null
                            isScenarioScreenOpen = false
                            isSettingsScreenOpen = false
                            isModelCatalogScreenOpen = false
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
                            selectedRun = null
                            modelSelectionAgentId = null
                            isScenarioScreenOpen = true
                            isSettingsScreenOpen = false
                            isModelCatalogScreenOpen = false
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
                    selected = isModelCatalogScreenOpen,
                    onClick = {
                        if (!isRunAllRunning && runningAgentId == null && !isSearchingModels) {
                            selectedAgentId = null
                            selectedRun = null
                            modelSelectionAgentId = null
                            isScenarioScreenOpen = false
                            isSettingsScreenOpen = false
                            isModelCatalogScreenOpen = true
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
                            title = "Settings",
                            subtitle = "Provider / API Key 설정"
                        )
                    },
                    selected = isSettingsScreenOpen,
                    onClick = {
                        if (!isRunAllRunning && runningAgentId == null && !isSearchingModels) {
                            selectedAgentId = null
                            selectedRun = null
                            modelSelectionAgentId = null
                            isScenarioScreenOpen = false
                            isSettingsScreenOpen = true
                            isModelCatalogScreenOpen = false
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
                        selected = selectedRun?.runId == runList.firstOrNull()?.runId,
                        onClick = {
                            if (!isRunAllRunning && runningAgentId == null && !isSearchingModels) {
                                selectedAgentId = null
                                modelSelectionAgentId = null
                                isScenarioScreenOpen = false
                                isSettingsScreenOpen = false
                                isModelCatalogScreenOpen = false
                                selectedRun = runList.firstOrNull()
                                scope.launch { drawerState.close() }
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        when {
            selectedRun != null -> {
                RunDetailScreen(
                    run = selectedRun!!,
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    },
                    onBackClick = {
                        selectedRun = null
                    }
                )
            }

            isModelCatalogScreenOpen -> {
                ModelCatalogScreen(
                    discoveredModels = discoveredModelList,
                    selectedModelName = modelSelectionAgent?.modelName ?: GeminiModelType.DEFAULT,
                    isSelectionMode = modelSelectionAgent != null,
                    isSearchingModels = isSearchingModels,
                    modelSearchMessage = modelSearchMessage,
                    onSearchApiModelsClick = {
                        if (isSearchingModels) return@ModelCatalogScreen

                        scope.launch {
                            isSearchingModels = true
                            modelSearchMessage = "API 모델을 검색하는 중입니다..."

                            try {
                                val models = GoogleModelDiscoveryProvider.fetchModels()

                                if (models.isEmpty()) {
                                    modelSearchMessage = "API 모델 검색 실패 또는 결과 없음. 수동 모델은 계속 사용할 수 있습니다."
                                } else {
                                    ModelCatalogManager.saveDiscoveredModels(context, models)

                                    discoveredModelList.clear()
                                    discoveredModelList.addAll(
                                        ModelCatalogManager.getDiscoveredModels(context)
                                    )

                                    val generateContentCount = models.count {
                                        it.isGenerateContentSupported
                                    }

                                    modelSearchMessage =
                                        "API 모델 검색 완료. 전체: ${models.size}, 사용 가능: $generateContentCount"
                                }
                            } catch (exception: Exception) {
                                modelSearchMessage =
                                    "API 모델 검색 실패: ${exception.message ?: "알 수 없는 오류"}"
                            } finally {
                                isSearchingModels = false
                            }
                        }
                    },
                    onSelectModelClick = { modelName ->
                        val targetAgentId = modelSelectionAgentId ?: return@ModelCatalogScreen
                        val safeModelName = GeminiModelType.sanitize(modelName)
                        val index = agentList.indexOfFirst { it.id == targetAgentId }

                        if (index != -1) {
                            val updatedAgent = agentList[index].copy(
                                modelName = safeModelName
                            )

                            agentList[index] = sanitizeAgent(updatedAgent)
                            selectedAgentId = targetAgentId
                            modelSelectionAgentId = null
                            isModelCatalogScreenOpen = false
                        }
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
                        selectedRun = null
                        modelSelectionAgentId = null
                        isSettingsScreenOpen = false
                        isModelCatalogScreenOpen = false

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
                        selectedRun = null
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
                        selectedRun = runList.find { it.runId == run.runId }
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
                        selectedRun = null
                        isScenarioScreenOpen = false
                        isSettingsScreenOpen = false
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
                        selectedRun = runList.find { it.runId == run.runId }
                    },
                    onRunAllClick = {
                        if (isRunAllRunning || runningAgentId != null || isSearchingModels) {
                            return@AgentListScreen
                        }

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
                    }
                )
            }
        }
    }
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
            modelName = GeminiModelType.DEFAULT
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

    return agent.copy(
        id = safeId,
        name = safeName,
        description = safeDescription,
        status = safeStatus,
        inputFormat = safeInputFormat,
        outputFormat = safeOutputFormat,
        modelName = GeminiModelType.sanitize(agent.modelName)
    )
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