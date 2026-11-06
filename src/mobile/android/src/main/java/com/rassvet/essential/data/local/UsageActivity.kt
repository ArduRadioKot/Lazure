package com.rassvet.essential.data.local

import android.content.Context
import com.rassvet.essential.data.api.ApiBaseUrls
import com.rassvet.essential.data.api.DEV_STUB_AUTH_TOKEN
import com.rassvet.essential.data.api.EssentialApi
import com.rassvet.essential.data.network.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate

object UsageActivity {

    fun observeHeatmap(context: Context): Flow<List<Int>> =
        VaultPreferencesRepository(context).usageHeatmap

    suspend fun recordPrompt(context: Context) {
        val prefs = VaultPreferencesRepository(context)
        prefs.bumpUsage(prompts = 1, notes = 0)
        pushIncrement(context, prefs, prompts = 1, notes = 0)
    }

    suspend fun recordNoteCreated(context: Context) {
        val prefs = VaultPreferencesRepository(context)
        prefs.bumpUsage(prompts = 0, notes = 1)
        pushIncrement(context, prefs, prompts = 0, notes = 1)
    }

    suspend fun syncFromServer(context: Context, apiBase: String, token: String) {
        if (token.isBlank() || token == DEV_STUB_AUTH_TOKEN) return
        if (!NetworkMonitor(context.applicationContext).isOnline()) return
        val prefs = VaultPreferencesRepository(context)
        val api = EssentialApi(ApiBaseUrls.normalize(apiBase))
        try {
            val days = api.fetchActivityHeatmap(token)
            prefs.mergeUsageFromServer(
                days.map { ServerUsageDay(date = it.date, prompts = it.prompts, notes = it.notes) },
            )
        } finally {
            api.close()
        }
    }

    private suspend fun pushIncrement(
        context: Context,
        prefs: VaultPreferencesRepository,
        prompts: Int,
        notes: Int,
    ) {
        if (!NetworkMonitor(context.applicationContext).isOnline()) return
        val token = prefs.authToken.first()?.trim().orEmpty()
        if (token.isBlank() || token == DEV_STUB_AUTH_TOKEN) return
        val base =
            prefs.apiBaseUrl.first()?.trim()?.takeIf { it.isNotBlank() }
                ?: return
        val api = EssentialApi(ApiBaseUrls.normalize(base))
        try {
            api.incrementActivity(
                token,
                prompts,
                notes,
                LocalDate.now().toString(),
            )
        } catch (_: Exception) {
        } finally {
            api.close()
        }
    }
}
