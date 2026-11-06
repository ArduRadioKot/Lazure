package com.rassvet.essential.data.vault;

import android.net.Uri;

public final class VaultRenameResult {
    public final boolean ok;
    public final Uri uri;
    public final String error;

    public VaultRenameResult(boolean ok, Uri uri, String error) {
        this.ok = ok;
        this.uri = uri;
        this.error = error;
    }
}


