package app.gabrielaraujo.gymtrack.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import app.gabrielaraujo.gymtrack.GymTrackApplication
import app.gabrielaraujo.gymtrack.di.AppContainer
import app.gabrielaraujo.gymtrack.ui.account.AccountViewModel
import app.gabrielaraujo.gymtrack.ui.goals.GoalsViewModel
import app.gabrielaraujo.gymtrack.ui.history.HistoryViewModel
import app.gabrielaraujo.gymtrack.ui.home.HomeViewModel
import app.gabrielaraujo.gymtrack.ui.navigation.PlanEditorRoute
import app.gabrielaraujo.gymtrack.ui.plans.PlanEditorViewModel
import app.gabrielaraujo.gymtrack.ui.plans.PlansViewModel
import app.gabrielaraujo.gymtrack.ui.progress.ProgressViewModel
import app.gabrielaraujo.gymtrack.ui.workout.WorkoutViewModel

/** Fábrica de todos os ViewModels: cada um recebe do [AppContainer] só o que usa. */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            val c = container()
            HomeViewModel(c.repositories.plans, c.repositories.sessions, c.repositories.records, c.repositories.exercises, c.workout)
        }
        initializer {
            val c = container()
            AccountViewModel(
                auth = c.authManager,
                account = c.account,
                syncStatus = c.syncEngine.status,
                pendingSync = c.repositories.userData.pendingSyncCount,
                backup = c.repositories.backup,
                sessions = c.repositories.sessions,
                healthImporter = c.healthImporter,
                healthConnect = c.healthConnect,
                appScope = c.appScope,
            )
        }
        initializer {
            val c = container()
            WorkoutViewModel(
                c.workout, c.repositories.plans, c.repositories.exercises, c.repositories.sessions,
                c.healthImporter.importEnabled, c.appScope,
            )
        }
        initializer {
            val c = container()
            PlansViewModel(c.repositories.plans, c.repositories.exercises, c.appScope)
        }
        initializer {
            val c = container()
            val route = createSavedStateHandle().toRoute<PlanEditorRoute>()
            PlanEditorViewModel(route.planId, c.repositories.plans, c.repositories.exercises, c.appScope)
        }
        initializer {
            val c = container()
            HistoryViewModel(c.repositories.sessions, c.repositories.plans, c.repositories.records, c.repositories.exercises, c.healthConnect, c.appScope)
        }
        initializer {
            val c = container()
            ProgressViewModel(c.repositories.sessions, c.repositories.records, c.repositories.exercises, c.healthImporter.bodyWeights)
        }
        initializer {
            val c = container()
            GoalsViewModel(
                c.repositories.goals, c.repositories.sessions, c.repositories.records, c.repositories.exercises,
                c.healthImporter.bodyWeights, c.appScope,
            )
        }
    }
}

private fun CreationExtras.container(): AppContainer = (this[APPLICATION_KEY] as GymTrackApplication).container
