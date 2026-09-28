package com.geno1024.ai.gits.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geno1024.ai.gits.R
import com.geno1024.ai.gits.data.DocumentTree
import com.geno1024.ai.gits.data.RecentRepository
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpen: (File) -> Unit,
    onManageKeys: () -> Unit,
    onCheckUpdate: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var creating by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf(false) }

    /**
     * The folder a new repository would be made inside, when one was picked.
     *
     * Held here rather than passed down so that picking a folder is the same gesture
     * whether it ends in opening something or making something.
     */
    var parent by remember { mutableStateOf<File?>(null) }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Without this the grant is gone the next time the app starts, and the entry
        // in the recents list would point at a folder this app can no longer read.
        DocumentTree.takePersistablePermission(context, uri)
        DocumentTree.requireDirectoryOf(uri)
            .onSuccess { picked ->
                parent = picked
                if (opening) {
                    opening = false
                    viewModel.open(picked.path, onOpen)
                } else {
                    creating = true
                }
            }
            .onFailure { viewModel.report(it.message ?: "That folder cannot be used.") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onCheckUpdate) {
                        Icon(
                            Icons.Default.SystemUpdateAlt,
                            contentDescription = stringResource(R.string.action_check_update),
                        )
                    }
                    IconButton(onClick = onManageKeys) {
                        Icon(
                            Icons.Default.VpnKey,
                            contentDescription = stringResource(R.string.keys_title),
                        )
                    }
                    IconButton(onClick = { pickFolder.launch(DocumentTree.initialUri()) }) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = stringResource(R.string.action_open_repository),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.action_create_repository),
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.busy -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.repositories.isEmpty() -> EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    detail = stringResource(R.string.home_empty_detail),
                    onOpen = { pickFolder.launch(DocumentTree.initialUri()) },
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.repositories, key = { it.path }) { repository ->
                        RepositoryRow(
                            repository = repository,
                            onOpen = { viewModel.open(repository.path, onOpen) },
                            onForget = { viewModel.forget(repository) },
                        )
                    }
                }
            }

            state.error?.let { message ->
                ErrorBar(message = message, onDismiss = viewModel::clearError)
            }
        }
    }

    if (opening) {
        FolderDialog(
            title = stringResource(R.string.action_open_repository),
            confirmLabel = stringResource(R.string.action_open),
            suggestedFolder = viewModel.suggestedFolder,
            onDismiss = { opening = false },
            onConfirm = { folder ->
                opening = false
                viewModel.open(folder, onOpen)
            },
        )
    }

    if (creating) {
        CreateRepositoryDialog(
            suggestedFolder = parent?.let { "${it.name}/" } ?: viewModel.suggestedFolder,
            parent = parent,
            onPickParent = { pickFolder.launch(DocumentTree.initialUri()) },
            onDismiss = { creating = false },
            onCreate = { path, branch ->
                creating = false
                viewModel.create(path, branch, onOpen)
            },
        )
    }
}

@Composable
private fun RepositoryRow(
    repository: RecentRepository,
    onOpen: () -> Unit,
    onForget: () -> Unit,
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repository.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = repository.path,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onForget) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_forget),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    detail: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onOpen) { Text(stringResource(R.string.action_open_repository)) }
    }
}

@Composable
private fun ErrorBar(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
        title = { Text(stringResource(R.string.error_title)) },
        text = { Text(message) },
    )
}

/** Asks for a folder, with the app's own folder offered as the default. */
@Composable
private fun FolderDialog(
    title: String,
    confirmLabel: String,
    suggestedFolder: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var path by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FolderField(path = path, onPathChange = { path = it }, suggested = suggestedFolder)
                FolderHint()
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(path) },
                enabled = path.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun CreateRepositoryDialog(
    suggestedFolder: String,
    parent: File?,
    onPickParent: () -> Unit,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
) {
    var path by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("main") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_create_repository)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onPickParent) {
                        Icon(
                            Icons.Default.CreateNewFolder,
                            contentDescription = stringResource(R.string.action_pick_parent),
                        )
                    }
                    Text(
                        text = parent?.path ?: stringResource(R.string.create_no_parent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FolderField(path = path, onPathChange = { path = it }, suggested = suggestedFolder)
                FolderHint()
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text(stringResource(R.string.field_initial_branch)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(parent?.let { File(it, path.trim()) }?.path ?: path, branch) },
                enabled = path.isNotBlank(),
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun FolderField(path: String, onPathChange: (String) -> Unit, suggested: String) {
    OutlinedTextField(
        value = path,
        onValueChange = onPathChange,
        label = { Text(stringResource(R.string.field_folder)) },
        placeholder = { Text(suggested, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FolderHint() {
    Text(
        text = stringResource(R.string.home_folder_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
