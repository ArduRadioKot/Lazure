package com.rassvet.essential.data.api;


public final class EssentialHttpException extends RuntimeException {
    private final int statusCode;
    private final String responseBody;

    public EssentialHttpException(int statusCode, String responseBody) {
        super("HTTP " + statusCode + (responseBody != null && !responseBody.isEmpty() ? ": " + responseBody : ""));
        this.statusCode = statusCode;
        this.responseBody = responseBody != null ? responseBody : "";
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}


