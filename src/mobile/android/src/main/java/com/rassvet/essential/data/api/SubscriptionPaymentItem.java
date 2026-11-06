package com.rassvet.essential.data.api;

public final class SubscriptionPaymentItem {
    private final String id;
    private final long amountRub;
    private final int periodDays;
    private final String status;
    private final long createdAtEpochMs;
    private final Long confirmedAtEpochMs;

    public SubscriptionPaymentItem(
            String id,
            long amountRub,
            int periodDays,
            String status,
            long createdAtEpochMs,
            Long confirmedAtEpochMs) {
        this.id = id;
        this.amountRub = amountRub;
        this.periodDays = periodDays;
        this.status = status;
        this.createdAtEpochMs = createdAtEpochMs;
        this.confirmedAtEpochMs = confirmedAtEpochMs;
    }

    public String getId() {
        return id;
    }

    public long getAmountRub() {
        return amountRub;
    }

    public int getPeriodDays() {
        return periodDays;
    }

    public String getStatus() {
        return status;
    }

    public long getCreatedAtEpochMs() {
        return createdAtEpochMs;
    }

    public Long getConfirmedAtEpochMs() {
        return confirmedAtEpochMs;
    }
}


