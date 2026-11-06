package com.rassvet.essential.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(tableName = "wiki_edges", primaryKeys = {"fromUri", "toTitle"}, indices = {
    @Index("fromUri"), @Index("toTitle"),
})
public final class WikiEdgeEntity {
    @NonNull
    public final String fromUri;

    @NonNull
    public final String toTitle;

    public WikiEdgeEntity(@NonNull String fromUri, @NonNull String toTitle) {
        this.fromUri = fromUri;
        this.toTitle = toTitle;
    }
}


