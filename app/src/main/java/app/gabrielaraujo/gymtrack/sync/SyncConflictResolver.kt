package app.gabrielaraujo.gymtrack.sync

import app.gabrielaraujo.gymtrack.data.WorkoutSession

/**
 * Estratégias de conflito por tipo de dado, aplicadas quando a versão remota vence o last-write-wins.
 *
 * O LWW compara `updatedAt`, que só avança com edições do usuário. O que é calculado ou medido fora
 * dessas edições é tratado à parte:
 * - recordes e `prsAchieved`: derivados do histórico, recalculados localmente depois de cada pull;
 * - próximo treino da ficha: derivado do histórico (ver `nextDayIndex`), não é gravado na ficha;
 * - frequência cardíaca e calorias: medidas do relógio, mescladas (nunca apagadas por uma versão sem elas);
 * - ficha ativa: duas ativas vindas de aparelhos diferentes são resolvidas na leitura (ver `activePlanOf`);
 * - exclusões: tombstones, que seguem o mesmo LWW (uma edição mais nova recupera o registro).
 */
object SyncConflictResolver {
    /** Treino: conteúdo da versão remota; medidas do relógio que só o aparelho tem são preservadas. */
    fun session(local: WorkoutSession, remote: WorkoutSession): WorkoutSession = remote.copy(
        avgHeartRate = remote.avgHeartRate ?: local.avgHeartRate,
        maxHeartRate = remote.maxHeartRate ?: local.maxHeartRate,
        calories = remote.calories ?: local.calories,
    )
}
