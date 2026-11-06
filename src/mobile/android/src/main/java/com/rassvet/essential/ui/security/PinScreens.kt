package com.rassvet.essential.ui.security

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rassvet.essential.R
import com.rassvet.essential.data.security.PinRepository
import kotlinx.coroutines.launch

@Composable
fun PinGuard(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val repo = remember { PinRepository(context) }
    val enabled by repo.enabled.collectAsState(initial = null)
    var unlocked by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(enabled) { if (enabled == false) { unlocked = true; entered = true } }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) unlocked = false }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(enabled) {
        val window = (context as? Activity)?.window
        if (enabled == true) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    Box(Modifier.fillMaxSize()) {
        if (enabled == false || entered) content()
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        if (enabled == true && !unlocked) {
            Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PinForm(repo, setup = false, disable = false, onSuccess = { unlocked = true; entered = true })
                }
            }
        }
    }
}

@Composable
fun PinSettingsCard() {
    val context = LocalContext.current
    val repo = remember { PinRepository(context) }
    val enabled by repo.enabled.collectAsState(initial = false)
    var show by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.pin_section), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pin_setting), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(if (enabled) R.string.pin_enabled_hint else R.string.pin_disabled_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = { show = true })
            }
        }
    }
    if (show) Dialog(onDismissRequest = { show = false }) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
            PinForm(repo, setup = !enabled, disable = enabled, onSuccess = { show = false }, onCancel = { show = false })
        }
    }
}

@Composable
private fun PinForm(repo: PinRepository, setup: Boolean, disable: Boolean, onSuccess: () -> Unit, onCancel: (() -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    var length by remember { mutableIntStateOf(4) }
    var pin by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableIntStateOf(0) }
    Column(Modifier.padding(24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(if (disable) R.string.pin_disable_title else if (setup) R.string.pin_setup_title else R.string.pin_unlock_title), style = MaterialTheme.typography.headlineSmall)
        if (setup && first == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(4, 6).forEach { count -> FilterChip(selected = length == count, onClick = { length = count; pin = ""; error = 0 }, label = { Text(stringResource(R.string.pin_digits, count)) }) }
        }
        Text(stringResource(if (first != null) R.string.pin_confirm_hint else R.string.pin_input_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= (if (setup) length else 6) && it.all { c -> c in '0'..'9' }) { pin = it; error = 0 } },
            label = { Text(stringResource(R.string.pin_setting)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            shape = MaterialTheme.shapes.large,
            enabled = !busy,
            isError = error != 0,
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != 0) Text(stringResource(error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button(
            enabled = !busy && PinRepository.isValidPin(pin) && (!setup || pin.length == length),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (setup && first == null) { first = pin; pin = "" }
                else if (setup && first != pin) { error = R.string.pin_mismatch; pin = "" }
                else {
                    busy = true
                    scope.launch {
                        try {
                            if (setup) { repo.enable(pin); pin = ""; first = null; onSuccess() }
                            else when (repo.verify(pin, disable)) {
                                PinRepository.Result.Success -> { pin = ""; onSuccess() }
                                PinRepository.Result.Incorrect -> { pin = ""; error = R.string.pin_incorrect }
                                PinRepository.Result.Locked -> { pin = ""; error = R.string.pin_locked }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { error = R.string.pin_error }
                        finally { busy = false }
                    }
                }
            },
        ) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(if (first == null && setup) R.string.pin_continue else R.string.pin_submit)) }
        if (onCancel != null) TextButton(onClick = onCancel, enabled = !busy) { Text(stringResource(R.string.cancel)) }
    }
}
