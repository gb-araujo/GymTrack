package app.gabrielaraujo.gymtrack.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.gabrielaraujo.gymtrack.ui.AppViewModelProvider
import app.gabrielaraujo.gymtrack.ui.account.AccountViewModel
import app.gabrielaraujo.gymtrack.ui.goals.GoalsScreen
import app.gabrielaraujo.gymtrack.ui.history.HistoryDetailScreen
import app.gabrielaraujo.gymtrack.ui.history.HistoryScreen
import app.gabrielaraujo.gymtrack.ui.home.HomeScreen
import app.gabrielaraujo.gymtrack.ui.plans.PlanEditorScreen
import app.gabrielaraujo.gymtrack.ui.plans.WorkoutPlansScreen
import app.gabrielaraujo.gymtrack.ui.progress.ProgressScreen
import app.gabrielaraujo.gymtrack.ui.theme.TC
import app.gabrielaraujo.gymtrack.ui.workout.SessionSummaryScreen
import app.gabrielaraujo.gymtrack.ui.workout.WorkoutExecutionScreen
import app.gabrielaraujo.gymtrack.ui.workout.WorkoutViewModel

private data class Tab(val route: Any, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(HomeRoute, "Início", Icons.Default.Home),
    Tab(PlansRoute, "Fichas", Icons.Default.FitnessCenter),
    Tab(HistoryRoute, "Histórico", Icons.Default.History),
    Tab(ProgressRoute, "Progresso", Icons.AutoMirrored.Filled.TrendingUp),
    Tab(GoalsRoute, "Metas", Icons.Default.Flag),
)

private fun NavDestination.isTab(tab: Tab) = hasRoute(tab.route::class)

@Composable
fun GymTrackApp() {
    val nav = rememberNavController()
    // Os dois vivem com a Activity: o treino em andamento assume a tela inteira e a conta é usada no Início.
    val workoutVm: WorkoutViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val accountVm: AccountViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val workout by workoutVm.state.collectAsStateWithLifecycle()

    // Sessão ativa e não concluída assume a tela inteira.
    val active = workout.active
    if (active != null && !active.session.completed) {
        WorkoutExecutionScreen(workoutVm)
        return
    }
    // Tela de resumo após concluir a sessão.
    if (active != null) {
        SessionSummaryScreen(workoutVm, onFinish = workoutVm::dismissSummary)
        return
    }

    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBottomBar = destination != null && tabs.any { destination.isTab(it) }

    Box(Modifier.fillMaxSize().background(TC.Background)) {
        NavHost(navController = nav, startDestination = HomeRoute, modifier = Modifier.fillMaxSize()) {
            composable<HomeRoute> {
                HomeScreen(
                    viewModel(factory = AppViewModelProvider.Factory),
                    accountVm,
                    onGoPlans = { nav.navigate(PlansRoute) },
                    onGoProgress = { nav.navigate(ProgressRoute) },
                    onOpenSession = { id -> nav.navigate(HistoryDetailRoute(id)) },
                )
            }
            composable<PlansRoute> {
                WorkoutPlansScreen(
                    viewModel(factory = AppViewModelProvider.Factory),
                    onEditPlan = { planId -> nav.navigate(PlanEditorRoute(planId)) },
                    onNewPlan = { nav.navigate(PlanEditorRoute()) },
                )
            }
            composable<HistoryRoute> {
                HistoryScreen(viewModel(factory = AppViewModelProvider.Factory), onOpenSession = { id -> nav.navigate(HistoryDetailRoute(id)) })
            }
            composable<ProgressRoute> { ProgressScreen(viewModel(factory = AppViewModelProvider.Factory)) }
            composable<GoalsRoute> { GoalsScreen(viewModel(factory = AppViewModelProvider.Factory)) }
            composable<HistoryDetailRoute> { entry ->
                HistoryDetailScreen(
                    viewModel(factory = AppViewModelProvider.Factory),
                    sessionId = entry.toRoute<HistoryDetailRoute>().sessionId,
                    onBack = { nav.popBackStack() },
                )
            }
            composable<PlanEditorRoute> {
                PlanEditorScreen(viewModel(factory = AppViewModelProvider.Factory), onDone = { nav.popBackStack() })
            }
        }

        if (showBottomBar) {
            BottomBar(
                isSelected = { tab -> destination?.isTab(tab) == true },
                onSelect = { tab ->
                    nav.navigate(tab.route) {
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
private fun BottomBar(isSelected: (Tab) -> Boolean, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
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
            val selected = isSelected(tab)
            val color = if (selected) TC.Indigo400 else TC.Slate500
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onSelect(tab) }.padding(horizontal = 12.dp, vertical = 2.dp),
            ) {
                Icon(tab.icon, contentDescription = tab.label, tint = color, modifier = Modifier.size(24.dp))
                Text(tab.label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
