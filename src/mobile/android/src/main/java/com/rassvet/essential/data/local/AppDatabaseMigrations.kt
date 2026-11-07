package com.rassvet.essential.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AppDatabaseMigrations {
    val MIGRATION_3_4 =
        object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS chat_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        last_updated_at INTEGER NOT NULL
                    )""",
                )
                db.execSQL(
                    """CREATE INDEX IF NOT EXISTS index_chat_sessions_last_updated_at
                       ON chat_sessions(last_updated_at)""",
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS chat_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        session_id INTEGER NOT NULL,
                        is_user INTEGER NOT NULL,
                        text TEXT NOT NULL,
                        attachments_json TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )""",
                )
                db.execSQL(
                    """CREATE INDEX IF NOT EXISTS index_chat_messages_session_id
                       ON chat_messages(session_id)""",
                )
            }
        }

    val MIGRATION_4_5 =
        object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AppDatabaseFts.ensureNoteFtsTable(db)
            }
        }

    val MIGRATION_5_6 =
        object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """ALTER TABLE chat_messages ADD COLUMN sources_json TEXT NOT NULL DEFAULT '[]'""",
                )
            }
        }

    val MIGRATION_6_7 =
        object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS recordings (
                    id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, audio_path TEXT NOT NULL,
                    duration_ms INTEGER NOT NULL, created_at INTEGER NOT NULL,
                    processing_status TEXT NOT NULL, processing_progress INTEGER NOT NULL DEFAULT 0,
                    language TEXT)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recordings_created_at ON recordings(created_at)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS transcript_segments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, recording_id TEXT NOT NULL,
                    speaker_id TEXT NOT NULL, start_ms INTEGER NOT NULL, end_ms INTEGER NOT NULL,
                    text TEXT NOT NULL, original_text TEXT)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transcript_segments_recording_id_start_ms ON transcript_segments(recording_id, start_ms)")
            }
        }

    val MIGRATION_7_8 =
        object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recordings ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE recordings ADD COLUMN selected_asr_model_id TEXT")
                db.execSQL("ALTER TABLE recordings ADD COLUMN selected_diarization_model_id TEXT")
                db.execSQL("UPDATE recordings SET updated_at = created_at WHERE updated_at = 0")
                db.execSQL("""CREATE TABLE IF NOT EXISTS speakers (
                    recording_id TEXT NOT NULL, raw_speaker_id TEXT NOT NULL,
                    display_name TEXT NOT NULL, color_index INTEGER NOT NULL,
                    PRIMARY KEY(recording_id, raw_speaker_id))""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_speakers_recording_id ON speakers(recording_id)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS recording_summaries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, recording_id TEXT NOT NULL,
                    content TEXT NOT NULL, created_at INTEGER NOT NULL, model_id TEXT NOT NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recording_summaries_recording_id_created_at ON recording_summaries(recording_id, created_at)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS recording_chat_sessions (
                    id TEXT NOT NULL PRIMARY KEY, recording_id TEXT NOT NULL,
                    title TEXT NOT NULL, created_at INTEGER NOT NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recording_chat_sessions_recording_id_created_at ON recording_chat_sessions(recording_id, created_at)")
            }
        }

    val MIGRATION_8_9 =
        object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS recording_chat_messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, recording_id TEXT NOT NULL,
                    role TEXT NOT NULL, text TEXT NOT NULL, source_starts TEXT NOT NULL DEFAULT '',
                    created_at INTEGER NOT NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recording_chat_messages_recording_id_created_at ON recording_chat_messages(recording_id, created_at)")
            }
        }

    val ALL = arrayOf(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
}
