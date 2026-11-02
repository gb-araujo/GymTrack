package app.gabrielaraujo.gymtrack.ui.navigation

import kotlinx.serialization.Serializable

// Rotas tipadas do Navigation Compose: os argumentos são checados em tempo de compilação.

@Serializable
data object HomeRoute

@Serializable
data object PlansRoute

@Serializable
data object HistoryRoute

@Serializable
data object ProgressRoute

@Serializable
data object GoalsRoute

@Serializable
data class HistoryDetailRoute(val sessionId: String)

@Serializable
data class ExerciseProgressRoute(val exerciseId: String)

/** @param planId ficha a editar; null cria uma nova. */
@Serializable
data class PlanEditorRoute(val planId: String? = null)
