package com.rassvet.essential.data.api;

public final class MeAccountResponse {
    private final String subscriptionStatus;
    private final long tokensUsed;
    private final long tokensQuota;
    private final long freeTokensQuota;
    private final long proTokensQuota;
    private final long createdAtEpochMs;
    private final Long subscriptionExpiresAtEpochMs;
    private final int subscriptionPriceRub;
    private final int subscriptionPeriodDays;

    public MeAccountResponse(
            String subscriptionStatus,
            long tokensUsed,
            long tokensQuota,
            long freeTokensQuota,
            long proTokensQuota,
            long createdAtEpochMs,
            Long subscriptionExpiresAtEpochMs,
            int subscriptionPriceRub,
            int subscriptionPeriodDays) {
        this.subscriptionStatus = subscriptionStatus;
        this.tokensUsed = tokensUsed;
        this.tokensQuota = tokensQuota;
        this.freeTokensQuota = freeTokensQuota;
        this.proTokensQuota = proTokensQuota;
        this.createdAtEpochMs = createdAtEpochMs;
        this.subscriptionExpiresAtEpochMs = subscriptionExpiresAtEpochMs;
        this.subscriptionPriceRub = subscriptionPriceRub;
        this.subscriptionPeriodDays = subscriptionPeriodDays;
    }

    public String getSubscriptionStatus() {
        return subscriptionStatus;
    }

    public long getTokensUsed() {
        return tokensUsed;
    }

    public long getTokensQuota() {
        return tokensQuota;
    }

    public long getFreeTokensQuota() {
        return freeTokensQuota;
    }

    public long getProTokensQuota() {
        return proTokensQuota;
    }

    public long getCreatedAtEpochMs() {
        return createdAtEpochMs;
    }

    public Long getSubscriptionExpiresAtEpochMs() {
        return subscriptionExpiresAtEpochMs;
    }

    public int getSubscriptionPriceRub() {
        return subscriptionPriceRub;
    }

    public int getSubscriptionPeriodDays() {
        return subscriptionPeriodDays;
    }

    public boolean hasSubscription() {
        if (!"subscription".equals(subscriptionStatus)) return false;
        Long expires = subscriptionExpiresAtEpochMs;
        if (expires == null || expires <= 0L) return true;
        return expires > System.currentTimeMillis();
    }


    public boolean isLifetimeSubscription() {
        return "subscription".equals(subscriptionStatus)
                && (subscriptionExpiresAtEpochMs == null || subscriptionExpiresAtEpochMs <= 0L);
    }
}


