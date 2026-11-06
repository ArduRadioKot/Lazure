package com.rassvet.essential.ui.models

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rassvet.essential.R
import com.rassvet.essential.data.local.VaultPreferencesRepository
import com.rassvet.essential.data.llm.ChatModelSelection
import com.rassvet.essential.data.llm.GgufRepository
import com.rassvet.essential.data.llm.LocalModelCatalog
import com.rassvet.essential.data.llm.LocalModelDownloadController
import com.rassvet.essential.data.llm.LocalModelDownloadState
import com.rassvet.essential.ui.home.ChatModelPickerSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIModelsScreen(prefs: VaultPreferencesRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(context) { GgufRepository(context.applicationContext) }
    val controller = remember(context) { LocalModelDownloadController.get(context.applicationContext) }
    val downloadState by controller.state.collectAsState()
    val activeModel by prefs.activeGgufName.collectAsState(initial = null)
    val allowCellular by prefs.ggufAllowCellular.collectAsState(initial = false)
    var installed by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingDownload by remember { mutableStateOf<ChatModelSelection.Option?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatModelSelection.Option?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val option = pendingDownload
        pendingDownload = null
        if (option != null) beginDownload(context, controller, option, allowCellular) { result, preset ->
            if (result.isSuccess) scope.launch { finishInstall(context, prefs, repo, preset, { installed = it }) }
            else result.exceptionOrNull()?.let { errorText = it.message }
        }
    }

    LaunchedEffect(repo) { installed = withContext(Dispatchers.IO) { repo.listVerifiedLocalModelFileNames() } }

    val options = remember(installed, activeModel) {
        ChatModelSelection.buildOptions(
            context = context,
            installedLocalModels = installed,
            homeChatMode = "local",
            activeLocalModel = activeModel,
            cloudProvider = "local",
        ).filter { it.kind == ChatModelSelection.Kind.LocalModel }
    }
    val selectedId = activeModel?.let { "local:$it" } ?: ""
    val ram = remember { ChatModelSelection.deviceTotalRamMb(context) }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.models_title)) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back)) } },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.models_offline_notice), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.models_catalog_hint),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!errorText.isNullOrBlank()) {
                    Text(errorText.orEmpty(), modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        ChatModelPickerSheet(
            visible = true,
            options = options,
            selectedId = selectedId,
            deviceRamMb = ram,
            modelDownload = downloadState,
            onDismiss = onBack,
            onSelect = { option ->
                scope.launch { ChatModelSelection.applySelection(prefs, option) }
                onBack()
            },
            onDownload = { option ->
                pendingDownload = option
                if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else {
                    pendingDownload = null
                    beginDownload(context, controller, option, allowCellular) { result, preset ->
                        if (result.isSuccess) scope.launch { finishInstall(context, prefs, repo, preset, { installed = it }) }
                        else errorText = result.exceptionOrNull()?.message
                    }
                }
            },
            onPauseDownload = { controller.pause() },
            onResumeDownload = { option ->
                val preset = ChatModelSelection.presetForOption(option) ?: return@ChatModelPickerSheet
                controller.resume(preset, allowCellular) { result, finished ->
                    if (result.isSuccess) scope.launch { finishInstall(context, prefs, repo, finished, { installed = it }) }
                    else if (result.exceptionOrNull() !is CancellationException) errorText = result.exceptionOrNull()?.message
                }
            },
            onCancelDownload = { controller.cancel() },
            onDelete = { option ->
                deleteTarget = option
            },
        )
        deleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text(stringResource(R.string.chat_model_delete_confirm_title)) },
                text = { Text(stringResource(R.string.chat_model_delete_confirm_body, target.title)) },
                confirmButton = {
                    TextButton(onClick = {
                        val filename = target.localFileName
                        deleteTarget = null
                        if (filename != null) scope.launch {
                            withContext(Dispatchers.IO) { repo.deleteModel(filename) }
                            installed = withContext(Dispatchers.IO) { repo.listVerifiedLocalModelFileNames() }
                            if (activeModel == filename) prefs.setActiveGguf(null)
                        }
                    }) { Text(stringResource(R.string.chat_model_delete)) }
                },
                dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}

private fun beginDownload(
    context: Context,
    controller: LocalModelDownloadController,
    option: ChatModelSelection.Option,
    allowCellular: Boolean,
    onFinished: (Result<Unit>, LocalModelCatalog.Preset) -> Unit,
) {
    val preset = ChatModelSelection.presetForOption(option) ?: return
    controller.start(preset, allowCellular) { result, downloaded ->
        if (result.isFailure && result.exceptionOrNull() !is CancellationException) {
            val key = when (controller.state.value.errorMessage) {
                "wifi_required" -> R.string.chat_model_download_wifi
                "storage_required" -> R.string.chat_model_download_storage
                else -> R.string.chat_model_download_failed
            }
            Toast.makeText(context, context.getString(key), Toast.LENGTH_LONG).show()
        }
        onFinished(result, downloaded)
    }
}

private suspend fun finishInstall(
    context: Context,
    prefs: VaultPreferencesRepository,
    repo: GgufRepository,
    preset: LocalModelCatalog.Preset,
    setInstalled: (List<String>) -> Unit,
) {
    setInstalled(withContext(Dispatchers.IO) { repo.listVerifiedLocalModelFileNames() })
    val option = ChatModelSelection.Option(
        id = "local:${preset.fileName}",
        pillLabel = ChatModelSelection.shortLocalModelLabel(preset.fileName),
        title = preset.label,
        subtitle = preset.info,
        description = preset.description,
        sizeLabel = preset.sizeLabel,
        kind = ChatModelSelection.Kind.LocalModel,
        localFileName = preset.fileName,
    )
    ChatModelSelection.applySelection(prefs, option)
    Toast.makeText(context, R.string.chat_model_download_done, Toast.LENGTH_SHORT).show()
}
