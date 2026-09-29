package app.gabrielaraujo.gymtrack.domain

import app.gabrielaraujo.gymtrack.data.Exercise
import app.gabrielaraujo.gymtrack.data.Goal
import app.gabrielaraujo.gymtrack.data.PersonalRecord
import app.gabrielaraujo.gymtrack.data.WorkoutPlan
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject

/** Conteúdo de um backup. Os nomes das tabelas seguem o dump do app web (Dexie). */
@Serializable
data class BackupTables(
    val workoutPlans: List<WorkoutPlan> = emptyList(),
    val workoutSessions: List<WorkoutSession> = emptyList(),
    val personalRecords: List<PersonalRecord> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
) {
    val size: Int get() = workoutPlans.size + workoutSessions.size + personalRecords.size + goals.size + exercises.size
}

@Serializable
data class BackupFile(
    val app: String = "GymTrack",
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val tables: BackupTables,
)

object BackupCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    /** Gera o JSON do backup, sem tombstones, sessões em andamento, nem campos de sincronização. */
    fun encode(tables: BackupTables, exportedAt: Long): String {
        val clean = BackupTables(
            workoutPlans = tables.workoutPlans.filter { !it.deleted }
                .map { it.copy(ownerId = null, dirty = false) },
            workoutSessions = tables.workoutSessions.filter { !it.deleted && it.completed }
                .map { it.copy(ownerId = null, dirty = false) },
            personalRecords = tables.personalRecords.filter { !it.deleted }
                .map { it.copy(ownerId = null, dirty = false) },
            goals = tables.goals.filter { !it.deleted }
                .map { it.copy(ownerId = null, dirty = false) },
            exercises = tables.exercises.filter { !it.deleted && it.isCustom }
                .map { it.copy(ownerId = null, dirty = false) },
        )
        return json.encodeToString(BackupFile.serializer(), BackupFile(exportedAt = exportedAt, tables = clean))
    }

    /**
     * Lê um backup deste app ou o dump do app web antigo.
     * Aceita `{ "tables": { ... } }` ou o objeto de tabelas cru. Linhas inválidas,
     * tombstones e sessões não concluídas são ignoradas.
     * @throws IllegalArgumentException se o texto não for um JSON de objeto.
     */
    fun decode(text: String): BackupTables {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }
            .getOrElse { throw IllegalArgumentException("JSON inválido", it) }
        val tables = (root["tables"] as? JsonObject) ?: root

        fun <T> rows(name: String, serializer: KSerializer<T>, accept: (JsonObject) -> Boolean = { true }): List<T> =
            (tables[name] as? JsonArray).orEmpty()
                .mapNotNull { it as? JsonObject }
                .filter { !isTombstone(it) && accept(it) }
                .mapNotNull { runCatching { json.decodeFromJsonElement(serializer, it) }.getOrNull() }

        return BackupTables(
            workoutPlans = rows("workoutPlans", WorkoutPlan.serializer()),
            workoutSessions = rows("workoutSessions", WorkoutSession.serializer()) { it.flag("completed") },
            personalRecords = rows("personalRecords", PersonalRecord.serializer()),
            goals = rows("goals", Goal.serializer()),
            exercises = rows("exercises", Exercise.serializer()).filter { it.isCustom },
        )
    }

    private fun JsonObject.flag(name: String): Boolean =
        (this[name] as? JsonPrimitive)?.booleanOrNull == true

    private fun isTombstone(o: JsonObject) = o.flag("_deleted") || o.flag("deleted")
}
