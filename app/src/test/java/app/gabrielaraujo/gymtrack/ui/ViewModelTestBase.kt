package app.gabrielaraujo.gymtrack.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import app.gabrielaraujo.gymtrack.testing.TestApp
import app.gabrielaraujo.gymtrack.ui.home.HomeViewModel
import app.gabrielaraujo.gymtrack.ui.workout.WorkoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import java.util.concurrent.Executors

/** Base dos testes de ViewModel: Dispatchers.Main de verdade (uma thread) e o app montado com fakes. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class ViewModelTestBase {
    private lateinit var main: ExecutorCoroutineDispatcher
    protected lateinit var app: TestApp
    private val vms = mutableListOf<ViewModel>()

    /** Usuário logado no início do teste (null = anônimo). */
    protected open val initialUid: String? = null

    @Before
    fun setUpApp() {
        main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(main)
        app = TestApp(uid = initialUid)
    }

    @After
    fun tearDownApp() {
        // Espera as corrotinas dos ViewModels terminarem: uma ainda rodando poderia ler o
        // Dispatchers.Main enquanto o próximo teste o troca (IllegalStateException no setMain).
        runBlocking { vms.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } }
        app.idle()
        app.close()
        Dispatchers.resetMain()
        main.close()
    }

    protected fun <T : ViewModel> track(vm: T): T = vm.also { vms += it }

    protected fun <S> StateFlow<S>.await(predicate: (S) -> Boolean): S =
        runBlocking { withTimeout(10_000) { first(predicate) } }

    protected fun <T : Any> eventually(block: suspend () -> T?): T = runBlocking {
        withTimeout(10_000) {
            var value = block()
            while (value == null) {
                delay(20)
                value = block()
            }
            value
        }
    }

    /** Inicia um treino pela tela inicial, depois de ela carregar a ficha ativa. */
    protected fun HomeViewModel.startWhenLoaded(dayIndex: Int? = null) {
        state.await { it.activePlan != null }
        startSession(dayIndex)
    }

    /** Faz um treino completo do dia [dayIndex], com a carga informada na 1ª série do 1º exercício. */
    protected fun doWorkout(firstWeight: Double = 50.0, dayIndex: Int = 0): WorkoutSession {
        val home: HomeViewModel = track(app.homeVm())
        val workout: WorkoutViewModel = track(app.workoutVm())
        home.startWhenLoaded(dayIndex)
        val active = workout.state.await { it.active != null }.active!!
        workout.updateSet(0, 0) { it.copy(weight = firstWeight) }
        active.session.exercises.indices.forEach { workout.completeExercise(it) }
        app.clock.advanceMinutes(45)
        workout.complete()
        val done = workout.state.await { it.active?.session?.completed == true }.active!!.session
        workout.dismissSummary()
        workout.state.await { it.active == null }
        app.idle()
        return done
    }
}
