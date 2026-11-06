package com.rassvet.essential.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions ORDER BY last_updated_at DESC")
    fun all(): List<ChatSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(session: ChatSessionEntity): Long

    @Update
    fun update(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET title = :title, last_updated_at = :ts WHERE id = :id")
    fun touch(id: Long, title: String, ts: Long)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    fun deleteById(id: Long)

    @Query("DELETE FROM chat_messages WHERE session_id = :sessionId")
    fun deleteMessagesFor(sessionId: Long)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY created_at ASC, id ASC")
    fun forSession(sessionId: Long): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(message: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET text = :text WHERE id = :id")
    fun updateText(id: Long, text: String)

    @Query(
        "SELECT id FROM chat_messages WHERE session_id = :sessionId AND is_user = 0 " +
            "ORDER BY created_at DESC, id DESC LIMIT 1",
    )
    fun lastAssistantMessageId(sessionId: Long): Long?
}


