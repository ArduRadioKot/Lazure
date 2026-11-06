package com.rassvet.essential.data.local;

import androidx.annotation.NonNull;

public final class NoteFtsResult {
    @NonNull public final String uri;
    @NonNull public final String title;
    @NonNull public final String body;

    public NoteFtsResult(@NonNull String uri, @NonNull String title, @NonNull String body) {
        this.uri = uri;
        this.title = title;
        this.body = body;
    }
}


