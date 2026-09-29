package app.gabrielaraujo.gymtrack.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId

/**
 * Registra os treinos concluídos no Health Connect (Google Fit, Samsung Health etc. leem de lá).
 * Opt-in: só grava com a integração ligada e a permissão concedida.
 */
class HealthConnectManager(context: Context) : WorkoutExporter {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("health_connect", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    val permissions: Set<String> = setOf(HealthPermission.getWritePermission(ExerciseSessionRecord::class))

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(appContext)

    val isAvailable: Boolean get() = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    private fun client(): HealthConnectClient? =
        if (isAvailable) HealthConnectClient.getOrCreate(appContext) else null

    suspend fun hasPermissions(): Boolean = try {
        client()?.permissionController?.getGrantedPermissions()?.containsAll(permissions) == true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }

    override suspend fun export(session: WorkoutSession) {
        exportAll(listOf(session))
    }

    /** Grava (ou atualiza, pelo id do treino) as sessões no Health Connect. @return quantas foram enviadas. */
    suspend fun exportAll(sessions: List<WorkoutSession>): Int {
        if (!_enabled.value) return 0
        val client = client() ?: return 0
        return try {
            if (!hasPermissions()) return 0
            val records = sessions.filter { it.completed && !it.deleted }.map { it.toRecord() }
            records.chunked(100).forEach { client.insertRecords(it) }
            records.size
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            0
        }
    }

    private fun WorkoutSession.toRecord(): ExerciseSessionRecord {
        val start = Instant.ofEpochMilli(startTime)
        val end = Instant.ofEpochMilli((endTime ?: startTime).coerceAtLeast(startTime + 60_000))
        val rules = ZoneId.systemDefault().rules
        return ExerciseSessionRecord(
            startTime = start,
            startZoneOffset = rules.getOffset(start),
            endTime = end,
            endZoneOffset = rules.getOffset(end),
            metadata = Metadata.manualEntry(clientRecordId = id, clientRecordVersion = updatedAt),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = "Treino $workoutDayLabel — $workoutDayName",
            notes = exercises.joinToString(", ") { it.name },
        )
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
    }
}
