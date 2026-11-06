package com.rassvet.essential.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.rassvet.essential.data.api.JwtPayload
import com.rassvet.essential.data.api.ApiBaseUrls
import com.rassvet.essential.data.api.DEV_STUB_AUTH_TOKEN
import com.rassvet.essential.data.api.EssentialApi
import com.rassvet.essential.data.api.EssentialHttpException
import androidx.appcompat.app.AppCompatActivity
import com.rassvet.essential.data.api.MeAccountResponse
import com.rassvet.essential.data.api.SubscriptionPaymentItem
import com.rassvet.essential.data.api.fetchAndCacheMeAccount
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.rassvet.essential.R
import com.rassvet.essential.data.local.VaultPreferencesKeys
import com.rassvet.essential.data.local.VaultPreferencesRepository
import com.rassvet.essential.data.llm.LlmComputeBackend
import com.rassvet.essential.data.llm.LiteRtLmCapabilities
import com.rassvet.essential.data.llm.LocalWebResearch
import com.rassvet.essential.locale.AppLocales
import com.rassvet.essential.locale.PlanDisplayLabels
import com.rassvet.essential.ui.legal.openSupportEmail
import com.rassvet.essential.ui.legal.openUrl
import com.rassvet.essential.data.vault.VaultDocuments
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: VaultPreferencesRepository,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onChangeVault: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fontId by prefs.readerFontId.collectAsState(initial = "georgia")
    val fontSize by prefs.readerFontSizeSp.collectAsState(initial = 16f)
    val fontWeight by prefs.readerFontWeight.collectAsState(initial = 400)
    val streamReplies by prefs.streamReplies.collectAsState(initial = true)
    val chatHaptics by prefs.chatHapticsEnabled.collectAsState(initial = true)
    val typewriterEffect by prefs.typewriterEffect.collectAsState(initial = true)
    val semanticSearch by prefs.semanticSearchEnabled.collectAsState(initial = true)
    val autoTitleChats by prefs.autoTitleChats.collectAsState(initial = true)
    val compressImages by prefs.compressImages.collectAsState(initial = true)
    val llmComputeBackend by prefs.llmComputeBackend.collectAsState(initial = LlmComputeBackend.CPU)
    val localWebResearch by prefs.localWebResearchEnabled.collectAsState(initial = true)
    val localWebResearchEndpoint by prefs.localWebResearchEndpoint.collectAsState(initial = "")
    var showWebResearchDialog by remember { mutableStateOf(false) }
    var webResearchDraft by remember { mutableStateOf("") }
    val gpuBackendBuilt = remember { LiteRtLmCapabilities.isAvailable }
    val gpuAvailable = remember { LiteRtLmCapabilities.isGpuAvailable() }
    val gpuDeviceName =
        remember(gpuBackendBuilt) {
            if (gpuBackendBuilt) LiteRtLmCapabilities.gpuDeviceDescription().trim() else ""
        }
    val appLocaleTag by prefs.appLocaleTag.collectAsState(initial = AppLocales.SYSTEM)
    var showLanguageDialog by remember { mutableStateOf(false) }
    val hostActivity = context as? AppCompatActivity
    val themeMode by prefs.themeMode.collectAsState(initial = "system")
    val dynamicColor by prefs.dynamicColor.collectAsState(initial = true)
    val authToken by prefs.authToken.collectAsState(initial = null)
    val authAccountEmail by prefs.authAccountEmail.collectAsState(initial = null)
    val apiBase by prefs.apiBaseUrl.collectAsState(initial = null)
    var accountInfo by remember { mutableStateOf<MeAccountResponse?>(null) }
    var accountLoading by remember { mutableStateOf(false) }
    var accountLoadFailed by remember { mutableStateOf(false) }
    var accountLoadError by remember { mutableStateOf<Throwable?>(null) }
    var accountDevStub by remember { mutableStateOf(false) }
    var accountRefreshKey by remember { mutableIntStateOf(0) }
    var billingLoading by remember { mutableStateOf(false) }
    var subscriptionPayments by remember { mutableStateOf<List<SubscriptionPaymentItem>?>(null) }
    var paymentsLoading by remember { mutableStateOf(false) }
    var showPaymentsDialog by remember { mutableStateOf(false) }
    val cachedAccount by prefs.cachedMeAccount.collectAsState(initial = null)
    val vaultStored by prefs.vaultTreeUri.collectAsState(initial = null)
    val vaultLabel =
        remember(vaultStored, context) {
            val stored = vaultStored?.trim().orEmpty()
            if (stored.isEmpty()) {
                context.getString(R.string.settings_vault_not_set)
            } else {
                VaultDocuments.vaultDisplayName(context, stored)
            }
        }
    val reader = ReaderTypographySettings(fontId, fontSize, fontWeight)
    val isSignedIn = !authToken.isNullOrBlank()
    val accountLabel =
        when {
            isSignedIn && !authAccountEmail.isNullOrBlank() -> authAccountEmail!!
            isSignedIn -> stringResource(R.string.home_drawer_account_default)
            else -> stringResource(R.string.settings_account_signed_out)
        }

    fun persist(font: String = fontId, size: Float = fontSize, weight: Int = fontWeight) {
        scope.launch { prefs.setReaderTypography(font, size, weight) }
    }

    fun setBool(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        scope.launch { prefs.setBooleanPref(key, value) }
    }

    fun startProCheckout() {
        val token = authToken?.trim().orEmpty()
        val base =
            ApiBaseUrls.normalize(
                apiBase?.trim()?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.default_api_base).trim(),
            )
        if (token.isEmpty() || base.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.settings_subscription_sign_in), Toast.LENGTH_LONG).show()
            return
        }
        if (billingLoading) return
        billingLoading = true
        scope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val api = EssentialApi(base)
                        try {
                            api.createSubscriptionCheckout(token)
                        } finally {
                            api.close()
                        }
                    }
                }
            billingLoading = false
            result.fold(
                onSuccess = { checkout ->
                    context.openUrl(checkout.paymentUrl)
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_subscription_payment_opened),
                        Toast.LENGTH_LONG,
                    ).show()
                },
                onFailure = { err ->
                    val msg =
                        when {
                            err is EssentialHttpException && err.statusCode == 502 ->
                                context.getString(R.string.settings_subscription_billing_unavailable)
                            err is EssentialHttpException && err.statusCode == 401 ->
                                context.getString(R.string.settings_quota_error_401)
                            err is UnknownHostException || err is SocketTimeoutException ->
                                context.getString(R.string.settings_quota_error_network)
                            else -> context.getString(R.string.settings_subscription_checkout_error)
                        }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    LaunchedEffect(authToken, apiBase, accountRefreshKey) {
        val token = authToken?.trim().orEmpty()
        val base =
            ApiBaseUrls.normalize(
                apiBase?.trim()?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.default_api_base).trim(),
            )
        if (token.isEmpty() || base.isEmpty()) {
            accountInfo = null
            accountLoadFailed = false
            accountLoadError = null
            accountDevStub = false
            return@LaunchedEffect
        }
        if (token == DEV_STUB_AUTH_TOKEN) {
            accountInfo = null
            accountLoadFailed = false
            accountLoadError = null
            accountDevStub = true
            accountLoading = false
            return@LaunchedEffect
        }
        accountDevStub = false
        accountLoading = true
        accountLoadFailed = false
        accountLoadError = null
        val result =
            withContext(Dispatchers.IO) {
                runCatching { prefs.fetchAndCacheMeAccount(base, token) }
            }
        accountInfo = result.getOrNull()
        accountLoadFailed = result.isFailure
        accountLoadError = result.exceptionOrNull()
        accountLoading = false
    }

    LaunchedEffect(showPaymentsDialog, authToken, apiBase) {
        if (!showPaymentsDialog) return@LaunchedEffect
        val token = authToken?.trim().orEmpty()
        val base =
            ApiBaseUrls.normalize(
                apiBase?.trim()?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.default_api_base).trim(),
            )
        if (token.isEmpty() || base.isEmpty() || token == DEV_STUB_AUTH_TOKEN) {
            subscriptionPayments = emptyList()
            paymentsLoading = false
            return@LaunchedEffect
        }
        paymentsLoading = true
        subscriptionPayments = null
        val paymentsResult =
            withContext(Dispatchers.IO) {
                runCatching {
                    val api = EssentialApi(base)
                    try {
                        api.listSubscriptionPayments(token)
                    } finally {
                        api.close()
                    }
                }
            }
        subscriptionPayments = paymentsResult.getOrNull().orEmpty()
        paymentsLoading = false
    }

    @Composable
    fun paymentStatusLabel(status: String): String {
        return when (status.trim().uppercase(Locale.US)) {
            "CONFIRMED" -> stringResource(R.string.settings_payment_status_confirmed)
            "PENDING" -> stringResource(R.string.settings_payment_status_pending)
            "CANCELED", "CANCELLED" -> stringResource(R.string.settings_payment_status_canceled)
            "FAILED" -> stringResource(R.string.settings_payment_status_failed)
            else -> status
        }
    }

    @Composable
    fun quotaErrorMessage(error: Throwable?): String {
        if (error == null) return stringResource(R.string.settings_quota_error)
        if (error is EssentialHttpException) {
            return when (error.statusCode) {
                401 -> stringResource(R.string.settings_quota_error_401)
                404 -> stringResource(R.string.settings_quota_error_404)
                else -> stringResource(R.string.settings_quota_error_http, error.statusCode)
            }
        }
        if (error is UnknownHostException || error is SocketTimeoutException) {
            return stringResource(R.string.settings_quota_error_network)
        }
        val cause = error.cause
        if (cause is EssentialHttpException) {
            return quotaErrorMessage(cause)
        }
        if (cause is UnknownHostException || cause is SocketTimeoutException) {
            return stringResource(R.string.settings_quota_error_network)
        }
        return stringResource(R.string.settings_quota_error)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val fontScroll = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsSectionTitle(stringResource(R.string.settings_section_reading))

            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = stringResource(R.string.settings_preview_label),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.settings_preview_sample),
                        style = reader.bodyStyle(MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.settings_font_label),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(fontScroll),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Spacer(Modifier.width(20.dp))
                    ReaderFontCatalog.forEach { option ->
                        FontPickCard(
                            option = option,
                            selected = option.id == fontId,
                            onClick = { persist(font = option.id) },
                        )
                    }
                    Spacer(Modifier.width(20.dp))
                }
            }

            SettingsCard(Modifier.padding(horizontal = 20.dp)) {
                ReaderSettingRow(
                    icon = { Icon(Icons.Outlined.FormatSize, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_font_size),
                    value = fontSize.toInt().toString(),
                )
                Slider(
                    value = fontSize,
                    onValueChange = { persist(size = it) },
                    valueRange = 13f..24f,
                    steps = 10,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                ReaderSettingRow(
                    icon = { Icon(Icons.Outlined.TextFormat, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_font_weight),
                    value = fontWeightLabel(fontWeight),
                    onClick = {
                        val next = when (fontWeight) {
                            300 -> 400
                            400 -> 500
                            500 -> 600
                            else -> 300
                        }
                        persist(weight = next)
                    },
                )
            }

            SettingsSectionTitle(stringResource(R.string.settings_section_chat))

            SettingsCard(Modifier.padding(horizontal = 20.dp)) {
                ToggleSettingRow(
                    icon = { Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_stream_replies),
                    subtitle = stringResource(R.string.settings_stream_replies_hint),
                    checked = streamReplies,
                    onCheckedChange = { setBool(VaultPreferencesKeys.streamReplies, it) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                ToggleSettingRow(
                    icon = { Icon(Icons.Outlined.Vibration, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_chat_haptics),
                    subtitle = stringResource(R.string.settings_chat_haptics_hint),
                    checked = chatHaptics,
                    onCheckedChange = { setBool(VaultPreferencesKeys.chatHapticsEnabled, it) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                ToggleSettingRow(
                    icon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_typewriter),
                    subtitle = stringResource(R.string.settings_typewriter_hint),
                    checked = typewriterEffect,
                    onCheckedChange = { setBool(VaultPreferencesKeys.typewriterEffect, it) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                ToggleSettingRow(
                    icon = { Icon(Icons.Outlined.Search, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_semantic_search),
                    subtitle = stringResource(R.string.settings_semantic_search_hint),
                    checked = semanticSearch,
                    onCheckedChange = { setBool(VaultPreferencesKeys.semanticSearchEnabled, it) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                ToggleSettingRow(
                    icon = { Icon(Icons.Outlined.Title, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_auto_title_chats),
                    subtitle = stringResource(R.string.settings_auto_title_chats_hint),
                    checked = autoTitleChats,
                    onCheckedChange = { setBool(VaultPreferencesKeys.autoTitleChats, it) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                ToggleSettingRow(
                    icon = { Icon(Icons.Outlined.Compress, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_compress_images),
                    subtitle = stringResource(R.string.settings_compress_images_hint),
                    checked = compressImages,
                    onCheckedChange = { setBool(VaultPreferencesKeys.compressImages, it) },
                )
            }

            SettingsSectionTitle(stringResource(R.string.settings_local_llm_section))

            SettingsCard(Modifier.padding(horizontal = 20.dp)) {
                ReaderSettingRow(
                    icon = { Icon(Icons.Outlined.Memory, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_llm_compute_backend),
                    value =
                        when {
                            !gpuBackendBuilt ->
                                stringResource(R.string.settings_llm_compute_cpu_only_build)
                            llmComputeBackend == LlmComputeBackend.GPU ->
                                stringResource(R.string.settings_llm_compute_gpu)
                            else -> stringResource(R.string.settings_llm_compute_cpu)
                        },
                    onClick =
                        if (gpuBackendBuilt && gpuAvailable) {
                            {
                                val next =
                                    if (llmComputeBackend == LlmComputeBackend.GPU) {
                                        LlmComputeBackend.CPU
                                    } else {
                                        LlmComputeBackend.GPU
                                    }
                                scope.launch { prefs.setLlmComputeBackend(next) }
                            }
                        } else {
                            null
                        },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                Text(
                    text =
                        when {
                            !gpuBackendBuilt ->
                                stringResource(R.string.settings_llm_compute_cpu_only_build_hint)
                            gpuAvailable && gpuDeviceName.isNotBlank() ->
                                stringResource(
                                    R.string.settings_llm_compute_gpu_device,
                                    gpuDeviceName,
                                )
                            gpuAvailable ->
                                stringResource(R.string.settings_llm_compute_gpu_available)
                            else -> stringResource(R.string.settings_llm_compute_gpu_unavailable)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
            }

            SettingsSectionTitle(stringResource(R.string.settings_section_appearance))

            SettingsCard(Modifier.padding(horizontal = 20.dp)) {
                ReaderSettingRow(
                    icon = { Icon(Icons.Outlined.Language, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_language),
                    value =
                        when (appLocaleTag) {
                            AppLocales.RU -> stringResource(R.string.lang_russian)
                            AppLocales.EN -> stringResource(R.string.lang_english)
                            else -> stringResource(R.string.lang_system)
                        },
                    onClick = { showLanguageDialog = true },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                ReaderSettingRow(
                    icon = { Icon(Icons.Outlined.DarkMode, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_theme),
                    value = when (themeMode) {
                        "dark" -> stringResource(R.string.settings_theme_dark)
                        "light" -> stringResource(R.string.settings_theme_light)
                        else -> stringResource(R.string.settings_theme_system)
                    },
                    onClick = {
                        val next = when (themeMode) {
                            "system" -> "dark"
                            "dark" -> "light"
                            else -> "system"
                        }
                        scope.launch {
                            prefs.setStringPref(VaultPreferencesKeys.themeMode, next)
                        }
                    },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                ToggleSettingRow(
                    icon = { Icon(Icons.Outlined.Palette, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dynamic_color_hint),
                    checked = dynamicColor,
                    onCheckedChange = { setBool(VaultPreferencesKeys.dynamicColor, it) },
                )
            }

            SettingsSectionTitle(stringResource(R.string.settings_section_vault))

            SettingsCard(Modifier.padding(horizontal = 20.dp)) {
                ReaderSettingRow(
                    icon = { Icon(Icons.Outlined.Folder, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_vault_current_label),
                    value = vaultLabel,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                ReaderSettingRow(
                    icon = { Icon(Icons.Outlined.Folder, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_change_vault),
                    value = "",
                    onClick = onChangeVault,
                )
            }

            if (isSignedIn) {
                SettingsSectionTitle(stringResource(R.string.settings_section_quota))
                val displayAccount = accountInfo ?: cachedAccount
                when {
                    accountDevStub -> {
                        Text(
                            text = stringResource(R.string.settings_quota_dev_stub),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = TextStyle(fontSize = 14.sp),
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                    accountLoading && displayAccount == null -> {
                        Text(
                            text = stringResource(R.string.settings_quota_loading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = TextStyle(fontSize = 14.sp),
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                    displayAccount != null -> {
                        val info = displayAccount
                        val planLabel =
                            if (info.hasSubscription()) {
                                PlanDisplayLabels.PRO
                            } else {
                                PlanDisplayLabels.FREE
                            }
                        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                            if (accountInfo == null && cachedAccount != null) {
                                Text(
                                    text = stringResource(R.string.settings_quota_cached_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = TextStyle(fontSize = 12.sp),
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                            TokenQuotaCard(
                                planLabel = planLabel,
                                tokensUsed = info.tokensUsed,
                                tokensQuota = info.tokensQuota,
                                hasSubscription = info.hasSubscription(),
                                subscriptionExpiresAtEpochMs = info.subscriptionExpiresAtEpochMs,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.settings_quota_daily_reset),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = TextStyle(fontSize = 12.sp),
                            )
                            Spacer(Modifier.height(12.dp))
                            PlanCompareCards(
                                freeCloudQuota =
                                    info.freeTokensQuota.takeIf { it > 0L } ?: 10_000L,
                                proCloudQuota =
                                    info.proTokensQuota.takeIf { it > 0L } ?: 50_000L,
                                proPriceRub = info.subscriptionPriceRub,
                                activePro = info.hasSubscription(),
                            )
                            Spacer(Modifier.height(12.dp))
                            if (info.hasSubscription()) {
                                Text(
                                    text =
                                        if (info.isLifetimeSubscription()) {
                                            stringResource(R.string.settings_subscription_active_forever_hint)
                                        } else {
                                            stringResource(R.string.settings_subscription_active_hint)
                                        },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = TextStyle(fontSize = 12.sp),
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            if (!info.isLifetimeSubscription()) {
                                Button(
                                    onClick = { startProCheckout() },
                                    enabled = !billingLoading,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (billingLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(Modifier.width(10.dp))
                                    } else {
                                        Icon(
                                            Icons.Outlined.WorkspacePremium,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(
                                        text =
                                            if (info.hasSubscription()) {
                                                stringResource(
                                                    R.string.settings_subscription_renew_button,
                                                    info.subscriptionPriceRub,
                                                )
                                            } else {
                                                stringResource(
                                                    R.string.settings_subscription_buy_button,
                                                    info.subscriptionPriceRub,
                                                )
                                            },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showPaymentsDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    Icons.Outlined.ReceiptLong,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.settings_subscription_payments_button))
                            }
                            if (accountLoadFailed && accountInfo == null) {
                                val errorText =
                                    if (cachedAccount != null) {
                                        stringResource(R.string.settings_quota_refresh_failed)
                                    } else {
                                        quotaErrorMessage(accountLoadError)
                                    }
                                Text(
                                    text = errorText,
                                    color = MaterialTheme.colorScheme.error,
                                    style = TextStyle(fontSize = 12.sp),
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                TextButton(onClick = { accountRefreshKey++ }) {
                                    Text(stringResource(R.string.settings_quota_retry))
                                }
                            }
                        }
                    }
                    accountLoadFailed -> {
                        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                            Text(
                                text = quotaErrorMessage(accountLoadError),
                                color = MaterialTheme.colorScheme.error,
                                style = TextStyle(fontSize = 14.sp),
                            )
                            TextButton(onClick = { accountRefreshKey++ }) {
                                Text(stringResource(R.string.settings_quota_retry))
                            }
                        }
                    }
                }
            }

            SettingsSectionTitle(stringResource(R.string.settings_section_account))

            if (isSignedIn) {
                val sessionsApiBase =
                    ApiBaseUrls.normalize(
                        apiBase?.trim()?.takeIf { it.isNotBlank() }
                            ?: context.getString(R.string.default_api_base).trim(),
                    )
                AuthSessionsSection(
                    apiBase = sessionsApiBase,
                    authToken = authToken!!.trim(),
                )
                Spacer(Modifier.height(8.dp))
            }

            SettingsCard(Modifier.padding(horizontal = 20.dp)) {
                ReaderSettingRow(
                    icon = { Icon(Icons.Outlined.Person, null, Modifier.size(20.dp)) },
                    title = accountLabel,
                    value = "",
                )
                if (isSignedIn) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                    ReaderSettingRow(
                        icon = { Icon(Icons.Outlined.Logout, null, Modifier.size(20.dp)) },
                        title = stringResource(R.string.settings_logout),
                        value = "",
                        onClick = {
                            scope.launch {
                                val token = authToken?.trim().orEmpty()
                                val base =
                                    ApiBaseUrls.normalize(
                                        apiBase?.trim()?.takeIf { it.isNotBlank() }
                                            ?: context.getString(R.string.default_api_base).trim(),
                                    )
                                val sessionId = JwtPayload.sessionId(token)
                                if (token.isNotEmpty() && sessionId != null) {
                                    withContext(Dispatchers.IO) {
                                        runCatching {
                                            val api = EssentialApi(base)
                                            try {
                                                api.revokeAuthSession(token, sessionId)
                                            } finally {
                                                api.close()
                                            }
                                        }
                                    }
                                }
                                prefs.setAuthToken(null)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_toast_logout),
                                    Toast.LENGTH_SHORT,
                                ).show()
                                onLogout()
                            }
                        },
                    )
                }
            }

            SettingsSectionTitle(stringResource(R.string.settings_privacy_section))

            SettingsCard(Modifier.padding(horizontal = 20.dp)) {
                ReaderSettingRow(
                    icon = { Icon(Icons.Outlined.Policy, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_privacy_policy_link),
                    value = "",
                    onClick = {
                        context.openUrl(context.getString(R.string.privacy_policy_url))
                    },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                ReaderSettingRow(
                    icon = { Icon(Icons.Outlined.Gavel, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_terms_link),
                    value = "",
                    onClick = {
                        context.openUrl(context.getString(R.string.terms_url))
                    },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                ReaderSettingRow(
                    icon = { Icon(Icons.Outlined.Email, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_support_title),
                    value = stringResource(R.string.support_email),
                    onClick = { context.openSupportEmail() },
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }

    if (showWebResearchDialog) {
        AlertDialog(
            onDismissRequest = { showWebResearchDialog = false },
            title = { Text(stringResource(R.string.settings_local_web_research_endpoint)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_local_web_research_endpoint_dialog_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = webResearchDraft,
                        onValueChange = { webResearchDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(LocalWebResearch.DEFAULT_ENDPOINT) },
                        singleLine = false,
                        minLines = 2,
                        maxLines = 4,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            prefs.setLocalWebResearchEndpoint(webResearchDraft)
                            showWebResearchDialog = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWebResearchDialog = false }) {
                    Text(stringResource(R.string.editor_delete_cancel))
                }
            },
        )
    }

    if (showPaymentsDialog) {
        val paymentsScroll = rememberScrollState()
        val paymentDateFormat =
            remember {
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
            }
        AlertDialog(
            onDismissRequest = { showPaymentsDialog = false },
            title = { Text(stringResource(R.string.settings_subscription_payments_title)) },
            text = {
                when {
                    paymentsLoading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = stringResource(R.string.settings_quota_loading),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = TextStyle(fontSize = 12.sp),
                            )
                        }
                    }
                    subscriptionPayments.isNullOrEmpty() -> {
                        Text(
                            text = stringResource(R.string.settings_subscription_payments_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = TextStyle(fontSize = 12.sp),
                        )
                    }
                    else -> {
                        Column(
                            modifier =
                                Modifier
                                    .heightIn(max = 360.dp)
                                    .verticalScroll(paymentsScroll),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            subscriptionPayments.orEmpty().forEach { payment ->
                                val whenMs =
                                    payment.confirmedAtEpochMs?.takeIf { it > 0L }
                                        ?: payment.createdAtEpochMs
                                val dateLabel = paymentDateFormat.format(Date(whenMs))
                                Text(
                                    text =
                                        stringResource(
                                            R.string.settings_payment_row,
                                            dateLabel,
                                            payment.amountRub.toInt(),
                                            paymentStatusLabel(payment.status),
                                        ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = TextStyle(fontSize = 12.sp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPaymentsDialog = false }) {
                    Text(stringResource(R.string.cd_close))
                }
            },
        )
    }

    if (showLanguageDialog && hostActivity != null) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LanguageDialogOption(
                        label = stringResource(R.string.lang_system),
                        selected = appLocaleTag == AppLocales.SYSTEM,
                        onClick = {
                            applyAppLanguage(hostActivity, prefs, scope, AppLocales.SYSTEM) {
                                showLanguageDialog = false
                            }
                        },
                    )
                    LanguageDialogOption(
                        label = stringResource(R.string.lang_russian),
                        selected = appLocaleTag == AppLocales.RU,
                        onClick = {
                            applyAppLanguage(hostActivity, prefs, scope, AppLocales.RU) {
                                showLanguageDialog = false
                            }
                        },
                    )
                    LanguageDialogOption(
                        label = stringResource(R.string.lang_english),
                        selected = appLocaleTag == AppLocales.EN,
                        onClick = {
                            applyAppLanguage(hostActivity, prefs, scope, AppLocales.EN) {
                                showLanguageDialog = false
                            }
                        },
                    )
                }
            },
            confirmButton = {},
        )
    }
}

private fun applyAppLanguage(
    activity: AppCompatActivity,
    prefs: VaultPreferencesRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    tag: String,
    onDone: () -> Unit,
) {
    scope.launch {
        prefs.setAppLocaleTag(tag)
        val saved = prefs.appLocaleTag.first()
        AppLocales.apply(saved)
        withContext(Dispatchers.Main.immediate) {
            onDone()
            activity.recreate()
        }
    }
}

@Composable
private fun LanguageDialogOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = if (selected) "✓ $label" else label,
        style = TextStyle(
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        ),
        color =
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(vertical = 10.dp),
    )
}

@Composable
internal fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun FontPickCard(
    option: ReaderFontOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .size(width = 88.dp, height = 96.dp)
            .border(1.5.dp, borderColor, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Aa",
                style = TextStyle(
                    fontFamily = option.family,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = option.label,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun ReaderSettingRow(
    icon: @Composable () -> Unit,
    title: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(rowModifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            icon()
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun ToggleSettingRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            icon()
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = TextStyle(fontSize = 13.sp),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}


