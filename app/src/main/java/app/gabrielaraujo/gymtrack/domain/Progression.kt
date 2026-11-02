package app.gabrielaraujo.gymtrack.domain

import app.gabrielaraujo.gymtrack.data.SetRecord

/** Aumento de carga sugerido quando o topo da faixa de repetições é alcançado. */
const val PROGRESSION_INCREMENT_KG = 2.5

/**
 * Progressão de carga: com todas as séries do exercício concluídas e todas no topo da faixa
 * ([repMax] repetições ou mais), sugere a maior carga usada + [PROGRESSION_INCREMENT_KG].
 * @return a carga sugerida, ou null se ainda não é hora de subir.
 */
fun suggestedNextWeight(sets: List<SetRecord>, repMax: Int): Double? {
    if (repMax <= 0 || sets.isEmpty() || sets.any { !it.completed }) return null
    if (sets.any { it.reps < repMax }) return null
    return ((sets.maxOf { it.weight } + PROGRESSION_INCREMENT_KG) * 10).toInt() / 10.0
}
