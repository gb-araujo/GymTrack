package app.gabrielaraujo.gymtrack.data.repository

import app.gabrielaraujo.gymtrack.data.GymTrackDatabase

/** Os repositórios do app sobre o mesmo banco (montados num lugar só para o app e os testes). */
class Repositories(
    db: GymTrackDatabase,
    currentUid: () -> String? = { null },
    /** Chamado após cada escrita do usuário que entra na fila de envio (agenda a sincronização). */
    onPendingChange: () -> Unit = {},
    clock: () -> Long = System::currentTimeMillis,
) {
    val records = PersonalRecordRepository(db, clock)
    val sessions = WorkoutSessionRepository(db, records, currentUid, onPendingChange, clock)
    val plans = WorkoutPlanRepository(db, currentUid, onPendingChange, clock)
    val exercises = ExerciseRepository(db, currentUid, onPendingChange, clock)
    val goals = GoalRepository(db, currentUid, onPendingChange, clock)
    val backup = BackupRepository(db, records, currentUid, onPendingChange, clock)
    val userData = UserDataRepository(db, clock)
}
