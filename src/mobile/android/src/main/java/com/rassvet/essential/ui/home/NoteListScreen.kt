package com.rassvet.essential.ui.home

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.rassvet.essential.R
import com.rassvet.essential.data.index.IndexRepository
import com.rassvet.essential.data.local.AppDatabase
import com.rassvet.essential.data.local.NoteIndexEntity
import com.rassvet.essential.data.local.VaultPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    db: AppDatabase,
    prefs: VaultPreferencesRepository,
    onOpenNote: (Uri) -> Unit,
    onChat: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val index = remember { IndexRepository(db) }
    var notes by remember { mutableStateOf<List<NoteIndexEntity>>(emptyList()) }
    var vaultUri by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(prefs) {
        prefs.vaultTreeUri.collect { vaultUri = it }
    }

    LaunchedEffect(vaultUri) {
        val uri = vaultUri ?: return@LaunchedEffect
        notes =
            withContext(Dispatchers.IO) {
                index.rebuild(context, uri)
                index.allNotes()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notes_title)) },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val uri = vaultUri ?: return@launch
                                notes =
                                    withContext(Dispatchers.IO) {
                                        index.rebuild(context, uri)
                                        index.allNotes()
                                    }
                            }
                        },
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.notes_reindex),
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.notes_settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onChat,
                shape = MaterialTheme.shapes.large,
            ) {
                Text(stringResource(R.string.notes_chat))
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(notes, key = { it.uri }) { note ->
                ListItem(
                    headlineContent = { Text(note.title) },
                    supportingContent = {
                        Text(
                            note.preview,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            onOpenNote(Uri.parse(note.uri))
                        },
                )
            }
        }
    }
}


