package app.gabrielaraujo.gymtrack.health

import app.gabrielaraujo.gymtrack.data.WorkoutSession
import app.gabrielaraujo.gymtrack.data.repository.WorkoutSessionRepository
import app.gabrielaraujo.gymtrack.domain.health.BodyWeightEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Importa do Health Connect o peso corporal e, nos treinos recentes, a frequência cardíaca e as calorias.
 * Os dados do relógio chegam com atraso (o Samsung Health sincroniza aos poucos), então os treinos dos
 * últimos dias são consultados de novo a cada volta ao app, no máximo uma vez a cada poucos minutos.
 */
class HealthImporter(
    private val healthData: HealthDataSource,
    private val sessions: WorkoutSessionRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Evita duas leituras simultâneas do Health Connect (e consultas repetidas a cada volta ao app). */
    private val lock = Mutex()
    private var lastRefresh = 0L
    private val weights = MutableStateFlow<List<BodyWeightEntry>>(emptyList())

    /** Importação ligada pelo usuário. */
    val importEnabled: StateFlow<Boolean> = healthData.importEnabled

    /** Pesagens do Health Connect (mais antiga primeiro); vazio com a importação desligada. */
    val bodyWeights: Flow<List<BodyWeightEntry>> =
        combine(weights, healthData.importEnabled) { list, enabled -> if (enabled) list else emptyList() }

    /**
     * Busca pesagens e medidas dos treinos recentes. Só é permitido com o app em primeiro plano.
     * @param backfill busca também os treinos do último mês (ao ligar a importação), ignorando o intervalo.
     */
    suspend fun refresh(backfill: Boolean = false) = lock.withLock {
        val now = clock()
        if (!backfill && now - lastRefresh < REFRESH_INTERVAL_MS) return@withLock
        if (!healthData.canRead()) {
            weights.value = emptyList()
            return@withLock
        }
        lastRefresh = now
        weights.value = healthData.bodyWeights(since = now - WEIGHT_HISTORY_MS)
        val window = if (backfill) BACKFILL_MS else RECENT_MS
        sessions.completedSessionsSnapshot()
            .filter { (it.endTime ?: it.startTime) >= now - window }
            .forEach { enrichLocked(it) }
    }

    /** Medidas do relógio de um treino recém-concluído (o resto chega nas próximas voltas ao app). */
    suspend fun enrich(session: WorkoutSession): WorkoutSession? = lock.withLock { enrichLocked(session) }

    private suspend fun enrichLocked(session: WorkoutSession): WorkoutSession? {
        val end = session.endTime ?: return null
        val metrics = healthData.workoutMetrics(session.startTime, end) ?: return null
        return sessions.updateSessionHealthMetrics(session.id, metrics)
    }

    companion object {
        const val REFRESH_INTERVAL_MS = 5 * 60_000L
        const val RECENT_MS = 3 * 86_400_000L
        const val BACKFILL_MS = 30 * 86_400_000L
        const val WEIGHT_HISTORY_MS = 365 * 86_400_000L
    }
}
