package app.gabrielaraujo.gymtrack.data

import java.text.Normalizer

/*
 * Identidade e exibição dos exercícios.
 *
 * O `exerciseId` é a chave estável que fichas (JSON), backup, nuvem, treinos, recordes e metas
 * guardam: ele nunca muda. Nome e ilustração são só exibição, resolvidos pelo id na hora de mostrar.
 */

/** Forma canônica de um id escrito à mão: minúsculas, sem acento, `_` no lugar de espaço, hífen e símbolos. */
fun normalizeExerciseId(raw: String): String =
    Normalizer.normalize(raw.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')

/** Outras grafias comuns (já normalizadas) de exercícios nativos, apontando para o id canônico. */
val exerciseAliases: Map<String, String> = mapOf(
    // Peito
    "supino" to "supino_reto",
    "supino_reto_barra" to "supino_reto",
    "supino_inclinado_barra" to "supino_inclinado",
    "supino_reto_halteres" to "supino_halteres",
    "crucifixo_halteres" to "crucifixo",
    "voador" to "peck_deck",
    "pec_deck" to "peck_deck",
    "flexao_de_braco" to "flexao",
    "paralelas" to "mergulho",
    "dips" to "mergulho",
    // Costas
    "pull_up" to "barra_fixa",
    "puxada_frente" to "pulley_frente",
    "puxada_frontal" to "pulley_frente",
    "puxada_alta" to "pulley_frente",
    "pulldown" to "pulley_frente",
    "puxada_triangulo" to "pulley_apertado",
    "pulley_triangulo" to "pulley_apertado",
    "remada_unilateral" to "remada_serrote",
    "remada_baixa" to "remada_cabo",
    "remada_sentada" to "remada_cabo",
    "terra" to "levantamento_terra",
    "deadlift" to "levantamento_terra",
    // Perna
    "agachamento_livre" to "agachamento",
    "agachamento_barra" to "agachamento",
    "squat" to "agachamento",
    "hack" to "agachamento_hack",
    "hack_squat" to "agachamento_hack",
    "leg_press_45" to "leg_press",
    "cadeira_extensora" to "extensao_joelho",
    "extensora" to "extensao_joelho",
    "mesa_flexora" to "flexao_joelho",
    "cadeira_flexora" to "flexao_joelho",
    "flexora" to "flexao_joelho",
    "passada" to "afundo",
    "avanco" to "afundo",
    "panturrilha" to "elevacao_panturrilha",
    "panturrilha_em_pe" to "elevacao_panturrilha",
    "adutora" to "cadeira_adutora",
    // Ombros e braços
    "desenvolvimento_militar" to "desenvolvimento_barra",
    "rosca_barra" to "rosca_direta",
    "rosca_alternada" to "rosca_halteres",
    // Abdômen
    "abdominal" to "abdominal_crunch",
    "abdominal_supra" to "abdominal_crunch",
    "crunch" to "abdominal_crunch",
)

/**
 * Id da biblioteca correspondente a [raw]: o próprio, sua forma normalizada ou um apelido conhecido.
 * Sem correspondência, devolve [raw] intacto (quem chama decide como exibir um id desconhecido).
 */
fun resolveExerciseId(raw: String, knownIds: Set<String>): String {
    if (raw in knownIds) return raw
    val normalized = normalizeExerciseId(raw)
    if (normalized in knownIds) return normalized
    val alias = exerciseAliases[normalized]
    return if (alias != null && alias in knownIds) alias else raw
}

/** Palavras que perdem o acento num id e são comuns em nomes de exercício. */
private val accentedWords = mapOf(
    "abdomen" to "abdômen",
    "abducao" to "abdução",
    "aducao" to "adução",
    "biceps" to "bíceps",
    "elevacao" to "elevação",
    "extensao" to "extensão",
    "flexao" to "flexão",
    "frances" to "francês",
    "gluteo" to "glúteo",
    "gluteos" to "glúteos",
    "hiperextensao" to "hiperextensão",
    "maquina" to "máquina",
    "obliquo" to "oblíquo",
    "pe" to "pé",
    "quadriceps" to "quadríceps",
    "rotacao" to "rotação",
    "triceps" to "tríceps",
)

/** Preposições e artigos que ficam em minúscula no meio do nome. */
private val lowercaseWords = setOf("a", "o", "e", "de", "da", "do", "das", "dos", "em", "na", "no", "nas", "nos", "com")

/** Nome legível a partir de um id: `agachamento_hack` → "Agachamento Hack", `extensao_de_joelho` → "Extensão de Joelho". */
fun humanizeExerciseId(id: String): String {
    val words = id.split('_', '-', ' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return id
    return words.mapIndexed { i, raw ->
        val word = raw.lowercase().let { accentedWords[it] ?: it }
        if (i > 0 && word in lowercaseWords) word else word.replaceFirstChar { it.titlecase() }
    }.joinToString(" ")
}

/**
 * Nome de exibição de um exercício: o atual da biblioteca; senão o [snapshot] gravado
 * (ex.: no treino, quando o exercício personalizado foi apagado); senão o id humanizado.
 * Um snapshot igual ao id (treinos antigos que gravaram o id cru) também é humanizado.
 */
fun exerciseDisplayName(id: String, byId: Map<String, Exercise>, snapshot: String? = null): String =
    byId[id]?.name
        ?: snapshot?.takeIf { it.isNotBlank() && it != id }
        ?: humanizeExerciseId(id)
