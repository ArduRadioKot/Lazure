package com.rassvet.essential.data.local

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @Test
    fun migration7To8AddsMeetingEntitiesAndPreservesImportedRecordings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val callback = object : SupportSQLiteOpenHelper.Callback(7) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE recordings (
                    id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, audio_path TEXT NOT NULL,
                    duration_ms INTEGER NOT NULL, created_at INTEGER NOT NULL,
                    processing_status TEXT NOT NULL, processing_progress INTEGER NOT NULL DEFAULT 0,
                    language TEXT)""")
                db.execSQL("INSERT INTO recordings VALUES ('rec-1','Planning','/private/audio',42000,100,'awaiting_asr',0,'ru')")
            }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        context.deleteDatabase("migration-7-test.db")
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("migration-7-test.db").callback(callback).build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        AppDatabaseMigrations.MIGRATION_7_8.migrate(db)
        db.query("SELECT title, duration_ms, updated_at FROM recordings WHERE id='rec-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Planning", cursor.getString(0))
            assertEquals(42_000L, cursor.getLong(1))
            assertEquals(100L, cursor.getLong(2))
        }
        for (table in listOf("speakers", "recording_summaries", "recording_chat_sessions")) {
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { cursor ->
                assertTrue("missing table $table", cursor.moveToFirst())
            }
        }
        helper.close()
    }

    @Test
    fun migration6To7CreatesRecordingTablesAndPreservesExistingTables() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val callback = object : SupportSQLiteOpenHelper.Callback(6) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY)")
                db.execSQL("CREATE TABLE chat_messages (id INTEGER PRIMARY KEY)")
            }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("migration-test.db").callback(callback).build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        AppDatabaseMigrations.MIGRATION_6_7.migrate(db)
        for (table in listOf("notes", "chat_messages", "recordings", "transcript_segments")) {
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { cursor ->
                assertTrue("missing table $table", cursor.moveToFirst())
            }
        }
        helper.close()
    }

    @Test
    fun freshDatabase_hasFtsTable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.build(context)
        db.openHelper.writableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='note_fts'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        db.close()
    }
}
