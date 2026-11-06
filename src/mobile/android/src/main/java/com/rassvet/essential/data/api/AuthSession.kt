package com.rassvet.essential.data.api

import com.rassvet.essential.data.local.VaultPreferencesRepository
import java.io.IOException


const val DEV_STUB_AUTH_TOKEN = "dev_local_stub_no_jwt"

suspend fun VaultPreferencesRepository.fetchAndCacheMeAccount(
    apiBase: String,
    token: String,
): MeAccountResponse {
    if (token == DEV_STUB_AUTH_TOKEN) {
        throw IOException("dev_stub_token")
    }
    val api = EssentialApi(ApiBaseUrls.normalize(apiBase))
    try {
        val info = api.meAccount(token)
        setAccountQuotaCache(
            subscriptionStatus = info.subscriptionStatus,
            tokensUsed = info.tokensUsed,
            tokensQuota = info.tokensQuota,
            subscriptionExpiresAtEpochMs = info.subscriptionExpiresAtEpochMs,
            subscriptionPriceRub = info.subscriptionPriceRub,
            subscriptionPeriodDays = info.subscriptionPeriodDays,
        )
        return info
    } finally {
        api.close()
    }
}


