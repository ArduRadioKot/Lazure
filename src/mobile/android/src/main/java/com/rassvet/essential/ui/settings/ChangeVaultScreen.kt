package com.rassvet.essential.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rassvet.essential.R
import com.rassvet.essential.data.local.VaultPreferencesRepository
import com.rassvet.essential.data.vault.VaultDocuments
import com.rassvet.essential.ui.components.InternalVaultPickerList
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeVaultScreen(
    prefs: VaultPreferencesRepository,
    onBack: () -> Unit,
    onCreateNewVault: () -> Unit,
    onVaultChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vaultStored by prefs.vaultTreeUri.collectAsState(initial = null)

    val vaultSlugs =
        remember {
            val root = File(context.filesDir, "vaults")
            if (!root.isDirectory) {
                emptyList()
            } else {
                root.listFiles()
                    ?.filter { it.isDirectory }
                    ?.map { it.name }
                    ?.sorted()
                    .orEmpty()
            }
        }

    val currentSlug =
        vaultStored
            ?.trim()
            ?.takeIf { it.startsWith(VaultDocuments.INTERNAL_PREFIX) }
            ?.removePrefix(VaultDocuments.INTERNAL_PREFIX)

    fun selectVault(slug: String) {
        scope.launch {
            prefs.setVaultTreeUri("${VaultDocuments.INTERNAL_PREFIX}$slug")
            Toast.makeText(
                context,
                context.getString(R.string.settings_toast_vault_changed),
                Toast.LENGTH_SHORT,
            ).show()
            onVaultChanged()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_change_vault_title),
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
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_change_vault_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
            )
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onCreateNewVault,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_create_new_vault))
            }

            Spacer(Modifier.height(20.dp))

            if (vaultSlugs.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_no_vaults_yet),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = TextStyle(fontSize = 14.sp),
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                InternalVaultPickerList(
                    vaultSlugs = vaultSlugs,
                    selectedSlug = currentSlug,
                    currentSlug = currentSlug,
                    onSelect = { selectVault(it) },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}


