package com.rassvet.essential.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface WikiEdgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<WikiEdgeEntity> edges);

    @Query("DELETE FROM wiki_edges")
    void clear();

    @Query("DELETE FROM wiki_edges WHERE fromUri = :noteId")
    void deleteFromNote(String noteId);

    @Query(
            "SELECT toTitle FROM wiki_edges WHERE fromUri = :fromUri ORDER BY toTitle COLLATE NOCASE ASC")
    List<String> linksFrom(String fromUri);

    @Query(
            "SELECT DISTINCT n.uri, n.title FROM wiki_edges e "
                    + "INNER JOIN note_index n ON e.fromUri = n.uri "
                    + "WHERE LOWER(e.toTitle) = LOWER(:targetTitle) "
                    + "ORDER BY n.title COLLATE NOCASE ASC")
    List<BacklinkRow> backlinksTo(String targetTitle);
}


