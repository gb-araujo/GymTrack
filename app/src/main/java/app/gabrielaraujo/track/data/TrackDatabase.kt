package app.gabrielaraujo.track.data

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
abstract class TrackDatabase : RoomDatabase() {
    abstract fun dao(): TrackDao

    companion object {
        fun build(context: Context): TrackDatabase =
            Room.databaseBuilder(context, TrackDatabase::class.java, "track.db")
                // App ainda não publicado: recriar em mudança de schema é aceitável.
                .fallbackToDestructiveMigration()
                .build()
    }
}
