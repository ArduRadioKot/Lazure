package com.rassvet.essential.data.api;

public final class SubscriptionCheckoutResponse {
    private final String paymentId;
    private final String transactionId;
    private final String paymentUrl;
    private final int amountRub;
    private final int periodDays;

    public SubscriptionCheckoutResponse(
            String paymentId,
            String transactionId,
            String paymentUrl,
            int amountRub,
            int periodDays) {
        this.paymentId = paymentId;
        this.transactionId = transactionId;
        this.paymentUrl = paymentUrl;
        this.amountRub = amountRub;
        this.periodDays = periodDays;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getPaymentUrl() {
        return paymentUrl;
    }

    public int getAmountRub() {
        return amountRub;
    }

    public int getPeriodDays() {
        return periodDays;
    }
}


