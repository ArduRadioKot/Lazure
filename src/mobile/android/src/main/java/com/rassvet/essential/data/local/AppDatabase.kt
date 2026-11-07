package com.rassvet.essential.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rassvet.essential.BuildConfig

@Database(
    entities = [
        NoteIndexEntity::class,
        WikiEdgeEntity::class,
        StoredNoteEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        RecordingEntity::class,
        TranscriptSegmentEntity::class,
        SpeakerEntity::class,
        SummaryEntity::class,
        RecordingChatSessionEntity::class,
        RecordingChatMessageEntity::class,
    ],
    version = 10,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteIndexDao(): NoteIndexDao

    abstract fun wikiEdgeDao(): WikiEdgeDao

    abstract fun storedNoteDao(): StoredNoteDao

    abstract fun chatSessionDao(): ChatSessionDao

    abstract fun chatMessageDao(): ChatMessageDao

    abstract fun recordingDao(): RecordingDao

    abstract fun speakerDao(): SpeakerDao

    abstract fun summaryDao(): SummaryDao

    abstract fun recordingChatSessionDao(): RecordingChatSessionDao

    abstract fun recordingChatMessageDao(): RecordingChatMessageDao

    companion object {
        fun build(context: Context): AppDatabase {
            val builder =
                Room.databaseBuilder(context, AppDatabase::class.java, "essential.db")
                    .addMigrations(*AppDatabaseMigrations.ALL)
                    .addCallback(
                        object : Callback() {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                AppDatabaseFts.ensureNoteFtsTable(db)
                            }

                            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                                AppDatabaseFts.ensureNoteFtsTable(db)
                            }
                        },
                    )
            return builder.build()
        }
    }
}
