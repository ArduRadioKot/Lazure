package com.rassvet.essential.ui.security

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.*
import com.rassvet.essential.ui.theme.LazureIcons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
                    PinForm(repo, setup = false, disable = false, onSuccess = { if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) { unlocked = true; entered = true } })
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
        Text(stringResource(R.string.pin_section), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(LazureIcons.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pin_setting), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(if (enabled) R.string.pin_enabled_hint else R.string.pin_disabled_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = { show = true }, modifier = Modifier.testTag("pin_toggle"))
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
    val savedLength by repo.digitCount.collectAsState(initial = 0)
    val effectiveLength = if (setup) length else savedLength?.takeIf { it == 4 || it == 6 } ?: length
    if (!setup && savedLength == 0) {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    fun submit() {
        if (busy || pin.length != effectiveLength) return
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
                catch (_: Exception) { pin = ""; error = R.string.pin_error }
                finally { busy = false }
            }
        }
    }
    LaunchedEffect(pin, effectiveLength, savedLength) {
        if (!setup && savedLength != 0 && pin.length == effectiveLength) submit()
    }
    Box(Modifier.fillMaxWidth().safeDrawingPadding().testTag("pin_form"), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 360.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(LazureIcons.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(if (disable) R.string.pin_disable_title else if (setup) R.string.pin_setup_title else R.string.pin_unlock_title), style = MaterialTheme.typography.headlineSmall)
            if ((setup && first == null) || (!setup && savedLength == null)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(4, 6).forEach { count -> FilterChip(selected = length == count, enabled = !busy, onClick = { length = count; pin = ""; error = 0 }, label = { Text(stringResource(R.string.pin_digits, count)) }) }
            }
            Text(
                text = when {
                    first != null -> stringResource(R.string.pin_confirm_hint)
                    !setup && savedLength == null -> stringResource(R.string.pin_legacy_length_hint)
                    else -> stringResource(R.string.pin_exact_length_hint, effectiveLength)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val enteredLabel = stringResource(R.string.pin_entered_count, pin.length)
            Row(
                Modifier.padding(vertical = 8.dp).semantics { contentDescription = enteredLabel },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(effectiveLength) { index ->
                    Box(Modifier.size(14.dp).background(
                        if (index < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        CircleShape,
                    ))
                }
            }
            PinKeypad(
                enabled = !busy && (setup || savedLength != 0),
                canAdd = pin.length < effectiveLength,
                canDelete = pin.isNotEmpty(),
                onDigit = { if (!busy && pin.length < effectiveLength) { pin += it; error = 0 } },
                onDelete = { if (!busy) { pin = pin.dropLast(1); error = 0 } },
            )
            Box(Modifier.fillMaxWidth().heightIn(min = 40.dp), contentAlignment = Alignment.Center) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else if (error != 0) Text(stringResource(error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (setup) Button(
                enabled = !busy && PinRepository.isValidPin(pin) && (!setup || pin.length == length),
                modifier = Modifier.fillMaxWidth(),
                onClick = { submit() },
            ) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(if (first == null && setup) R.string.pin_continue else R.string.pin_submit)) }
            if (onCancel != null) TextButton(onClick = onCancel, enabled = !busy) { Text(stringResource(R.string.cancel)) }
        }
    }
}


@Composable
private fun PinKeypad(
    enabled: Boolean,
    canAdd: Boolean,
    canDelete: Boolean,
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val keySize = minOf(80.dp, (maxWidth - 24.dp) / 3)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "delete")).forEach { keys ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                    keys.forEach { key ->
                        if (key.isEmpty()) Spacer(Modifier.size(keySize))
                        else FilledTonalButton(
                            onClick = { if (key == "delete") onDelete() else onDigit(key) },
                            enabled = enabled && if (key == "delete") canDelete else canAdd,
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(keySize),
                        ) {
                            if (key == "delete") Icon(LazureIcons.Backspace, contentDescription = stringResource(R.string.pin_delete_digit))
                            else Text(key, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
        }
    }
}
