package com.rassvet.essential.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rassvet.essential.R
import com.rassvet.essential.data.api.AuthSessionItem
import com.rassvet.essential.data.api.EssentialApi
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AuthSessionsSection(
    apiBase: String,
    authToken: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<AuthSessionItem>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var revokingId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(apiBase, authToken, refreshKey) {
        loading = true
        loadFailed = false
        val result =
            withContext(Dispatchers.IO) {
                runCatching {
                    val api = EssentialApi(apiBase)
                    try {
                        api.listAuthSessions(authToken)
                    } finally {
                        api.close()
                    }
                }
            }
        loading = false
        result.fold(
            onSuccess = { sessions = it },
            onFailure = {
                loadFailed = true
                sessions = emptyList()
            },
        )
    }

    SettingsSectionTitle(stringResource(R.string.settings_sessions_title))

    SettingsCard(modifier.padding(horizontal = 20.dp)) {
        Text(
            text = stringResource(R.string.settings_sessions_subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = TextStyle(fontSize = 13.sp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        when {
            loading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            loadFailed -> {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_sessions_load_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = TextStyle(fontSize = 13.sp),
                    )
                    TextButton(onClick = { refreshKey++ }) {
                        Text(stringResource(R.string.settings_quota_retry))
                    }
                }
            }
            sessions.isNullOrEmpty() -> {
                Text(
                    text = stringResource(R.string.settings_sessions_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = TextStyle(fontSize = 13.sp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            else -> {
                sessions.orEmpty().forEachIndexed { index, session ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        )
                    }
                    AuthSessionRow(
                        session = session,
                        revoking = revokingId == session.id,
                        onRevoke = {
                            if (session.current || revokingId != null) return@AuthSessionRow
                            revokingId = session.id
                            scope.launch {
                                val ok =
                                    withContext(Dispatchers.IO) {
                                        runCatching {
                                            val api = EssentialApi(apiBase)
                                            try {
                                                api.revokeAuthSession(authToken, session.id)
                                                true
                                            } finally {
                                                api.close()
                                            }
                                        }.getOrDefault(false)
                                    }
                                revokingId = null
                                if (ok) {
                                    refreshKey++
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.settings_sessions_revoke_failed),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthSessionRow(
    session: AuthSessionItem,
    revoking: Boolean,
    onRevoke: () -> Unit,
) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val lastSeen =
        remember(session.lastSeenAtEpochMs) {
            if (session.lastSeenAtEpochMs > 0L) {
                dateFormat.format(Date(session.lastSeenAtEpochMs))
            } else {
                ""
            }
        }
    val platformLine =
        listOf(session.devicePlatform, session.appVersion.takeIf { it.isNotBlank() })
            .filterNotNull()
            .joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector =
                if (session.devicePlatform.contains("android", ignoreCase = true)) {
                    Icons.Outlined.PhoneAndroid
                } else {
                    Icons.Outlined.Computer
                },
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.deviceLabel.ifBlank { stringResource(R.string.settings_sessions_unknown_device) },
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                )
                if (session.current) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_sessions_current),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            if (platformLine.isNotBlank()) {
                Text(
                    text = platformLine,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = TextStyle(fontSize = 12.sp),
                )
            }
            if (lastSeen.isNotBlank()) {
                Text(
                    text = stringResource(R.string.settings_sessions_last_seen, lastSeen),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = TextStyle(fontSize = 12.sp),
                )
            }
            if (session.ipAddress.isNotBlank()) {
                Text(
                    text = session.ipAddress,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    style = TextStyle(fontSize = 11.sp),
                )
            }
        }
        if (!session.current) {
            TextButton(onClick = onRevoke, enabled = !revoking) {
                if (revoking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.settings_sessions_revoke))
                }
            }
        }
    }
}


