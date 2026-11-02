package app.gabrielaraujo.gymtrack.health

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import app.gabrielaraujo.gymtrack.domain.health.BodyWeightEntry
import app.gabrielaraujo.gymtrack.domain.health.EnergySample
import app.gabrielaraujo.gymtrack.domain.health.WorkoutHealthMetrics
import app.gabrielaraujo.gymtrack.domain.health.caloriesDuring
import app.gabrielaraujo.gymtrack.domain.health.workoutNotes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId

/** Resultado do último envio ao Health Connect: quantos treinos foram gravados, ou o erro. */
data class ExportStatus(val at: Long, val count: Int, val error: String? = null)

/**
 * Integração com o Health Connect, por onde o GymTrack troca dados com o Samsung Health, Google Fit etc.
 * Os dois sentidos são opt-in e independentes:
 * - exportação: grava os treinos concluídos como sessões de exercício;
 * - importação: lê a frequência cardíaca e as calorias medidas pelo relógio durante o treino e o peso corporal.
 */
class HealthConnectManager(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : WorkoutExporter, HealthDataSource {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("health_connect", Context.MODE_PRIVATE)

    private val _exportEnabled = MutableStateFlow(prefs.getBoolean(KEY_EXPORT, false))
    val exportEnabled: StateFlow<Boolean> = _exportEnabled.asStateFlow()

    private val _importEnabled = MutableStateFlow(prefs.getBoolean(KEY_IMPORT, false))
    override val importEnabled: StateFlow<Boolean> = _importEnabled.asStateFlow()

    private val _exportStatus = MutableStateFlow(loadExportStatus())
    val exportStatus: StateFlow<ExportStatus?> = _exportStatus.asStateFlow()

    val writePermissions: Set<String> = setOf(HealthPermission.getWritePermission(ExerciseSessionRecord::class))

    val readPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
    )

    /** O treino é registrado em tempo real no celular (início e fim marcados no app). */
    private val phone = Device(type = Device.TYPE_PHONE, manufacturer = Build.MANUFACTURER, model = Build.MODEL)

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(appContext)

    val isAvailable: Boolean get() = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    private fun client(): HealthConnectClient? =
        if (isAvailable) HealthConnectClient.getOrCreate(appContext) else null

    suspend fun grantedPermissions(): Set<String> = try {
        client()?.permissionController?.getGrantedPermissions().orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptySet()
    }

    fun setExportEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_EXPORT, value).apply()
        _exportEnabled.value = value
    }

    fun setImportEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_IMPORT, value).apply()
        _importEnabled.value = value
    }

    /** Tela do Health Connect com os dados gravados pelos apps (para conferir os treinos enviados). */
    fun manageDataIntent(): Intent = HealthConnectClient.getHealthConnectManageDataIntent(appContext)

    // ---------------- Exportação ----------------

    override suspend fun export(session: WorkoutSession) {
        exportAll(listOf(session))
    }

    /** Grava (ou atualiza, pelo id do treino) as sessões no Health Connect. @return quantas foram enviadas. */
    suspend fun exportAll(sessions: List<WorkoutSession>): Int {
        if (!_exportEnabled.value) return 0
        val records = sessions.filter { it.completed && !it.deleted }.map { it.toRecord() }
        if (records.isEmpty()) return 0
        val ok = write { client -> records.chunked(100).forEach { client.insertRecords(it) } }
        if (!ok) return 0
        saveExportStatus(ExportStatus(clock(), records.size))
        return records.size
    }

    override suspend fun remove(sessionId: String) {
        if (!_exportEnabled.value) return
        write { client ->
            client.deleteRecords(ExerciseSessionRecord::class, recordIdsList = emptyList(), clientRecordIdsList = listOf(sessionId))
        }
    }

    /** Executa uma escrita no Health Connect; uma falha vira mensagem em [exportStatus] em vez de exceção. */
    private suspend fun write(block: suspend (HealthConnectClient) -> Unit): Boolean {
        val client = client() ?: return fail("Health Connect indisponível neste aparelho.")
        return try {
            if (!client.permissionController.getGrantedPermissions().containsAll(writePermissions)) {
                return fail("Sem permissão para gravar treinos no Health Connect.")
            }
            block(client)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun fail(message: String): Boolean {
        saveExportStatus(ExportStatus(clock(), 0, message))
        return false
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
            metadata = Metadata.activelyRecorded(device = phone, clientRecordId = id, clientRecordVersion = updatedAt),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = "Treino $workoutDayLabel — $workoutDayName",
            notes = workoutNotes(this).ifEmpty { null },
        )
    }

    private fun loadExportStatus(): ExportStatus? {
        val at = prefs.getLong(KEY_STATUS_AT, 0)
        if (at == 0L) return null
        return ExportStatus(at, prefs.getInt(KEY_STATUS_COUNT, 0), prefs.getString(KEY_STATUS_ERROR, null))
    }

    private fun saveExportStatus(status: ExportStatus) {
        prefs.edit()
            .putLong(KEY_STATUS_AT, status.at)
            .putInt(KEY_STATUS_COUNT, status.count)
            .putString(KEY_STATUS_ERROR, status.error)
            .apply()
        _exportStatus.value = status
    }

    // ---------------- Importação ----------------

    override suspend fun canRead(): Boolean = read { _, _ -> true } == true

    override suspend fun workoutMetrics(startTime: Long, endTime: Long): WorkoutHealthMetrics? = read { client, granted ->
        val range = TimeRangeFilter.between(Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime))
        var avg: Int? = null
        var max: Int? = null
        if (HealthPermission.getReadPermission(HeartRateRecord::class) in granted) {
            val hr = client.aggregate(
                AggregateRequest(setOf(HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MAX, HeartRateRecord.MEASUREMENTS_COUNT), range),
            )
            // Poucas medições (ex.: relógio medindo a cada 10 min) não representam o treino.
            if ((hr[HeartRateRecord.MEASUREMENTS_COUNT] ?: 0) >= MIN_HEART_RATE_SAMPLES) {
                avg = hr[HeartRateRecord.BPM_AVG]?.toInt()
                max = hr[HeartRateRecord.BPM_MAX]?.toInt()
            }
        }
        // Registros de calorias que começaram antes do treino também contam, na parte que se sobrepõe.
        val energyRange = TimeRangeFilter.between(Instant.ofEpochMilli(startTime - ENERGY_LOOKBACK_MS), Instant.ofEpochMilli(endTime))
        val active = if (HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted) {
            client.readRecords(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, energyRange)).records.map {
                EnergySample(it.startTime.toEpochMilli(), it.endTime.toEpochMilli(), it.energy.inKilocalories, it.metadata.dataOrigin.packageName)
            }
        } else emptyList()
        // O Samsung Health grava as calorias dos exercícios do relógio como "total".
        val total = if (HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class) in granted) {
            client.readRecords(ReadRecordsRequest(TotalCaloriesBurnedRecord::class, energyRange)).records.map {
                EnergySample(it.startTime.toEpochMilli(), it.endTime.toEpochMilli(), it.energy.inKilocalories, it.metadata.dataOrigin.packageName)
            }
        } else emptyList()
        val calories = caloriesDuring(active, startTime, endTime) ?: caloriesDuring(total, startTime, endTime)
        WorkoutHealthMetrics(avg, max, calories).takeIf { !it.isEmpty }
    }

    override suspend fun bodyWeights(since: Long): List<BodyWeightEntry> = read { client, granted ->
        if (HealthPermission.getReadPermission(WeightRecord::class) !in granted) return@read emptyList()
        val range = TimeRangeFilter.after(Instant.ofEpochMilli(since))
        val out = mutableListOf<BodyWeightEntry>()
        var token: String? = null
        do {
            val page = client.readRecords(
                ReadRecordsRequest(WeightRecord::class, range, ascendingOrder = true, pageSize = 1000, pageToken = token),
            )
            page.records.mapTo(out) { BodyWeightEntry(it.time.toEpochMilli(), it.weight.inKilograms) }
            token = page.pageToken
        } while (!token.isNullOrEmpty() && out.size < MAX_WEIGHT_ENTRIES)
        out
    }.orEmpty()

    /** Leitura com a importação ligada e ao menos uma permissão de leitura; sem isso, ou com erro, devolve null. */
    private suspend fun <T> read(block: suspend (client: HealthConnectClient, granted: Set<String>) -> T): T? {
        if (!_importEnabled.value) return null
        val client = client() ?: return null
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            if (granted.none { it in readPermissions }) null else block(client, granted)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val KEY_EXPORT = "enabled"
        const val KEY_IMPORT = "import_enabled"
        const val KEY_STATUS_AT = "export_status_at"
        const val KEY_STATUS_COUNT = "export_status_count"
        const val KEY_STATUS_ERROR = "export_status_error"
        const val MIN_HEART_RATE_SAMPLES = 3L
        const val ENERGY_LOOKBACK_MS = 2 * 3600_000L
        const val MAX_WEIGHT_ENTRIES = 5000
    }
}
