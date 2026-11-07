package com.rassvet.essential.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rassvet.essential.data.api.MeAccountResponse
import com.rassvet.essential.data.llm.LlamaRuntimeParams
import com.rassvet.essential.data.llm.LlmComputeBackend
import com.rassvet.essential.data.security.PassphraseKeystore
import com.rassvet.essential.locale.AppLocales
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "essential")

object VaultPreferencesKeys {
    val vaultTreeUri = stringPreferencesKey("vault_tree_uri")
    val apiBaseUrl = stringPreferencesKey("api_base_url")
    val authToken = stringPreferencesKey("auth_token")
    val authAccountEmail = stringPreferencesKey("auth_account_email")
    val cachedSubscriptionStatus = stringPreferencesKey("cached_subscription_status")
    val cachedTokensUsed = longPreferencesKey("cached_tokens_used")
    val cachedTokensQuota = longPreferencesKey("cached_tokens_quota")
    val cachedSubscriptionExpiresAt = longPreferencesKey("cached_subscription_expires_at")
    val cachedSubscriptionPriceRub = intPreferencesKey("cached_subscription_price_rub")
    val cachedSubscriptionPeriodDays = intPreferencesKey("cached_subscription_period_days")
    val snapshotPassphrase = stringPreferencesKey("snapshot_passphrase")
    val needsRegisterWelcome = booleanPreferencesKey("needs_register_welcome")

    val activeGgufName = stringPreferencesKey("active_gguf_name")


    val cloudLlmProvider = stringPreferencesKey("cloud_llm_provider")


    val homeChatMode = stringPreferencesKey("home_chat_mode")

    val openAiCompatBaseUrl = stringPreferencesKey("openai_compat_base_url")
    val openAiCompatApiKey = stringPreferencesKey("openai_compat_api_key")
    val openAiCompatModel = stringPreferencesKey("openai_compat_model")

    val geminiApiKey = stringPreferencesKey("gemini_api_key")
    val geminiModel = stringPreferencesKey("gemini_model")

    val llmTemperature = floatPreferencesKey("llm_temperature")
    val llmTopP = floatPreferencesKey("llm_top_p")
    val llmTopK = intPreferencesKey("llm_top_k")
    val llmMaxTokens = intPreferencesKey("llm_max_tokens")
    val llmContextSize = intPreferencesKey("llm_context_size")
    val llmNThreads = intPreferencesKey("llm_n_threads")

    val llmComputeBackend = stringPreferencesKey("llm_compute_backend")

    val localWebResearchEnabled = booleanPreferencesKey("local_web_research_enabled")

    val localWebResearchEndpoint = stringPreferencesKey("local_web_research_endpoint")


    val compressImages = booleanPreferencesKey("compress_images")
    val autoTitleChats = booleanPreferencesKey("auto_title_chats")
    val semanticSearchEnabled = booleanPreferencesKey("semantic_search_enabled")
    val streamReplies = booleanPreferencesKey("stream_replies")
    val thinkingEnabled = booleanPreferencesKey("thinking_enabled")
    val speechDenoiseEnabled = booleanPreferencesKey("speech_denoise_enabled")
    val speechSeparationEnabled = booleanPreferencesKey("speech_separation_enabled")
    val autoScrollTranscript = booleanPreferencesKey("auto_scroll_transcript")
    val chatHapticsEnabled = booleanPreferencesKey("chat_haptics_enabled")
    val typewriterEffect = booleanPreferencesKey("typewriter_effect")


    val appLocaleTag = stringPreferencesKey("app_locale_tag")
    val themeMode = stringPreferencesKey("theme_mode")
    val dynamicColor = booleanPreferencesKey("dynamic_color")
    val expressiveMotion = booleanPreferencesKey("expressive_motion")
    val ggufAllowCellular = booleanPreferencesKey("gguf_allow_cellular")


    val readerFontId = stringPreferencesKey("reader_font_id")
    val readerFontSizeSp = floatPreferencesKey("reader_font_size_sp")
    val readerFontWeight = intPreferencesKey("reader_font_weight")
    val usageActivity = stringPreferencesKey("usage_activity_v1")
}

