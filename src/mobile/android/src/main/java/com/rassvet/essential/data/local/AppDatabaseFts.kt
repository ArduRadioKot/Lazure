package com.rassvet.essential.data.local

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase

object AppDatabaseFts {
    enum class Mode {
        FTS5,
        FTS4,
        NONE,
    }

    private const val TAG = "AppDatabase"

    @Volatile
    var mode: Mode = Mode.NONE
        private set

    private const val CREATE_FTS5_SQL =
        """CREATE VIRTUAL TABLE IF NOT EXISTS note_fts
           USING fts5(uri UNINDEXED, title, body, tokenize='unicode61')"""

    private const val CREATE_FTS4_SQL =
        """CREATE VIRTUAL TABLE IF NOT EXISTS note_fts
           USING fts4(uri, title, body, notindexed=uri, tokenize=unicode61)"""

    @JvmStatic
    fun isAvailable(): Boolean = mode != Mode.NONE

    @JvmStatic
    fun ftsSearchOrderClause(): String =
        when (mode) {
            Mode.FTS5 -> " ORDER BY rank"
            Mode.FTS4, Mode.NONE -> ""
        }

    fun ensureNoteFtsTable(db: SupportSQLiteDatabase) {
        if (mode != Mode.NONE && tableExists(db)) return

        if (tryCreate(db, CREATE_FTS5_SQL, Mode.FTS5)) return
        if (tryCreate(db, CREATE_FTS4_SQL, Mode.FTS4)) return

        mode = Mode.NONE
        Log.w(TAG, "FTS unavailable on this device; note search falls back to recent notes")
    }

    private fun tryCreate(
        db: SupportSQLiteDatabase,
        sql: String,
        target: Mode,
    ): Boolean {
        return try {
            db.execSQL(sql)
            db.execSQL("DELETE FROM note_fts")
            mode = target
            Log.i(TAG, "note_fts ready ($target)")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "FTS init failed for $target", t)
            false
        }
    }

    private fun tableExists(db: SupportSQLiteDatabase): Boolean {
        return try {
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='note_fts'").use { cursor ->
                cursor.moveToFirst()
            }
        } catch (_: Throwable) {
            false
        }
    }
}
