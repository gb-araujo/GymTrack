package app.gabrielaraujo.gymtrack.data

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RebrandDatabaseTest {
    @Test
    fun `banco GymTrack v2 migra para RepStack preservando dados`() = checkUpgrade(2)

    @Test
    fun `banco GymTrack v3 abre no RepStack preservando dados`() = checkUpgrade(3)

    private fun checkUpgrade(version: Int) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(RepStackDatabase.NAME)
        val path = context.getDatabasePath(RepStackDatabase.NAME)
        path.parentFile!!.mkdirs()
        val schema = JSONObject(
            File("schemas/app.gabrielaraujo.gymtrack.data.GymTrackDatabase/$version.json").readText(),
        ).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO sync_meta (`key`, `value`) VALUES ('rebrand-test', 123456)")
            old.version = version
        }
        val db = RepStackDatabase.build(context)
        try {
                db.openHelper.writableDatabase.query("SELECT value FROM sync_meta WHERE `key` = 'rebrand-test'").use { cursor ->
                    check(cursor.moveToFirst())
                    assertEquals(123456L, cursor.getLong(0))
                }
                assertEquals(3, db.openHelper.writableDatabase.version)
        } finally {
            db.close()
            context.deleteDatabase(RepStackDatabase.NAME)
        }
    }
}
