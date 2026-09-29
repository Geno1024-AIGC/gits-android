package com.geno1024.ai.gits.ui.repo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.geno1024.ai.gits.R
import android.app.Application
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoScreen(path: String, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    // The path is the ViewModel's identity, so switching repositories builds a new
    // one instead of reloading state that belonged to the last.
    val application = LocalContext.current.applicationContext as Application
    val viewModel: RepoViewModel = viewModel(
        key = path,
        factory = viewModelFactory { initializer { RepoViewModel(application, path) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }

    // Git's answers are the useful part of most failures, so they are shown once and
    // then dismissed rather than left sitting under the next action.
    state.message?.let { message ->
        LaunchedEffect(message) {
            snackbars.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = state.branch ?: stringResource(R.string.repo_no_branch),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = viewModel::pull) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = stringResource(R.string.action_pull),
                        )
                    }
                    IconButton(onClick = viewModel::push) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = stringResource(R.string.action_push),
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                RepoTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = state.tab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        icon = {},
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> when (state.tab) {
                    RepoTab.CHANGES -> ChangesPane(state, viewModel)
                    RepoTab.HISTORY -> HistoryPane(state)
                    RepoTab.BRANCHES -> BranchesPane(state, viewModel)
                    RepoTab.REMOTES -> RemotesPane(state, viewModel)
                }
            }

            if (state.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                )
            }
        }
    }

    state.awaitingCredentials?.let { request ->
        CredentialsDialog(
            host = request.host,
            action = if (request.transfer == Transfer.PUSH) {
                stringResource(R.string.action_push)
            } else {
                stringResource(R.string.action_pull)
            },
            onDismiss = viewModel::cancelCredentials,
            onConfirm = viewModel::provideCredentials,
        )
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

@Composable
private fun ChangesPane(state: RepoUiState, viewModel: RepoViewModel) {
    var message by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = viewModel::stageSelected,
                enabled = state.selected.isNotEmpty(),
            ) { Text("Stage") }
            OutlinedButton(
                onClick = viewModel::unstageSelected,
                enabled = state.staged.any { it.path in state.selected },
            ) { Text("Unstage") }
            OutlinedButton(onClick = viewModel::selectAll, enabled = state.changes.isNotEmpty()) {
                Text("All")
            }
        }

        TextButton(onClick = { editing = !editing }) { Text("Who is committing?") }
        Text(
            text = state.identity?.let { "${it.name} <${it.email}>" } ?: stringResource(R.string.repo_no_identity),
            style = MaterialTheme.typography.bodySmall,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Switch(
                checked = state.signCommits,
                onCheckedChange = viewModel::setSignCommits,
                // Offering a switch that can only fail is worse than not offering it.
                enabled = state.signingAvailable,
            )
            Text("Sign commits", style = MaterialTheme.typography.bodyMedium)
        }
        if (!state.signingAvailable) {
            Text(
                text = stringResource(R.string.repo_no_signing_key),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.changes.isEmpty()) {
            EmptyNote("Nothing has changed.")
        } else {
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(state.changes, key = { it.path }) { change ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = change.path in state.selected,
                            onCheckedChange = { viewModel.toggleSelected(change.path) },
                        )
                        Text(
                            text = change.path,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                        Text(
                            text = change.kind.name.lowercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Commit message") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = {
                viewModel.commit(message)
                message = ""
            },
            enabled = state.canCommit && message.isNotBlank() && !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Commit") }

        if (state.staged.isNotEmpty()) {
            Text(
                text = "${state.staged.size} staged",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }

    if (editing) {
        IdentityDialog(
            current = state.identity,
            onDismiss = { editing = false },
            onSave = { name, email ->
                editing = false
                viewModel.setIdentity(name, email)
            },
        )
    }
}

@Composable
private fun IdentityDialog(
    current: com.geno1024.ai.gits.git.Identity?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(current?.name.orEmpty()) }
    var email by remember { mutableStateOf(current?.email.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Who is committing?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, email) },
                enabled = name.isNotBlank() && email.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun HistoryPane(state: RepoUiState) {
    if (state.history.isEmpty()) {
        EmptyNote("This repository has no commits yet.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(state.history, key = { it.id }) { entry ->
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = entry.shortId.take(7),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = entry.subject,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val signature = entry.signatureSummary()
                Text(
                    text = buildString {
                        append(entry.authorName)
                        signature?.let { append(" · ${it.text}") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    // A signature that does not verify is the one thing here worth
                    // interrupting the grey for.
                    color = when (signature?.trust) {
                        Trust.GOOD -> MaterialTheme.colorScheme.primary
                        Trust.BAD -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun BranchesPane(state: RepoUiState, viewModel: RepoViewModel) {
    var creating by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.branches, key = { it.name }) { branch ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(branch.name, style = MaterialTheme.typography.bodyLarge)
                        val tracking = branch.upstreamName
                            ?: "no upstream"
                        Text(
                            text = if (branch.isCurrent) {
                                "checked out · $tracking"
                            } else {
                                tracking
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!branch.isCurrent) {
                        TextButton(onClick = { viewModel.checkout(branch.name) }) { Text("Check out") }
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { creating = true },
            modifier = Modifier.padding(16.dp),
        ) { Text("New branch") }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New branch") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        creating = false
                        viewModel.createBranch(name)
                    },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = {
                TextButton(onClick = { creating = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun RemotesPane(state: RepoUiState, viewModel: RepoViewModel) {
    var adding by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (state.remotes.isEmpty()) {
            EmptyNote("This repository has no remotes.")
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.remotes, key = { it.name }) { remote ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(remote.name, style = MaterialTheme.typography.bodyLarge)
                            remote.uris.forEach { uri ->
                                Text(
                                    text = uri,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.removeRemote(remote.name) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_remove_remote),
                            )
                        }
                    }
                }
            }
        }
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::pull) {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
                Text("  Pull")
            }
            OutlinedButton(onClick = viewModel::push) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Text("  Push")
            }
            OutlinedButton(onClick = { adding = true }) { Text("Add remote") }
        }
    }

    if (adding) {
        var name by remember { mutableStateOf("origin") }
        var uri by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add remote") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = uri,
                        onValueChange = { uri = it },
                        label = { Text("URL") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        adding = false
                        viewModel.addRemote(name, uri)
                    },
                    enabled = name.isNotBlank() && uri.isNotBlank(),
                ) { Text(stringResource(R.string.action_add)) }
            },
            dismissButton = {
                TextButton(onClick = { adding = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/**
 * Asks for a name and a secret for one host.
 *
 * The host is spelled out because a token is about to be handed to it, and the reason
 * the app is asking is that some host wants one.
 */
@Composable
private fun CredentialsDialog(
    host: String,
    action: String,
    onDismiss: () -> Unit,
    onConfirm: (String, CharArray) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.credentials_title, action, host)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.credentials_detail, host),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.field_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(R.string.field_token)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(username, token.toCharArray()) },
                enabled = username.isNotBlank() && token.isNotBlank(),
            ) { Text(action) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun EmptyNote(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
