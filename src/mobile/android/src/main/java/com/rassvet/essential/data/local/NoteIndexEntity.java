package com.rassvet.essential.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "note_index", indices = {@Index(value = "uri", unique = true)})
public final class NoteIndexEntity {

    @PrimaryKey
    @NonNull
    public final String uri;

    @NonNull
    public final String title;

    @NonNull
    public final String preview;

    public final long updatedAtEpochMs;

    public NoteIndexEntity(
            String uri,
            String title,
            String preview,
            long updatedAtEpochMs) {
        this.uri = uri != null ? uri : "";
        this.title = title != null ? title : "";
        this.preview = preview != null ? preview : "";
        this.updatedAtEpochMs = updatedAtEpochMs;
    }
}


