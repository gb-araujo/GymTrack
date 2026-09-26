package app.gabrielaraujo.gymtrack.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Exercise::class,
        WorkoutPlan::class,
        WorkoutSession::class,
        PersonalRecord::class,
        Goal::class,
        SyncMeta::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class GymTrackDatabase : RoomDatabase() {
    abstract fun dao(): GymTrackDao

    companion object {
        fun build(context: Context): GymTrackDatabase =
            Room.databaseBuilder(context, GymTrackDatabase::class.java, "track.db")
                // App ainda não publicado: recriar em mudança de schema é aceitável.
                .fallbackToDestructiveMigration()
                .build()
    }
}
