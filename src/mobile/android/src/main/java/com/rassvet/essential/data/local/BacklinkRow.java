package com.rassvet.essential.data.local;

import androidx.annotation.NonNull;


public final class BacklinkRow {
    @NonNull
    public final String uri;

    @NonNull
    public final String title;

    public BacklinkRow(@NonNull String uri, @NonNull String title) {
        this.uri = uri;
        this.title = title;
    }
}


