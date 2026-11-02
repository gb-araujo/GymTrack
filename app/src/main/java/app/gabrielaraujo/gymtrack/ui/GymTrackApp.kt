package app.gabrielaraujo.gymtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.gabrielaraujo.gymtrack.ui.screens.ExerciseProgressScreen
import app.gabrielaraujo.gymtrack.ui.screens.GoalsScreen
import app.gabrielaraujo.gymtrack.ui.screens.HistoryDetailScreen
import app.gabrielaraujo.gymtrack.ui.screens.HistoryScreen
import app.gabrielaraujo.gymtrack.ui.screens.HomeScreen
import app.gabrielaraujo.gymtrack.ui.screens.PlanEditorScreen
import app.gabrielaraujo.gymtrack.ui.screens.ProgressScreen
import app.gabrielaraujo.gymtrack.ui.screens.WorkoutExecutionScreen
import app.gabrielaraujo.gymtrack.ui.screens.WorkoutPlansScreen
import app.gabrielaraujo.gymtrack.ui.theme.TC

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Início", Icons.Default.Home),
    Tab("plans", "Fichas", Icons.Default.FitnessCenter),
    Tab("history", "Histórico", Icons.Default.History),
    Tab("progress", "Progresso", Icons.AutoMirrored.Filled.TrendingUp),
    Tab("goals", "Metas", Icons.Default.Flag),
)

@Composable
fun GymTrackApp(vm: GymTrackViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()

    // Ao abrir ou voltar ao app: dados do relógio (Samsung Health etc.) via Health Connect.
    // A leitura só é permitida com o app em primeiro plano.
    LifecycleStartEffect(vm) {
        vm.refreshHealthData()
        onStopOrDispose { }
    }

    // Sessão ativa e não concluída assume a tela inteira (equiv. ao showExecution do web).
    val active = state.activeSession
    if (active != null && !active.session.completed) {
        WorkoutExecutionScreen(vm)
        return
    }
    // Tela de resumo após concluir a sessão.
    if (active != null && active.session.completed) {
        app.gabrielaraujo.gymtrack.ui.screens.SessionSummaryScreen(vm) { vm.dismissSummary() }
        return
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in tabs.map { it.route }

    Box(Modifier.fillMaxSize().background(TC.Background)) {
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.fillMaxSize()) {
            composable("home") {
                HomeScreen(
                    vm,
                    onStart = { /* execução assume a tela via state */ },
                    onGoPlans = { nav.navigate("plans") },
                    onGoProgress = { nav.navigate("progress") },
                    onOpenSession = { id -> nav.navigate("history_detail/$id") },
                )
            }
            composable("plans") {
                WorkoutPlansScreen(
                    vm,
                    onEditPlan = { planId -> nav.navigate("plan_editor/$planId") },
                    onNewPlan = { nav.navigate("plan_editor/new") },
                )
            }
            composable("history") {
                HistoryScreen(vm, onOpenSession = { id -> nav.navigate("history_detail/$id") })
            }
            composable("progress") { ProgressScreen(vm, onOpenExercise = { id -> nav.navigate("exercise/$id") }) }
            composable("goals") { GoalsScreen(vm) }
            composable("history_detail/{sessionId}") { entry ->
                HistoryDetailScreen(
                    vm,
                    sessionId = entry.arguments?.getString("sessionId").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenExercise = { id -> nav.navigate("exercise/$id") },
                )
            }
            composable("exercise/{exerciseId}") { entry ->
                ExerciseProgressScreen(
                    vm,
                    exerciseId = entry.arguments?.getString("exerciseId").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenSession = { id -> nav.navigate("history_detail/$id") },
                )
            }
            composable("plan_editor/{planId}") { entry ->
                PlanEditorScreen(
                    vm,
                    planId = entry.arguments?.getString("planId").orEmpty(),
                    onDone = { nav.popBackStack() },
                )
            }
        }

        if (showBottomBar) {
            BottomBar(
                currentRoute = currentRoute,
                onSelect = { route ->
                    nav.navigate(route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun BottomBar(currentRoute: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(TC.Slate950)
            .navigationBarsPadding()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val active = currentRoute == tab.route
            val color = if (active) TC.Indigo400 else TC.Slate500
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onSelect(tab.route) }.padding(horizontal = 12.dp, vertical = 2.dp),
            ) {
                Icon(tab.icon, contentDescription = tab.label, tint = color, modifier = Modifier.size(24.dp))
                Text(tab.label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
