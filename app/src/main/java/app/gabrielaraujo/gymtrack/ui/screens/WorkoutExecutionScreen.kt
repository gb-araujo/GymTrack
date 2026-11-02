package app.gabrielaraujo.gymtrack.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gabrielaraujo.gymtrack.data.SetRecord
import app.gabrielaraujo.gymtrack.ui.GymTrackViewModel
import app.gabrielaraujo.gymtrack.ui.components.BtnVariant
import app.gabrielaraujo.gymtrack.ui.components.NumberStepper
import app.gabrielaraujo.gymtrack.ui.components.GymTrackButton
import app.gabrielaraujo.gymtrack.ui.formatWeight
import app.gabrielaraujo.gymtrack.ui.theme.TC
import app.gabrielaraujo.gymtrack.ui.timeAgo
import kotlinx.coroutines.delay

/** Pede permissão de notificação (Android 13+) para o aviso de fim de descanso. */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private fun clock(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

@Composable
fun WorkoutExecutionScreen(vm: GymTrackViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val active = state.activeSession ?: return
    val session = active.session
    val idx = active.currentExerciseIndex.coerceIn(0, (session.exercises.size - 1).coerceAtLeast(0))
    val current = session.exercises.getOrNull(idx) ?: return
    val total = session.exercises.size
    val completedExercises = session.exercises.count { it.completed }

    RequestNotificationPermission()

    var showSubstitute by remember { mutableStateOf(false) }
    var showAbort by remember { mutableStateOf(false) }
    var progressionSuggestion by remember(idx) { mutableStateOf<Double?>(null) }

    val dayExercise = state.activePlan?.days?.find { it.id == session.workoutDayId }
        ?.exercises?.find { it.id == current.workoutExerciseId }
    val restDefault = dayExercise?.restSeconds ?: 90

    val lastExercise = active.lastSessionForDay?.exercises?.find { it.exerciseId == current.exerciseId }
    val lastCompletedSets = lastExercise?.sets?.filter { it.completed } ?: emptyList()
    val lastBestSet = lastCompletedSets.maxByOrNull { it.weight }

    val firstIncomplete = current.sets.indexOfFirst { !it.completed }
    val completedSetCount = current.sets.count { it.completed }
    val allDone = completedExercises == total

    fun checkProgression(updatedSets: List<SetRecord>) {
        val repMax = dayExercise?.repMax ?: 0
        if (repMax == 0) { progressionSuggestion = null; return }
        val completed = updatedSets.filter { it.completed }
        if (completed.isEmpty() || completed.size < updatedSets.size) return
        progressionSuggestion = if (completed.all { it.reps >= repMax }) {
            val maxW = completed.maxOf { it.weight }
            ((maxW + 2.5) * 10).toInt() / 10.0
        } else null
    }

    fun completeSet(setIndex: Int) {
        val s = current.sets[setIndex]
        if (s.completed) {
            vm.updateSetRecord(idx, setIndex) { it.copy(completed = false) }
            progressionSuggestion = null
        } else {
            // O horário da série permite mostrar, no histórico, o descanso real entre as séries.
            vm.updateSetRecord(idx, setIndex) { it.copy(completed = true, timestamp = System.currentTimeMillis()) }
            vm.startRestTimer(restDefault, current.id)
            checkProgression(current.sets.mapIndexed { i, set -> if (i == setIndex) set.copy(completed = true) else set })
        }
    }

    Column(Modifier.fillMaxSize().background(TC.Background)) {
        // Top bar
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Icon(Icons.Default.Close, "Sair", tint = TC.Slate400, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { showAbort = true }.padding(6.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Treino ${session.workoutDayLabel} — ${session.workoutDayName}", color = TC.Slate500, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text("$completedExercises/$total exercícios", color = TC.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            ElapsedTimer(session.startTime)
        }

        // Progress bar
        Box(Modifier.fillMaxWidth().height(4.dp).background(TC.Slate800)) {
            Box(Modifier.fillMaxWidth(if (total == 0) 0f else completedExercises.toFloat() / total).height(4.dp).background(TC.Indigo500))
        }

        // Tabs
        LazyRow(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(session.exercises) { ex ->
                val i = session.exercises.indexOf(ex)
                val isActive = i == idx
                val done = ex.completed
                val bg = if (isActive) TC.Indigo600 else if (done) TC.Emerald600.copy(alpha = 0.25f) else TC.Slate800
                val fg = if (isActive) TC.White else if (done) TC.Emerald400 else TC.Slate400
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(bg).clickable { vm.setCurrentExercise(i) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val top = if (done) "✓" else if (isActive) "${ex.sets.count { it.completed }}/${ex.sets.size}" else "${i + 1}"
                    Text(top, color = fg, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(ex.name.split(" ").first(), color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // Rest timer
            val restEndsAt = active.restEndsAt
            val rest = restEndsAt?.let { rememberSecondsUntil(it) }
            if (rest != null && rest > 0) {
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(16.dp)).background(TC.Slate900).padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Timer, null, tint = TC.Indigo400, modifier = Modifier.size(18.dp))
                            Text("Descanso", color = TC.Slate400, fontSize = 14.sp)
                        }
                        Text(clock(rest), color = TC.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RestBtn("−15s", Modifier.weight(1f)) { vm.adjustRestTimer(-15) }
                        RestBtn("Pular", Modifier.weight(1f)) { vm.stopRestTimer() }
                        RestBtn("+15s", Modifier.weight(1f)) { vm.adjustRestTimer(15) }
                    }
                }
            }

            // Exercise header
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (current.completed) Badge("✓ Concluído", TC.Emerald400, TC.Emerald600.copy(alpha = 0.3f))
                        if (current.substitutedFrom != null) Badge("Substituído", TC.Slate400, TC.Slate800)
                    }
                    Text(current.name, color = TC.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    dayExercise?.let {
                        Text("${it.sets} séries · ${it.repMin}–${it.repMax} reps · ${it.restSeconds}s", color = TC.Slate500, fontSize = 14.sp)
                    }
                    if (lastBestSet != null && active.lastSessionForDay != null) {
                        Text(
                            "Última: ${formatWeight(lastBestSet.weight)}kg × ${lastBestSet.reps} · ${timeAgo(active.lastSessionForDay!!.startTime)}",
                            color = TC.Indigo400, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                Icon(Icons.Default.SwapHoriz, "Substituir", tint = TC.Slate500, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { showSubstitute = true }.padding(6.dp))
            }

            // Set dots
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                current.sets.forEachIndexed { i, s ->
                    val sizeDp = if (!s.completed && i == firstIncomplete) 16.dp else 12.dp
                    val color = when {
                        s.completed -> TC.Emerald500
                        i == firstIncomplete -> TC.Indigo500
                        else -> TC.Slate700
                    }
                    Box(Modifier.padding(horizontal = 4.dp).size(sizeDp).clip(CircleShape).background(color))
                }
            }

            // Sets
            current.sets.forEachIndexed { i, set ->
                SetRow(
                    set = set,
                    index = i,
                    isCurrent = i == firstIncomplete,
                    previous = lastCompletedSets.getOrNull(i),
                    canRemove = current.sets.size > 1,
                    onComplete = { completeSet(i) },
                    onWeight = { w -> vm.updateSetRecord(idx, i) { it.copy(weight = w) } },
                    onReps = { r -> vm.updateSetRecord(idx, i) { it.copy(reps = r) } },
                    onRemove = { vm.removeSet(idx, i) },
                )
                Spacer(Modifier.height(12.dp))
            }

            // Add set
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.addSet(idx) }.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Add, null, tint = TC.Slate500, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Adicionar série", color = TC.Slate500, fontSize = 14.sp)
            }

            // Progression suggestion
            progressionSuggestion?.let { sug ->
                if (!current.completed) {
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(TC.Emerald600.copy(alpha = 0.15f)).padding(16.dp)) {
                        Text("Todas as séries no máximo!", color = TC.Emerald400, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Sugestão: aumente para ${formatWeight(sug)}kg no próximo treino.", color = TC.Slate400, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GymTrackButton("Aceitar +2.5kg", onClick = { vm.updateExerciseInitialWeight(current.workoutExerciseId, sug); progressionSuggestion = null }, variant = BtnVariant.Success, modifier = Modifier.weight(1f))
                            GymTrackButton("Manter", onClick = { progressionSuggestion = null }, variant = BtnVariant.Ghost, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Complete / edit exercise
            if (!current.completed) {
                GymTrackButton(
                    "Concluir Exercício" + if (completedSetCount > 0) " ($completedSetCount/${current.sets.size})" else "",
                    onClick = {
                        vm.completeExercise(idx); vm.stopRestTimer(); progressionSuggestion = null
                        if (idx < total - 1) vm.setCurrentExercise(idx + 1)
                    },
                    variant = BtnVariant.Success, modifier = Modifier.fillMaxWidth(),
                    leading = { Icon(Icons.Default.Check, null, tint = TC.White, modifier = Modifier.size(18.dp)) },
                )
            } else {
                GymTrackButton("Editar exercício", onClick = { vm.uncompleteExercise(idx) }, variant = BtnVariant.Ghost, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(16.dp))

            // Nav
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                NavArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, idx > 0) { vm.setCurrentExercise(idx - 1) }
                Text("${idx + 1} / $total", color = TC.Slate600, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
                NavArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, idx < total - 1) { vm.setCurrentExercise(idx + 1) }
            }

            Spacer(Modifier.height(16.dp))

            if (allDone) {
                GymTrackButton(
                    "Finalizar Treino", onClick = { vm.completeSession() }, modifier = Modifier.fillMaxWidth(),
                    leading = { Icon(Icons.Default.EmojiEvents, null, tint = TC.White, modifier = Modifier.size(20.dp)) },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showSubstitute) {
        ExerciseSubstituteDialog(vm, current.exerciseId, current.name, idx) { showSubstitute = false }
    }
    if (showAbort) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showAbort = false }) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TC.Slate900).padding(24.dp)) {
                Text("Abandonar treino?", color = TC.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("O progresso deste treino não será salvo.", color = TC.Slate400, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GymTrackButton("Continuar", onClick = { showAbort = false }, variant = BtnVariant.Ghost, modifier = Modifier.weight(1f))
                    GymTrackButton("Abandonar", onClick = { showAbort = false; vm.abandonSession() }, variant = BtnVariant.Danger, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Segundos restantes até [endsAt], atualizados a cada segundo (0 quando acabou). */
@Composable
private fun rememberSecondsUntil(endsAt: Long): Int {
    val remaining by produceState(secondsUntil(endsAt), endsAt) {
        while (true) {
            value = secondsUntil(endsAt)
            if (value <= 0) break
            delay(250)
        }
    }
    return remaining
}

private fun secondsUntil(endsAt: Long): Int =
    ((endsAt - System.currentTimeMillis() + 999) / 1000).toInt().coerceAtLeast(0)

@Composable
private fun ElapsedTimer(startTime: Long) {
    val elapsed by produceState(0, startTime) {
        while (true) {
            value = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            delay(1000)
        }
    }
    Text(clock(elapsed), color = TC.Slate400, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(TC.Slate900).padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
private fun Badge(text: String, fg: Color, bg: Color) {
    Text(text, color = fg, fontSize = 11.sp, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp))
}

@Composable
private fun RestBtn(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(text, color = TC.Slate300, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = modifier.clip(RoundedCornerShape(12.dp)).background(TC.Slate800).clickable { onClick() }.padding(vertical = 10.dp))
}

@Composable
private fun NavArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Icon(
        icon, null, tint = if (enabled) TC.Slate400 else TC.Slate700,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(TC.Slate900).let { if (enabled) it.clickable { onClick() } else it }.padding(10.dp),
    )
}

@Composable
private fun SetRow(
    set: SetRecord,
    index: Int,
    isCurrent: Boolean,
    previous: SetRecord?,
    canRemove: Boolean,
    onComplete: () -> Unit,
    onWeight: (Double) -> Unit,
    onReps: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val border = when {
        set.completed -> TC.Emerald600.copy(alpha = 0.4f)
        isCurrent -> TC.Indigo500.copy(alpha = 0.5f)
        else -> TC.Slate800
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TC.Slate900).androidx_border(border)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(if (set.completed) TC.Emerald600 else if (isCurrent) TC.Indigo600 else TC.Slate800), contentAlignment = Alignment.Center) {
                if (set.completed) Icon(Icons.Default.Check, null, tint = TC.White, modifier = Modifier.size(14.dp))
                else Text("${index + 1}", color = if (isCurrent) TC.White else TC.Slate500, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text("CARGA (kg)" + if (previous != null && previous.weight > 0) " · ant. ${formatWeight(previous.weight)}" else "", color = TC.Slate500, fontSize = 10.sp)
                NumberStepper(set.weight, onWeight, step = 2.5, min = 0.0, max = 500.0)
            }
            Column(Modifier.weight(1f)) {
                Text("REPS" + if (previous != null) " · ant. ${previous.reps}" else "", color = TC.Slate500, fontSize = 10.sp)
                NumberStepper(set.reps.toDouble(), { onReps(it.toInt()) }, step = 1.0, min = 1.0, max = 100.0, isInt = true)
            }
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (set.completed) TC.Emerald600 else if (isCurrent) TC.Indigo600 else TC.Slate800).clickable { onComplete() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, "Concluir série", tint = if (set.completed || isCurrent) TC.White else TC.Slate400, modifier = Modifier.size(18.dp))
            }
        }
        if (canRemove) {
            Text("Remover série", color = TC.Slate600, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable { onRemove() }.padding(vertical = 6.dp))
        }
    }
}

private fun Modifier.androidx_border(color: Color): Modifier =
    this.border(1.dp, color, RoundedCornerShape(16.dp))