class VaultPreferencesRepository(
    private val context: Context,
) {
    private val passphraseKeystore = PassphraseKeystore(context)

    val vaultTreeUri: Flow<String?> =
        context.dataStore.data.map { it[VaultPreferencesKeys.vaultTreeUri] }

    val apiBaseUrl: Flow<String?> =
        context.dataStore.data.map { it[VaultPreferencesKeys.apiBaseUrl] }

    val authToken: Flow<String?> =
        context.dataStore.data.map { it[VaultPreferencesKeys.authToken] }

    val authAccountEmail: Flow<String?> =
        context.dataStore.data.map { it[VaultPreferencesKeys.authAccountEmail] }

    val cachedMeAccount: Flow<MeAccountResponse?> =
        context.dataStore.data.map { prefs ->
            val status = prefs[VaultPreferencesKeys.cachedSubscriptionStatus] ?: return@map null
            MeAccountResponse(
                status,
                prefs[VaultPreferencesKeys.cachedTokensUsed] ?: 0L,
                prefs[VaultPreferencesKeys.cachedTokensQuota] ?: 0L,
                0L,
                0L,
                0L,
                prefs[VaultPreferencesKeys.cachedSubscriptionExpiresAt],
                prefs[VaultPreferencesKeys.cachedSubscriptionPriceRub] ?: 10,
                prefs[VaultPreferencesKeys.cachedSubscriptionPeriodDays] ?: 31,
            )
        }

    val snapshotPassphrase: Flow<String?> =
        context.dataStore.data.map { prefs ->
            passphraseKeystore.decrypt(prefs[VaultPreferencesKeys.snapshotPassphrase])
        }

    val needsRegisterWelcome: Flow<Boolean> =
        context.dataStore.data.map { it[VaultPreferencesKeys.needsRegisterWelcome] ?: false }

    val activeGgufName: Flow<String?> =
        context.dataStore.data.map { it[VaultPreferencesKeys.activeGgufName] }

    val cloudLlmProvider: Flow<String> =
        context.dataStore.data.map { it[VaultPreferencesKeys.cloudLlmProvider] ?: "local" }

    val homeChatMode: Flow<String> =
        context.dataStore.data.map { it[VaultPreferencesKeys.homeChatMode] ?: "local" }

    val openAiCompatBaseUrl: Flow<String?> =
        context.dataStore.data.map { it[VaultPreferencesKeys.openAiCompatBaseUrl] }

    val openAiCompatApiKey: Flow<String?> =
        context.dataStore.data.map { it[VaultPreferencesKeys.openAiCompatApiKey] }

    val openAiCompatModel: Flow<String?> =
        context.dataStore.data.map { it[VaultPreferencesKeys.openAiCompatModel] }

    val geminiApiKey: Flow<String?> =
        context.dataStore.data.map { it[VaultPreferencesKeys.geminiApiKey] }

    val geminiModel: Flow<String?> =
        context.dataStore.data.map { it[VaultPreferencesKeys.geminiModel] }

    val compressImages: Flow<Boolean> =
        context.dataStore.data.map { it[VaultPreferencesKeys.compressImages] ?: true }

    val autoTitleChats: Flow<Boolean> =
        context.dataStore.data.map { it[VaultPreferencesKeys.autoTitleChats] ?: true }

    val semanticSearchEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[VaultPreferencesKeys.semanticSearchEnabled] ?: true }

    val streamReplies: Flow<Boolean> =
        context.dataStore.data.map { it[VaultPreferencesKeys.streamReplies] ?: true }

    /** Reasoning mode for models that support it (Qwen3, DeepSeek R1). Off by default: faster on phones. */
    val thinkingEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[VaultPreferencesKeys.thinkingEnabled] ?: false }

    /** DTLN-driven speech detection (helps in noise). */
    val speechDenoiseEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[VaultPreferencesKeys.speechDenoiseEnabled] ?: true }

    /** MossFormer2 separation of overlapped speech (slower than real time on phones). */
    val speechSeparationEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[VaultPreferencesKeys.speechSeparationEnabled] ?: true }

    /** Follow the playhead: scroll the transcript to the quote being spoken. */
    val autoScrollTranscript: Flow<Boolean> =
        context.dataStore.data.map { it[VaultPreferencesKeys.autoScrollTranscript] ?: true }

    suspend fun setSpeechDenoiseEnabled(v: Boolean) { context.dataStore.edit { it[VaultPreferencesKeys.speechDenoiseEnabled] = v } }
    suspend fun setSpeechSeparationEnabled(v: Boolean) { context.dataStore.edit { it[VaultPreferencesKeys.speechSeparationEnabled] = v } }

    val chatHapticsEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[VaultPreferencesKeys.chatHapticsEnabled] ?: true }

    val typewriterEffect: Flow<Boolean> =
        context.dataStore.data.map { it[VaultPreferencesKeys.typewriterEffect] ?: true }

    val appLocaleTag: Flow<String> =
        context.dataStore.data.map { it[VaultPreferencesKeys.appLocaleTag] ?: "system" }

    val themeMode: Flow<String> =
        context.dataStore.data.map { it[VaultPreferencesKeys.themeMode] ?: "system" }

    val dynamicColor: Flow<Boolean> =
        context.dataStore.data.map { it[VaultPreferencesKeys.dynamicColor] ?: true }

    val expressiveMotion: Flow<Boolean> =
        context.dataStore.data.map { it[VaultPreferencesKeys.expressiveMotion] ?: true }

    val ggufAllowCellular: Flow<Boolean> =
        context.dataStore.data.map { it[VaultPreferencesKeys.ggufAllowCellular] ?: true }

    val llmComputeBackend: Flow<String> =
        context.dataStore.data.map { it[VaultPreferencesKeys.llmComputeBackend] ?: LlmComputeBackend.CPU }

    val localWebResearchEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[VaultPreferencesKeys.localWebResearchEnabled] ?: true }

    val localWebResearchEndpoint: Flow<String> =
        context.dataStore.data.map { it[VaultPreferencesKeys.localWebResearchEndpoint].orEmpty() }

    suspend fun hasLlmComputeBackend(): Boolean =
        context.dataStore.data.first().contains(VaultPreferencesKeys.llmComputeBackend)

    suspend fun resolveLlmComputeBackend(): String {
        val snapshot = context.dataStore.data.first()
        snapshot[VaultPreferencesKeys.llmComputeBackend]?.let { return it }
        return LlmComputeBackend.CPU
    }

    suspend fun setLlmComputeBackend(backend: String) {
        context.dataStore.edit { prefs ->
            prefs[VaultPreferencesKeys.llmComputeBackend] =
                if (backend == LlmComputeBackend.GPU) LlmComputeBackend.GPU else LlmComputeBackend.CPU
        }
    }

    suspend fun resolveLocalWebResearchEnabled(): Boolean =
        context.dataStore.data.first()[VaultPreferencesKeys.localWebResearchEnabled] ?: true

    suspend fun resolveLocalWebResearchEndpoint(): String? =
        context.dataStore.data.first()[VaultPreferencesKeys.localWebResearchEndpoint]
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    suspend fun setLocalWebResearchEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[VaultPreferencesKeys.localWebResearchEnabled] = enabled
        }
    }

    suspend fun setLocalWebResearchEndpoint(endpoint: String?) {
        context.dataStore.edit { prefs ->
            val trimmed = endpoint?.trim().orEmpty()
            if (trimmed.isBlank()) {
                prefs.remove(VaultPreferencesKeys.localWebResearchEndpoint)
            } else {
                prefs[VaultPreferencesKeys.localWebResearchEndpoint] = trimmed
            }
        }
    }

    val readerFontId: Flow<String> =
        context.dataStore.data.map { it[VaultPreferencesKeys.readerFontId] ?: "georgia" }

    val readerFontSizeSp: Flow<Float> =
        context.dataStore.data.map { it[VaultPreferencesKeys.readerFontSizeSp] ?: 16f }

    val readerFontWeight: Flow<Int> =
        context.dataStore.data.map { it[VaultPreferencesKeys.readerFontWeight] ?: 400 }

    suspend fun setBooleanPref(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, v: Boolean) {
        context.dataStore.edit { it[key] = v }
    }

    suspend fun setStringPref(key: androidx.datastore.preferences.core.Preferences.Key<String>, v: String) {
        context.dataStore.edit { it[key] = v }
    }

    suspend fun setAppLocaleTag(tag: String) {
        context.dataStore.edit { it[VaultPreferencesKeys.appLocaleTag] = tag }
    }

    suspend fun hasAppLocaleTag(): Boolean =
        context.dataStore.data.first().contains(VaultPreferencesKeys.appLocaleTag)

    suspend fun resolveAppLocaleTag(): String {
        val snapshot = context.dataStore.data.first()
        if (VaultPreferencesKeys.appLocaleTag in snapshot) {
            return snapshot[VaultPreferencesKeys.appLocaleTag] ?: "system"
        }
        val lang = context.resources.configuration.locales[0].language
        return AppLocales.tagForDeviceLanguage(lang)
    }

    suspend fun setReaderTypography(fontId: String, fontSizeSp: Float, fontWeight: Int) {
        context.dataStore.edit { prefs ->
            prefs[VaultPreferencesKeys.readerFontId] = fontId
            prefs[VaultPreferencesKeys.readerFontSizeSp] = fontSizeSp.coerceIn(13f, 24f)
            prefs[VaultPreferencesKeys.readerFontWeight] = fontWeight.coerceIn(300, 700)
        }
    }

    suspend fun setVaultTreeUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(VaultPreferencesKeys.vaultTreeUri)
            else prefs[VaultPreferencesKeys.vaultTreeUri] = uri
        }
    }

    suspend fun clearVaultSelection() {
        setVaultTreeUri(null)
    }

    suspend fun setApiBaseUrl(url: String?) {
        context.dataStore.edit { prefs ->
            if (url == null) prefs.remove(VaultPreferencesKeys.apiBaseUrl)
            else prefs[VaultPreferencesKeys.apiBaseUrl] = com.rassvet.essential.data.api.ApiBaseUrls.normalize(url)
        }
    }

    suspend fun setAccountQuotaCache(
        subscriptionStatus: String,
        tokensUsed: Long,
        tokensQuota: Long,
        subscriptionExpiresAtEpochMs: Long? = null,
        subscriptionPriceRub: Int = 10,
        subscriptionPeriodDays: Int = 31,
    ) {
        context.dataStore.edit { prefs ->
            prefs[VaultPreferencesKeys.cachedSubscriptionStatus] = subscriptionStatus
            prefs[VaultPreferencesKeys.cachedTokensUsed] = tokensUsed
            prefs[VaultPreferencesKeys.cachedTokensQuota] = tokensQuota
            if (subscriptionExpiresAtEpochMs != null && subscriptionExpiresAtEpochMs > 0L) {
                prefs[VaultPreferencesKeys.cachedSubscriptionExpiresAt] = subscriptionExpiresAtEpochMs
            } else {
                prefs.remove(VaultPreferencesKeys.cachedSubscriptionExpiresAt)
            }
            prefs[VaultPreferencesKeys.cachedSubscriptionPriceRub] = subscriptionPriceRub
            prefs[VaultPreferencesKeys.cachedSubscriptionPeriodDays] = subscriptionPeriodDays
        }
    }

    private fun clearAccountQuotaCache(prefs: androidx.datastore.preferences.core.MutablePreferences) {
        prefs.remove(VaultPreferencesKeys.cachedSubscriptionStatus)
        prefs.remove(VaultPreferencesKeys.cachedTokensUsed)
        prefs.remove(VaultPreferencesKeys.cachedTokensQuota)
        prefs.remove(VaultPreferencesKeys.cachedSubscriptionExpiresAt)
        prefs.remove(VaultPreferencesKeys.cachedSubscriptionPriceRub)
        prefs.remove(VaultPreferencesKeys.cachedSubscriptionPeriodDays)
    }

    suspend fun setAuthToken(token: String?, accountEmail: String? = null) {
        context.dataStore.edit { prefs ->
            if (token == null) {
                prefs.remove(VaultPreferencesKeys.authToken)
                prefs.remove(VaultPreferencesKeys.authAccountEmail)
                prefs[VaultPreferencesKeys.needsRegisterWelcome] = false
                prefs.remove(VaultPreferencesKeys.vaultTreeUri)
                clearAccountQuotaCache(prefs)
            } else {
                prefs[VaultPreferencesKeys.authToken] =
                    com.rassvet.essential.data.api.EssentialApi.normalizeAuthToken(token)
                val email = accountEmail?.trim().orEmpty()
                if (email.isNotEmpty()) {
                    prefs[VaultPreferencesKeys.authAccountEmail] = email
                }
            }
        }
    }

    suspend fun prepareOfflineMode() {
        context.dataStore.edit { prefs ->
            prefs.remove(VaultPreferencesKeys.authToken)
            prefs.remove(VaultPreferencesKeys.authAccountEmail)
            prefs[VaultPreferencesKeys.needsRegisterWelcome] = false
            clearAccountQuotaCache(prefs)
            prefs[VaultPreferencesKeys.homeChatMode] = "local"
            prefs[VaultPreferencesKeys.localWebResearchEnabled] = false
            if (prefs[VaultPreferencesKeys.activeGgufName] == "essentialai") {
                prefs.remove(VaultPreferencesKeys.activeGgufName)
            }
            prefs.remove(VaultPreferencesKeys.openAiCompatApiKey)
            prefs.remove(VaultPreferencesKeys.geminiApiKey)
            prefs.remove(VaultPreferencesKeys.openAiCompatBaseUrl)
            prefs.remove(VaultPreferencesKeys.openAiCompatModel)
            prefs.remove(VaultPreferencesKeys.geminiModel)
            prefs[VaultPreferencesKeys.cloudLlmProvider] = "local"
            if (prefs[VaultPreferencesKeys.dynamicColor] == null) prefs[VaultPreferencesKeys.dynamicColor] = true
        }
    }

    suspend fun setNeedsRegisterWelcome(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[VaultPreferencesKeys.needsRegisterWelcome] = value
        }
    }


    suspend fun setAuthTokenAfterRegister(token: String, accountEmail: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[VaultPreferencesKeys.authToken] =
                com.rassvet.essential.data.api.EssentialApi.normalizeAuthToken(token)
            prefs[VaultPreferencesKeys.needsRegisterWelcome] = true
            prefs.remove(VaultPreferencesKeys.vaultTreeUri)
            val email = accountEmail?.trim().orEmpty()
            if (email.isNotEmpty()) {
                prefs[VaultPreferencesKeys.authAccountEmail] = email
            }
        }
    }

    suspend fun setSnapshotPassphrase(pass: String?) {
        context.dataStore.edit { prefs ->
            if (pass.isNullOrBlank()) {
                prefs.remove(VaultPreferencesKeys.snapshotPassphrase)
            } else {
                prefs[VaultPreferencesKeys.snapshotPassphrase] = passphraseKeystore.encrypt(pass)
            }
        }
    }

    suspend fun getSnapshotPassphrasePlain(): String? =
        passphraseKeystore.decrypt(context.dataStore.data.first()[VaultPreferencesKeys.snapshotPassphrase])

    suspend fun setActiveGguf(fileName: String?) {
        context.dataStore.edit { prefs ->
            if (fileName.isNullOrBlank()) {
                prefs.remove(VaultPreferencesKeys.activeGgufName)
            } else {
                prefs[VaultPreferencesKeys.activeGgufName] = fileName
            }
        }
    }

    suspend fun setHomeChatMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[VaultPreferencesKeys.homeChatMode] = mode
        }
    }

    suspend fun setCloudLlmProvider(value: String) {
        context.dataStore.edit { prefs ->
            prefs[VaultPreferencesKeys.cloudLlmProvider] = value
        }
    }

    suspend fun setOpenAiCompat(
        baseUrl: String?,
        apiKey: String?,
        model: String?,
    ) {
        context.dataStore.edit { prefs ->
            if (baseUrl.isNullOrBlank()) prefs.remove(VaultPreferencesKeys.openAiCompatBaseUrl)
            else prefs[VaultPreferencesKeys.openAiCompatBaseUrl] = baseUrl.trim()
            if (apiKey.isNullOrBlank()) prefs.remove(VaultPreferencesKeys.openAiCompatApiKey)
            else prefs[VaultPreferencesKeys.openAiCompatApiKey] = apiKey.trim()
            if (model.isNullOrBlank()) prefs.remove(VaultPreferencesKeys.openAiCompatModel)
            else prefs[VaultPreferencesKeys.openAiCompatModel] = model.trim()
        }
    }

    suspend fun setGemini(
        apiKey: String?,
        model: String?,
    ) {
        context.dataStore.edit { prefs ->
            if (apiKey.isNullOrBlank()) prefs.remove(VaultPreferencesKeys.geminiApiKey)
            else prefs[VaultPreferencesKeys.geminiApiKey] = apiKey.trim()
            if (model.isNullOrBlank()) prefs.remove(VaultPreferencesKeys.geminiModel)
            else prefs[VaultPreferencesKeys.geminiModel] = model.trim()
        }
    }

    suspend fun setLlmRuntimeParams(params: LlamaRuntimeParams) {
        context.dataStore.edit { prefs ->
            prefs[VaultPreferencesKeys.llmTemperature] = params.temperature
            prefs[VaultPreferencesKeys.llmTopP] = params.topP
            prefs[VaultPreferencesKeys.llmTopK] = params.topK
            prefs[VaultPreferencesKeys.llmMaxTokens] = params.maxTokens
            prefs[VaultPreferencesKeys.llmContextSize] = params.contextSize
            prefs[VaultPreferencesKeys.llmNThreads] = params.nThreads
        }
    }

    suspend fun getLlmRuntimeParams(): LlamaRuntimeParams {
        val p = context.dataStore.data.first()
        val d = LlamaRuntimeParams.Default
        return LlamaRuntimeParams(
            temperature = p[VaultPreferencesKeys.llmTemperature] ?: d.temperature,
            topP = p[VaultPreferencesKeys.llmTopP] ?: d.topP,
            topK = p[VaultPreferencesKeys.llmTopK] ?: d.topK,
            maxTokens = p[VaultPreferencesKeys.llmMaxTokens] ?: d.maxTokens,
            contextSize = p[VaultPreferencesKeys.llmContextSize] ?: d.contextSize,
            nThreads = p[VaultPreferencesKeys.llmNThreads] ?: d.nThreads,
        )
    }

    val usageHeatmap: Flow<List<Int>> =
        context.dataStore.data.map { prefs ->
            val usage = decodeUsageActivity(prefs[VaultPreferencesKeys.usageActivity])
            val today = java.time.LocalDate.now()
            List(USAGE_HEATMAP_DAYS) { index ->
                val day = today.minusDays((USAGE_HEATMAP_DAYS - 1 - index).toLong())
                usage[day.format(USAGE_DAY_FORMATTER)]?.total() ?: 0
            }
        }

    suspend fun bumpUsage(prompts: Int, notes: Int) {
        if (prompts == 0 && notes == 0) return
        val today = java.time.LocalDate.now().format(USAGE_DAY_FORMATTER)
        context.dataStore.edit { prefs ->
            val map = decodeUsageActivity(prefs[VaultPreferencesKeys.usageActivity]).toMutableMap()
            val current = map[today] ?: intArrayOf(0, 0)
            current[0] = (current[0] + prompts).coerceAtLeast(0)
            current[1] = (current[1] + notes).coerceAtLeast(0)
            map[today] = current
            pruneUsageActivity(map)
            prefs[VaultPreferencesKeys.usageActivity] = encodeUsageActivity(map)
        }
    }

    suspend fun mergeUsageFromServer(days: List<ServerUsageDay>) {
        if (days.isEmpty()) return
        context.dataStore.edit { prefs ->
            val map = decodeUsageActivity(prefs[VaultPreferencesKeys.usageActivity]).toMutableMap()
            for (day in days) {
                if (day.date.isBlank()) continue
                val remote = intArrayOf(day.prompts.coerceAtLeast(0), day.notes.coerceAtLeast(0))
                val local = map[day.date]
                if (local == null) {
                    map[day.date] = remote
                } else {
                    local[0] = maxOf(local[0], remote[0])
                    local[1] = maxOf(local[1], remote[1])
                }
            }
            pruneUsageActivity(map)
            prefs[VaultPreferencesKeys.usageActivity] = encodeUsageActivity(map)
        }
    }

    private fun pruneUsageActivity(map: MutableMap<String, IntArray>) {
        val cutoff = java.time.LocalDate.now().minusDays(USAGE_RETAIN_DAYS.toLong())
        map.keys.toList().forEach { key ->
            runCatching { java.time.LocalDate.parse(key, USAGE_DAY_FORMATTER) }
                .getOrNull()
                ?.takeIf { it.isBefore(cutoff) }
                ?.let { map.remove(key) }
        }
    }

    private fun encodeUsageActivity(map: Map<String, IntArray>): String =
        map.entries.joinToString(";") { (day, counts) ->
            "${day}:${counts[0]},${counts[1]}"
        }

    private fun decodeUsageActivity(raw: String?): Map<String, IntArray> {
        if (raw.isNullOrBlank()) return emptyMap()
        return buildMap {
            for (chunk in raw.split(';')) {
                if (chunk.isBlank()) continue
                val parts = chunk.split(':', limit = 2)
                if (parts.size != 2) continue
                val day = parts[0].trim()
                if (day.isBlank()) continue
                val counts = parts[1].split(',')
                val prompts = counts.getOrNull(0)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val notes = counts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                put(day, intArrayOf(prompts, notes))
            }
        }
    }

    private fun IntArray.total(): Int = this[0] + this[1]
}

data class ServerUsageDay(
    val date: String,
    val prompts: Int,
    val notes: Int,
)

private const val USAGE_HEATMAP_DAYS = 30
private const val USAGE_RETAIN_DAYS = 120
private val USAGE_DAY_FORMATTER = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE


