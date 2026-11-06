package com.rassvet.essential.data.api;

public final class AuthSessionItem {
    public final String id;
    public final String deviceLabel;
    public final String devicePlatform;
    public final String appVersion;
    public final String ipAddress;
    public final long createdAtEpochMs;
    public final long lastSeenAtEpochMs;
    public final boolean current;

    public AuthSessionItem(
            String id,
            String deviceLabel,
            String devicePlatform,
            String appVersion,
            String ipAddress,
            long createdAtEpochMs,
            long lastSeenAtEpochMs,
            boolean current) {
        this.id = id;
        this.deviceLabel = deviceLabel;
        this.devicePlatform = devicePlatform;
        this.appVersion = appVersion;
        this.ipAddress = ipAddress;
        this.createdAtEpochMs = createdAtEpochMs;
        this.lastSeenAtEpochMs = lastSeenAtEpochMs;
        this.current = current;
    }
}


