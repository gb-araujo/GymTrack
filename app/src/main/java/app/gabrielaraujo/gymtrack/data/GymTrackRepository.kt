package app.gabrielaraujo.gymtrack.data

/** Camada fina sobre o [GymTrackDao]; semeia dados iniciais e expõe operações suspensas. */
class GymTrackRepository(db: GymTrackDatabase) {
    val dao = db.dao()

    /** Semeia exercícios e ficha exemplo na primeira execução. */
    suspend fun seedIfEmpty() {
        if (dao.exerciseCount() == 0) dao.insertExercises(builtinExercises)
        if (dao.planCount() == 0) dao.upsertPlan(buildSamplePlan(System.currentTimeMillis()))
    }

    suspend fun getExercises() = dao.getExercises()
    suspend fun upsertExercise(exercise: Exercise) = dao.upsertExercise(exercise)
    suspend fun deleteExercise(id: String) = dao.deleteExercise(id)

    suspend fun getPlans() = dao.getPlans()
    suspend fun getPlan(id: String) = dao.getPlan(id)
    suspend fun upsertPlan(plan: WorkoutPlan) = dao.upsertPlan(plan)
    suspend fun upsertPlans(plans: List<WorkoutPlan>) = dao.upsertPlans(plans)
    suspend fun deletePlan(id: String) = dao.deletePlan(id)

    suspend fun getSessions() = dao.getSessions()
    suspend fun upsertSession(session: WorkoutSession) = dao.upsertSession(session)
    suspend fun deleteSession(id: String) = dao.deleteSession(id)

    suspend fun getPersonalRecords() = dao.getPersonalRecords()
    suspend fun upsertPersonalRecord(record: PersonalRecord) = dao.upsertPersonalRecord(record)

    suspend fun getGoals() = dao.getGoals()
    suspend fun upsertGoal(goal: Goal) = dao.upsertGoal(goal)
    suspend fun deleteGoal(id: String) = dao.deleteGoal(id)
}
