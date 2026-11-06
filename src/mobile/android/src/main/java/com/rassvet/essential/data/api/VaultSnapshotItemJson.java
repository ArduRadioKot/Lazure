package com.rassvet.essential.data.api;

public final class VaultSnapshotItemJson {
    private final long id;
    private final long version;
    private final long sizeBytes;
    private final long createdAtEpochMs;

    public VaultSnapshotItemJson(long id, long version, long sizeBytes, long createdAtEpochMs) {
        this.id = id;
        this.version = version;
        this.sizeBytes = sizeBytes;
        this.createdAtEpochMs = createdAtEpochMs;
    }

    public long getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public long getCreatedAtEpochMs() {
        return createdAtEpochMs;
    }
}


