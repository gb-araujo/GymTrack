package app.gabrielaraujo.gymtrack.data

/** Biblioteca de exercícios embutida (portada de src/data/exercises.ts). */
val builtinExercises: List<Exercise> = listOf(
    // PEITO
    Exercise("supino_reto", "Supino Reto", "Peito", listOf("Tríceps", "Ombro Anterior"), "Barra", "Empurrar Horizontal", "Composto", "intermediate", "Deite no banco, segure a barra com pegada levemente mais larga que os ombros, desça até o peito e empurre para cima."),
    Exercise("supino_inclinado", "Supino Inclinado", "Peito Superior", listOf("Tríceps", "Ombro Anterior"), "Barra", "Empurrar Inclinado", "Composto", "intermediate"),
    Exercise("supino_halteres", "Supino com Halteres", "Peito", listOf("Tríceps", "Ombro Anterior"), "Halteres", "Empurrar Horizontal", "Composto", "intermediate"),
    Exercise("supino_maquina", "Supino Máquina", "Peito", listOf("Tríceps", "Ombro Anterior"), "Máquina", "Empurrar Horizontal", "Composto", "beginner"),
    Exercise("crucifixo", "Crucifixo", "Peito", listOf("Ombro Anterior"), "Halteres", "Adução Horizontal", "Isolado", "intermediate"),
    Exercise("crucifixo_maquina", "Crucifixo na Máquina", "Peito", emptyList(), "Máquina", "Adução Horizontal", "Isolado", "beginner"),
    Exercise("peck_deck", "Peck Deck", "Peito", emptyList(), "Máquina", "Adução Horizontal", "Isolado", "beginner"),
    Exercise("flexao", "Flexão de Braço", "Peito", listOf("Tríceps", "Ombro Anterior"), "Peso Corporal", "Empurrar Horizontal", "Composto", "beginner"),
    Exercise("mergulho", "Mergulho (Dips)", "Peito Inferior", listOf("Tríceps", "Ombro Anterior"), "Paralelas", "Empurrar Vertical", "Composto", "intermediate"),
    // COSTAS
    Exercise("barra_fixa", "Barra Fixa", "Costas", listOf("Bíceps", "Ombro Posterior"), "Peso Corporal", "Puxar Vertical", "Composto", "intermediate"),
    Exercise("remada_curvada", "Remada Curvada", "Costas", listOf("Bíceps", "Lombar"), "Barra", "Puxar Horizontal", "Composto", "intermediate"),
    Exercise("remada_serrote", "Remada Serrote", "Costas", listOf("Bíceps"), "Halteres", "Puxar Horizontal", "Composto", "beginner"),
    Exercise("pulley_frente", "Pulley Frente", "Costas", listOf("Bíceps"), "Cabo", "Puxar Vertical", "Composto", "beginner"),
    Exercise("pulley_apertado", "Pulley Triângulo", "Costas", listOf("Bíceps"), "Cabo", "Puxar Vertical", "Composto", "beginner"),
    Exercise("remada_maquina", "Remada na Máquina", "Costas", listOf("Bíceps"), "Máquina", "Puxar Horizontal", "Composto", "beginner"),
    Exercise("levantamento_terra", "Levantamento Terra", "Lombar", listOf("Costas", "Glúteo", "Posteriores de Coxa"), "Barra", "Dobradiça de Quadril", "Composto", "advanced"),
    Exercise("remada_cabo", "Remada no Cabo", "Costas", listOf("Bíceps"), "Cabo", "Puxar Horizontal", "Composto", "beginner"),
    Exercise("hiperextensao", "Hiperextensão", "Lombar", listOf("Glúteo"), "Banco Romano", "Extensão de Quadril", "Isolado", "beginner"),
    // PERNA
    Exercise("agachamento", "Agachamento Livre", "Quadríceps", listOf("Glúteo", "Posteriores de Coxa", "Lombar"), "Barra", "Empurrar Vertical", "Composto", "advanced"),
    Exercise("leg_press", "Leg Press 45°", "Quadríceps", listOf("Glúteo", "Posteriores de Coxa"), "Máquina", "Empurrar Vertical", "Composto", "beginner"),
    Exercise("extensao_joelho", "Extensão de Joelho", "Quadríceps", emptyList(), "Máquina", "Extensão de Joelho", "Isolado", "beginner"),
    Exercise("flexao_joelho", "Flexão de Joelho", "Posteriores de Coxa", emptyList(), "Máquina", "Flexão de Joelho", "Isolado", "beginner"),
    Exercise("stiff", "Stiff", "Posteriores de Coxa", listOf("Lombar", "Glúteo"), "Barra", "Dobradiça de Quadril", "Composto", "intermediate"),
    Exercise("afundo", "Afundo", "Quadríceps", listOf("Glúteo", "Posteriores de Coxa"), "Halteres", "Empurrar Unilateral", "Composto", "intermediate"),
    Exercise("elevacao_panturrilha", "Elevação de Panturrilha", "Panturrilha", emptyList(), "Máquina", "Extensão Plantar", "Isolado", "beginner"),
    Exercise("cadeira_adutora", "Cadeira Adutora", "Adutores", emptyList(), "Máquina", "Adução de Quadril", "Isolado", "beginner"),
    Exercise("agachamento_hack", "Hack Squat", "Quadríceps", listOf("Glúteo"), "Máquina", "Empurrar Vertical", "Composto", "intermediate"),
    // OMBROS
    Exercise("desenvolvimento_barra", "Desenvolvimento com Barra", "Ombro", listOf("Tríceps"), "Barra", "Empurrar Vertical", "Composto", "intermediate"),
    Exercise("desenvolvimento_halteres", "Desenvolvimento com Halteres", "Ombro", listOf("Tríceps"), "Halteres", "Empurrar Vertical", "Composto", "intermediate"),
    Exercise("elevacao_lateral", "Elevação Lateral", "Ombro Lateral", emptyList(), "Halteres", "Abdução de Ombro", "Isolado", "beginner"),
    Exercise("elevacao_frontal", "Elevação Frontal", "Ombro Anterior", emptyList(), "Halteres", "Flexão de Ombro", "Isolado", "beginner"),
    Exercise("remada_alta", "Remada Alta", "Ombro", listOf("Trapézio"), "Barra", "Puxar Vertical", "Composto", "intermediate"),
    Exercise("face_pull", "Face Pull", "Ombro Posterior", listOf("Trapézio"), "Cabo", "Puxar Horizontal", "Isolado", "beginner"),
    // BÍCEPS
    Exercise("rosca_direta", "Rosca Direta", "Bíceps", listOf("Braquial"), "Barra", "Flexão de Cotovelo", "Isolado", "beginner"),
    Exercise("rosca_halteres", "Rosca com Halteres", "Bíceps", listOf("Braquial"), "Halteres", "Flexão de Cotovelo", "Isolado", "beginner"),
    Exercise("rosca_martelo", "Rosca Martelo", "Braquial", listOf("Bíceps"), "Halteres", "Flexão de Cotovelo", "Isolado", "beginner"),
    Exercise("rosca_scott", "Rosca Scott", "Bíceps", emptyList(), "Barra", "Flexão de Cotovelo", "Isolado", "intermediate"),
    Exercise("rosca_cabo", "Rosca no Cabo", "Bíceps", emptyList(), "Cabo", "Flexão de Cotovelo", "Isolado", "beginner"),
    // TRÍCEPS
    Exercise("triceps_corda", "Tríceps Corda", "Tríceps", emptyList(), "Cabo", "Extensão de Cotovelo", "Isolado", "beginner"),
    Exercise("triceps_testa", "Tríceps Testa", "Tríceps", emptyList(), "Barra", "Extensão de Cotovelo", "Isolado", "intermediate"),
    Exercise("triceps_frances", "Tríceps Francês", "Tríceps", emptyList(), "Halteres", "Extensão de Cotovelo", "Isolado", "beginner"),
    Exercise("triceps_maquina", "Tríceps na Máquina", "Tríceps", emptyList(), "Máquina", "Extensão de Cotovelo", "Isolado", "beginner"),
    // ABDÔMEN
    Exercise("abdominal_crunch", "Abdominal Crunch", "Abdômen", emptyList(), "Peso Corporal", "Flexão de Tronco", "Isolado", "beginner"),
    Exercise("prancha", "Prancha", "Abdômen", listOf("Lombar"), "Peso Corporal", "Estabilização", "Isolado", "beginner"),
)

