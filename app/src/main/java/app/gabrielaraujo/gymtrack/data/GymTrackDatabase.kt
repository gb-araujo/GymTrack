package app.gabrielaraujo.gymtrack.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration

/**
 * Banco local. O schema de cada versão fica versionado em `app/schemas/` (exportSchema).
 *
 * Ao mudar qualquer @Entity: suba [VERSION], rode o build para gerar o novo JSON em
 * `app/schemas/`, commite-o e adicione a migração correspondente em [MIGRATIONS]
 * (ou um `@AutoMigration` quando a mudança for só aditiva). Não existe fallback
 * destrutivo: sem migração o app falha em vez de apagar os treinos do usuário.
 */
@Database(
    entities = [
        Exercise::class,
        WorkoutPlan::class,
        WorkoutSession::class,
        PersonalRecord::class,
        Goal::class,
        SyncMeta::class,
    ],
    version = GymTrackDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [
        // 3: frequência cardíaca e calorias do treino (colunas novas, anuláveis)
        AutoMigration(from = 2, to = 3),
    ],
)
@TypeConverters(Converters::class)
abstract class GymTrackDatabase : RoomDatabase() {
    abstract fun dao(): GymTrackDao

    companion object {
        const val VERSION = 3
        const val NAME = "track.db"

        /** Migrações manuais entre versões (ex.: `Migration(2, 3) { db -> db.execSQL(...) }`). */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun build(context: Context): GymTrackDatabase =
            Room.databaseBuilder(context, GymTrackDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
