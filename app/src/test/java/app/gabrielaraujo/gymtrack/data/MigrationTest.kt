package app.gabrielaraujo.gymtrack.data

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File

/**
 * Migrações do Room: cria o banco exatamente como cada versão antiga o deixava (a partir do schema
 * exportado em `app/schemas/`), grava dados e abre com o Room atual, que migra e valida o schema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val schemaDir = File("schemas/${GymTrackDatabase::class.java.name}")

    private fun schema(version: Int): JsonObject =
        Json.parseToJsonElement(File(schemaDir, "$version.json").readText()).jsonObject["database"]!!.jsonObject

    private fun oldVersions(): List<Int> =
        schemaDir.listFiles().orEmpty().mapNotNull { it.nameWithoutExtension.toIntOrNull() }.filter { it < GymTrackDatabase.VERSION }.sorted()

    /** Cria o arquivo do banco na [version] informada, com os dados de exemplo de [seed]. */
    private fun createAt(version: Int, name: String, seed: (SQLiteDatabase) -> Unit) {
        val db = schema(version)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sql ->
            for (entity in db["entities"]!!.jsonArray.map { it.jsonObject }) {
                val table = entity["tableName"]!!.jsonPrimitive.content
                sql.execSQL(entity["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity["indices"]?.jsonArray?.forEach { index ->
                    sql.execSQL(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            db["setupQueries"]!!.jsonArray.forEach { sql.execSQL(it.jsonPrimitive.content) }
            seed(sql)
            sql.version = version
        }
    }

    private fun openCurrent(name: String): GymTrackDatabase =
        Room.databaseBuilder(context, GymTrackDatabase::class.java, name).addMigrations(*GymTrackDatabase.MIGRATIONS).build()

    @Test
    fun `schema da versao atual esta exportado`() {
        assertTrue(File(schemaDir, "${GymTrackDatabase.VERSION}.json").exists())
        assertTrue("Sem versões antigas para testar", oldVersions().isNotEmpty())
    }

    @Test
    fun `todas as versoes antigas migram para a atual preservando os dados`() {
        for (version in oldVersions()) {
            val name = "migration-$version.db"
            createAt(version, name) { sql ->
                // Colunas que existem desde a versão 2 (a mais antiga com schema exportado).
                sql.execSQL(
                    "INSERT INTO workout_plans (id, name, days, currentDayIndex, createdAt, updatedAt, isActive, ownerId, dirty, deleted) " +
                        "VALUES ('p1', 'Ficha', ?, 1, 10, 20, 1, 'u1', 1, 0)",
                    arrayOf("""[{"id":"d1","label":"A","name":"Peito","exercises":[{"id":"we1","exerciseId":"supino_reto","sets":3,"repMin":8,"repMax":12,"restSeconds":90,"order":0}],"order":0}]"""),
                )
                sql.execSQL(
                    "INSERT INTO workout_sessions (id, workoutPlanId, workoutDayId, workoutDayLabel, workoutDayName, exercises, startTime, endTime, " +
                        "duration, completed, totalVolume, totalSets, prsAchieved, ownerId, updatedAt, dirty, deleted) " +
                        "VALUES ('s1', 'p1', 'd1', 'A', 'Peito', ?, 1000, 4600000, 60, 1, 600.0, 1, '[\"supino_reto\"]', 'u1', 4600000, 0, 0)",
                    arrayOf("""[{"id":"se1","exerciseId":"supino_reto","workoutExerciseId":"we1","name":"Supino","sets":[{"id":"r1","weight":60.0,"reps":10,"completed":true}],"order":0}]"""),
                )
                sql.execSQL(
                    "INSERT INTO personal_records (exerciseId, exerciseName, weight, reps, volume, date, sessionId, ownerId, updatedAt, dirty, deleted) " +
                        "VALUES ('supino_reto', 'Supino', 60.0, 10, 600.0, 4600000, 's1', 'u1', 4600000, 0, 0)",
                )
                sql.execSQL(
                    "INSERT INTO goals (id, type, title, target, current, unit, exerciseId, createdAt, completedAt, ownerId, updatedAt, dirty, deleted) " +
                        "VALUES ('g1', 'workout_count', 'Meta', 10.0, 0.0, 'treinos', NULL, 5, NULL, 'u1', 5, 1, 0)",
                )
                sql.execSQL(
                    "INSERT INTO exercises (id, name, primaryMuscle, secondaryMuscles, equipment, movementPattern, category, difficulty, instructions, " +
                        "isCustom, createdAt, ownerId, updatedAt, dirty, deleted) " +
                        "VALUES ('x1', 'Meu exercício', 'peito', '[]', '', '', '', 'beginner', NULL, 1, 5, 'u1', 5, 0, 0)",
                )
                sql.execSQL("INSERT INTO sync_meta (`key`, value) VALUES ('u1:plans:serverCursor', 123)")
            }

            val db = openCurrent(name)
            try {
                runBlocking {
                    val dao = db.dao()
                    val plan = dao.getPlan("p1")!!
                    assertEquals("supino_reto", plan.days.single().exercises.single().exerciseId)
                    assertEquals(1, plan.currentDayIndex)
                    assertTrue(plan.dirty)

                    val session = dao.getSession("s1")!!
                    assertTrue(session.completed)
                    assertEquals(60.0, session.exercises.single().sets.single().weight, 0.0)
                    assertEquals(listOf("supino_reto"), session.prsAchieved)
                    // Colunas novas (v3) chegam vazias.
                    assertNull(session.avgHeartRate)
                    assertNull(session.calories)

                    assertEquals(60.0, dao.getPersonalRecord("supino_reto")!!.weight, 0.0)
                    assertEquals("Meta", dao.getGoal("g1")!!.title)
                    assertTrue(dao.getExercise("x1")!!.isCustom)
                    assertFalse(dao.getExercise("x1")!!.deleted)
                    assertEquals(123L, dao.getSyncCursor("u1:plans:serverCursor"))
                }
            } finally {
                db.close()
                context.deleteDatabase(name)
            }
        }
    }
}
