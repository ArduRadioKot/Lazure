package com.rassvet.essential.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_sessions",
    indices = [Index(value = ["last_updated_at"])],
)
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "last_updated_at")
    val lastUpdatedAt: Long,
)

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["session_id"])],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    @ColumnInfo(name = "is_user")
    val isUser: Boolean,
    @ColumnInfo(name = "text")
    val text: String,

    @ColumnInfo(name = "attachments_json")
    val attachmentsJson: String,

    @ColumnInfo(name = "sources_json", defaultValue = "[]")
    val sourcesJson: String = "[]",
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)


