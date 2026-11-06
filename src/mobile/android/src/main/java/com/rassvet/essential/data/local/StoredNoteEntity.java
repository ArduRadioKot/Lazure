package com.rassvet.essential.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "stored_notes",
        indices = {
            @Index(value = "updated_at_epoch_ms"),
        })
public final class StoredNoteEntity {

    @PrimaryKey
    @ColumnInfo(name = "id")
    @NonNull
    public final String id;

    @NonNull
    @ColumnInfo(name = "title")
    public final String title;

    @NonNull
    @ColumnInfo(name = "preview")
    public final String preview;

    @NonNull
    @ColumnInfo(name = "body")
    public final String body;

    @ColumnInfo(name = "updated_at_epoch_ms")
    public final long updatedAtEpochMs;

    public StoredNoteEntity(
            String id,
            String title,
            String preview,
            String body,
            long updatedAtEpochMs) {
        this.id = id != null ? id : "";
        this.title = title != null ? title : "";
        this.preview = preview != null ? preview : "";
        this.body = body != null ? body : "";
        this.updatedAtEpochMs = updatedAtEpochMs;
    }
}


