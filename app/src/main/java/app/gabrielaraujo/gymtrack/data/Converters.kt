package app.gabrielaraujo.gymtrack.data

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Serializa listas aninhadas para JSON, já que o Room só persiste tipos primitivos. */
class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun stringListToJson(value: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> =
        json.decodeFromString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun daysToJson(value: List<WorkoutDay>): String =
        json.encodeToString(ListSerializer(WorkoutDay.serializer()), value)

    @TypeConverter
    fun jsonToDays(value: String): List<WorkoutDay> =
        json.decodeFromString(ListSerializer(WorkoutDay.serializer()), value)

    @TypeConverter
    fun sessionExercisesToJson(value: List<SessionExercise>): String =
        json.encodeToString(ListSerializer(SessionExercise.serializer()), value)

    @TypeConverter
    fun jsonToSessionExercises(value: String): List<SessionExercise> =
        json.decodeFromString(ListSerializer(SessionExercise.serializer()), value)
}
