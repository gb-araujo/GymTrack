package app.gabrielaraujo.gymtrack.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.gabrielaraujo.gymtrack.GymTrackApplication
import java.util.concurrent.TimeUnit

/** Agenda sincronizações em segundo plano. Abstraído para os ViewModels serem testáveis sem WorkManager. */
interface SyncScheduler {
    /** Pede uma sincronização: pedidos próximos são agrupados e ela só roda com rede. */
    fun request()

    /** Cancela as sincronizações agendadas (ex.: logout). */
    fun cancel()

    object None : SyncScheduler {
        override fun request() = Unit
        override fun cancel() = Unit
    }
}

/**
 * [SyncScheduler] via WorkManager: o pedido sobrevive ao fechamento do app, espera a rede voltar e,
 * se falhar, tenta de novo com backoff exponencial. O trabalho é único, então duas sincronizações
 * agendadas nunca rodam ao mesmo tempo (e o [SyncEngine] ainda serializa com um mutex).
 */
class WorkManagerSyncScheduler(context: Context) : SyncScheduler {
    private val workManager by lazy { WorkManager.getInstance(context.applicationContext) }

    override fun request() {
        val work = OneTimeWorkRequestBuilder<SyncWorker>()
            // Agrupa uma sequência de edições numa só ida à rede (REPLACE reinicia a espera).
            .setInitialDelay(DEBOUNCE_MS, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, work)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "gymtrack-sync"
        const val DEBOUNCE_MS = 1_200L
        const val BACKOFF_SECONDS = 30L
    }
}

/** Uma rodada de sincronização. Interrompê-la no meio é seguro: envio e recebimento são idempotentes. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as GymTrackApplication).container
        val uid = container.authManager.currentUid ?: return Result.success()
        return when (container.syncEngine.sync(uid)) {
            SyncResult.Success -> Result.success()
            // As pendências continuam marcadas no banco; a próxima edição ou abertura do app agenda de novo.
            is SyncResult.Failure -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 8
    }
}
