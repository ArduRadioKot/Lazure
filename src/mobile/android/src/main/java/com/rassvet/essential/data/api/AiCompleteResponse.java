package com.rassvet.essential.data.api;

public final class AiCompleteResponse {
    private final String text;
    private final int tokensIn;
    private final int tokensOut;

    public AiCompleteResponse(String text, int tokensIn, int tokensOut) {
        this.text = text;
        this.tokensIn = tokensIn;
        this.tokensOut = tokensOut;
    }

    public String getText() {
        return text;
    }

    public int getTokensIn() {
        return tokensIn;
    }

    public int getTokensOut() {
        return tokensOut;
    }
}


