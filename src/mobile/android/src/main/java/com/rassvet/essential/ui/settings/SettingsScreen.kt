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
import com.rassvet.essential.ui.theme.LazureIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.appcompat.app.AppCompatActivity
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
import com.rassvet.essential.locale.AppLocales
import com.rassvet.essential.data.vault.VaultDocuments
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: VaultPreferencesRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fontId by prefs.readerFontId.collectAsState(initial = "georgia")
    val fontSize by prefs.readerFontSizeSp.collectAsState(initial = 16f)
    val fontWeight by prefs.readerFontWeight.collectAsState(initial = 400)
    val streamReplies by prefs.streamReplies.collectAsState(initial = true)
    val thinkingEnabled by prefs.thinkingEnabled.collectAsState(initial = false)
    val autoScroll by prefs.autoScrollTranscript.collectAsState(initial = true)
    val speakerHint by prefs.speakerCountHint.collectAsState(initial = 0)
    val chatHaptics by prefs.chatHapticsEnabled.collectAsState(initial = true)
    val llmComputeBackend by prefs.llmComputeBackend.collectAsState(initial = LlmComputeBackend.CPU)
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
    val reader = ReaderTypographySettings(fontId, fontSize, fontWeight)
    fun persist(font: String = fontId, size: Float = fontSize, weight: Int = fontWeight) {
        scope.launch { prefs.setReaderTypography(font, size, weight) }
    }

    fun setBool(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        scope.launch { prefs.setBooleanPref(key, value) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            LazureIcons.ArrowBack,
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
                    icon = { Icon(LazureIcons.FormatSize, null, Modifier.size(20.dp)) },
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
                    icon = { Icon(LazureIcons.TextFormat, null, Modifier.size(20.dp)) },
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

            SettingsSectionTitle(stringResource(R.string.settings_section_recordings))

            SettingsCard(Modifier.padding(horizontal = 20.dp)) {
                ToggleSettingRow(
                    icon = { Icon(LazureIcons.GraphicEq, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_autoscroll),
                    subtitle = stringResource(R.string.settings_autoscroll_hint),
                    checked = autoScroll,
                    onCheckedChange = { setBool(VaultPreferencesKeys.autoScrollTranscript, it) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ReaderSettingRow(
                    icon = { Icon(LazureIcons.Mic, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_speaker_count),
                    value = if (speakerHint == 0) stringResource(R.string.settings_speaker_count_auto) else speakerHint.toString(),
                    onClick = { scope.launch { prefs.setSpeakerCountHint(if (speakerHint >= 6) 0 else if (speakerHint == 0) 2 else speakerHint + 1) } },
                )
            }

            SettingsSectionTitle(stringResource(R.string.settings_section_chat))

            SettingsCard(Modifier.padding(horizontal = 20.dp)) {
                ToggleSettingRow(
                    icon = { Icon(LazureIcons.ChatBubbleOutline, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_stream_replies),
                    subtitle = stringResource(R.string.settings_stream_replies_hint),
                    checked = streamReplies,
                    onCheckedChange = { setBool(VaultPreferencesKeys.streamReplies, it) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                ToggleSettingRow(
                    icon = { Icon(LazureIcons.AutoAwesome, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_thinking),
                    subtitle = stringResource(R.string.settings_thinking_hint),
                    checked = thinkingEnabled,
                    onCheckedChange = { setBool(VaultPreferencesKeys.thinkingEnabled, it) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                ToggleSettingRow(
                    icon = { Icon(LazureIcons.Vibration, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_chat_haptics),
                    subtitle = stringResource(R.string.settings_chat_haptics_hint),
                    checked = chatHaptics,
                    onCheckedChange = { setBool(VaultPreferencesKeys.chatHapticsEnabled, it) },
                )

            }

            SettingsSectionTitle(stringResource(R.string.settings_local_llm_section))

            SettingsCard(Modifier.padding(horizontal = 20.dp)) {
                ReaderSettingRow(
                    icon = { Icon(LazureIcons.Memory, null, Modifier.size(20.dp)) },
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
                    icon = { Icon(LazureIcons.Language, null, Modifier.size(20.dp)) },
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
                    icon = { Icon(LazureIcons.DarkMode, null, Modifier.size(20.dp)) },
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
                    icon = { Icon(LazureIcons.Palette, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dynamic_color_hint),
                    checked = dynamicColor,
                    onCheckedChange = { setBool(VaultPreferencesKeys.dynamicColor, it) },
                )
            }

            SettingsSectionTitle(stringResource(R.string.nav_vault))
            SettingsCard(Modifier.padding(horizontal = 20.dp)) {
                ReaderSettingRow(
                    icon = { Icon(LazureIcons.Folder, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.recordings_title),
                    value = stringResource(R.string.recordings_saved_here),
                )
            }

            com.rassvet.essential.ui.security.PinSettingsCard()

            Spacer(Modifier.height(12.dp))
        }
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
        style = MaterialTheme.typography.titleLarge,
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
            if (value.isNotBlank()) Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
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