/** Sugere exercícios similares por músculo/padrão de movimento (portado de getSuggestions). */
fun getSuggestions(exerciseId: String, all: List<Exercise>): List<Exercise> {
    val target = all.find { it.id == exerciseId } ?: return emptyList()
    return all.asSequence()
        .filter { it.id != exerciseId }
        .map { e ->
            var score = 0
            if (e.primaryMuscle == target.primaryMuscle) score += 3
            if (e.movementPattern == target.movementPattern) score += 2
            if (target.secondaryMuscles.any { m -> e.primaryMuscle == m || e.secondaryMuscles.contains(m) }) score += 1
            e to score
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(6)
        .map { it.first }
        .toList()
}

/** Ficha exemplo "Hipertrofia" A/B/C (portada de samplePlan.ts). */
fun buildSamplePlan(now: Long): WorkoutPlan = WorkoutPlan(
    id = "sample_hipertrofia",
    name = "Hipertrofia",
    currentDayIndex = 0,
    createdAt = now,
    updatedAt = now,
    isActive = true,
    days = listOf(
        WorkoutDay(
            id = "day_a", label = "A", name = "Peito", order = 0, estimatedMinutes = 55,
            exercises = listOf(
                WorkoutExercise("we_supino_reto", "supino_reto", 4, 8, 10, 120, 60.0, order = 0),
                WorkoutExercise("we_supino_inclinado", "supino_inclinado", 3, 10, 12, 90, 50.0, order = 1),
                WorkoutExercise("we_crucifixo", "crucifixo", 3, 12, 15, 60, 14.0, order = 2),
                WorkoutExercise("we_peck_deck", "peck_deck", 3, 12, 15, 60, 40.0, order = 3),
                WorkoutExercise("we_triceps_corda", "triceps_corda", 3, 12, 15, 60, 25.0, order = 4),
                WorkoutExercise("we_triceps_testa", "triceps_testa", 3, 10, 12, 60, 20.0, order = 5),
            ),
        ),
        WorkoutDay(
            id = "day_b", label = "B", name = "Costas", order = 1, estimatedMinutes = 60,
            exercises = listOf(
                WorkoutExercise("we_barra_fixa", "barra_fixa", 4, 6, 10, 120, 0.0, order = 0),
                WorkoutExercise("we_remada_curvada", "remada_curvada", 4, 8, 10, 120, 60.0, order = 1),
                WorkoutExercise("we_pulley_frente", "pulley_frente", 3, 10, 12, 90, 50.0, order = 2),
                WorkoutExercise("we_remada_serrote", "remada_serrote", 3, 10, 12, 90, 24.0, order = 3),
                WorkoutExercise("we_remada_cabo", "remada_cabo", 3, 12, 15, 60, 35.0, order = 4),
                WorkoutExercise("we_rosca_direta", "rosca_direta", 3, 10, 12, 60, 30.0, order = 5),
                WorkoutExercise("we_rosca_martelo", "rosca_martelo", 3, 12, 15, 60, 12.0, order = 6),
            ),
        ),
        WorkoutDay(
            id = "day_c", label = "C", name = "Perna", order = 2, estimatedMinutes = 65,
            exercises = listOf(
                WorkoutExercise("we_agachamento", "agachamento", 4, 8, 10, 150, 80.0, order = 0),
                WorkoutExercise("we_leg_press", "leg_press", 4, 10, 12, 120, 120.0, order = 1),
                WorkoutExercise("we_extensao_joelho", "extensao_joelho", 3, 12, 15, 60, 40.0, order = 2),
                WorkoutExercise("we_flexao_joelho", "flexao_joelho", 3, 12, 15, 60, 35.0, order = 3),
                WorkoutExercise("we_stiff", "stiff", 3, 10, 12, 90, 50.0, order = 4),
                WorkoutExercise("we_elevacao_panturrilha", "elevacao_panturrilha", 4, 15, 20, 45, 60.0, order = 5),
                WorkoutExercise("we_cadeira_adutora", "cadeira_adutora", 3, 15, 20, 45, 30.0, order = 6),
            ),
        ),
    ),
)
