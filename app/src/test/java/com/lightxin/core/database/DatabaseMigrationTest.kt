package com.lightxin.core.database

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.google.gson.JsonParser
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DatabaseMigrationTest {
    @Test fun versionOneScheduleSurvivesSnapshotMigration() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val schemaFile = File("schemas/com.lightxin.core.database.LightXinDatabase/1.json")
        val schema = JsonParser.parseString(schemaFile.readText()).asJsonObject
            .getAsJsonObject("database")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            schema.getAsJsonArray("entities").forEach { element ->
                val entity = element.asJsonObject
                old.execSQL(entity["createSql"].asString.replace("\${TABLE_NAME}", entity["tableName"].asString))
            }
            schema.getAsJsonArray("setupQueries").forEach { old.execSQL(it.asString) }
            old.execSQL("INSERT INTO semester_cache VALUES ('A','2026','1','[8]',8,'2026-10-25',123,1)")
            old.execSQL("INSERT INTO schedule_week_cache VALUES ('A','2026','1',8,'[]',123,1)")
            old.version = 1
        }
        val migrated = Room.databaseBuilder(context, LightXinDatabase::class.java, name).build()
        try {
            assertEquals("2026", migrated.scheduleCacheDao().semester("A")?.schoolYear)
            assertEquals("[]", migrated.scheduleCacheDao().week("A", "2026", "1", 8)?.payloadJson)
            val snapshot = RemoteSnapshotEntity("A", "test", "{}", 456)
            migrated.remoteSnapshotDao().put(snapshot)
            assertEquals(snapshot, migrated.remoteSnapshotDao().get("A", "test"))
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
