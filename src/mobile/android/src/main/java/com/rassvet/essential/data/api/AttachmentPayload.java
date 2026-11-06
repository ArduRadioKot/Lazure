package com.rassvet.essential.data.api;


public final class AttachmentPayload {
    public final String mimeType;
    public final String base64;
    public final String displayName;

    public AttachmentPayload(String mimeType, String base64, String displayName) {
        this.mimeType = mimeType != null ? mimeType : "application/octet-stream";
        this.base64 = base64 != null ? base64 : "";
        this.displayName = displayName != null ? displayName : "файл";
    }
}


