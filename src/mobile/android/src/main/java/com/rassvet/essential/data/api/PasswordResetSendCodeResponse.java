package com.rassvet.essential.data.api;

public final class PasswordResetSendCodeResponse {
    private final String email;

    public PasswordResetSendCodeResponse(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}


