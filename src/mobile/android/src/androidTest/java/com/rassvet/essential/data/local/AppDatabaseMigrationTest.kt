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

    @Test
    fun migration8To9CreatesRecordingChatMessagesAndKeepsRecordings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val callback = object : SupportSQLiteOpenHelper.Callback(8) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE recordings (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)")
                db.execSQL("INSERT INTO recordings VALUES ('r1','Keep me')")
            }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        context.deleteDatabase("migration-8-test.db")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name("migration-8-test.db").callback(callback).build())
        val db = helper.writableDatabase
        AppDatabaseMigrations.MIGRATION_8_9.migrate(db)
        db.execSQL("INSERT INTO recording_chat_messages (recording_id, role, text, created_at) VALUES ('r1','user','hi',1)")
        db.query("SELECT title FROM recordings").use { assertTrue(it.moveToFirst()); assertEquals("Keep me", it.getString(0)) }
        db.query("SELECT text, source_starts FROM recording_chat_messages").use { assertTrue(it.moveToFirst()); assertEquals("hi", it.getString(0)); assertEquals("", it.getString(1)) }
        helper.close()
    }

    @Test
    fun migration9To10AddsOverlapColumnWithFalseDefault() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val callback = object : SupportSQLiteOpenHelper.Callback(9) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE transcript_segments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, recording_id TEXT NOT NULL, speaker_id TEXT NOT NULL, start_ms INTEGER NOT NULL, end_ms INTEGER NOT NULL, text TEXT NOT NULL, original_text TEXT)")
                db.execSQL("INSERT INTO transcript_segments (recording_id, speaker_id, start_ms, end_ms, text) VALUES ('r','speaker_1',0,1000,'hi')")
            }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        context.deleteDatabase("migration-9-test.db")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name("migration-9-test.db").callback(callback).build())
        val db = helper.writableDatabase
        AppDatabaseMigrations.MIGRATION_9_10.migrate(db)
        db.query("SELECT text, overlap FROM transcript_segments").use { assertTrue(it.moveToFirst()); assertEquals("hi", it.getString(0)); assertEquals(0, it.getInt(1)) }
        helper.close()
    }

    @Test
    fun migration10To11AddsProcessingTimeWithZeroDefault() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val callback = object : SupportSQLiteOpenHelper.Callback(10) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE recordings (id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, audio_path TEXT NOT NULL, duration_ms INTEGER NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL DEFAULT 0, processing_status TEXT NOT NULL, processing_progress INTEGER NOT NULL DEFAULT 0, language TEXT, selected_asr_model_id TEXT, selected_diarization_model_id TEXT)")
                db.execSQL("INSERT INTO recordings (id, title, audio_path, duration_ms, created_at, processing_status) VALUES ('r','Cats','/a.wav',5000,1,'completed')")
            }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        context.deleteDatabase("migration-10-test.db")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name("migration-10-test.db").callback(callback).build())
        val db = helper.writableDatabase
        AppDatabaseMigrations.MIGRATION_10_11.migrate(db)
        db.query("SELECT title, processing_ms FROM recordings").use { assertTrue(it.moveToFirst()); assertEquals("Cats", it.getString(0)); assertEquals(0L, it.getLong(1)) }
        helper.close()
    }
}
