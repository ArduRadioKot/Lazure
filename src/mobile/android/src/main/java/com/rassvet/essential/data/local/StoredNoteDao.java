package com.rassvet.essential.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface StoredNoteDao {
    @Query("SELECT * FROM stored_notes ORDER BY title COLLATE NOCASE ASC")
    List<StoredNoteEntity> allByTitleAsc();

    @Query("SELECT * FROM stored_notes ORDER BY updated_at_epoch_ms DESC")
    List<StoredNoteEntity> allByUpdatedDesc();

    @Query("SELECT * FROM stored_notes WHERE id = :id LIMIT 1")
    StoredNoteEntity getById(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(StoredNoteEntity note);

    @Query("DELETE FROM stored_notes WHERE id = :id")
    void deleteById(String id);
}


