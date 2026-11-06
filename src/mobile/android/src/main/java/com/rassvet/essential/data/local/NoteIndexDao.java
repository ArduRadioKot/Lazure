package com.rassvet.essential.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface NoteIndexDao {
    @Query("SELECT * FROM note_index ORDER BY title COLLATE NOCASE ASC")
    List<NoteIndexEntity> all();

    @Query("SELECT * FROM note_index ORDER BY updatedAtEpochMs DESC LIMIT :limit")
    List<NoteIndexEntity> topRecent(int limit);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(NoteIndexEntity note);

    @Query("DELETE FROM note_index")
    void clear();
}


