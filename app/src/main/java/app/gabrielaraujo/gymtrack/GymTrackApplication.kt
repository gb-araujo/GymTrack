package app.gabrielaraujo.gymtrack

import android.app.Application
import app.gabrielaraujo.gymtrack.di.AppContainer
import app.gabrielaraujo.gymtrack.widget.WidgetUpdater
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Inicializa o [AppContainer], que guarda as dependências por toda a vida do processo. */
class GymTrackApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        val repos = container.repositories
        // Mantém o widget em dia com o histórico e a ficha ativa.
        container.appScope.launch {
            combine(repos.sessions.completedSessions, repos.plans.plans) { sessions, plans -> sessions to plans }
                .collectLatest { (sessions, plans) -> runCatching { WidgetUpdater.update(this@GymTrackApplication, sessions, plans) } }
        }
    }
}
