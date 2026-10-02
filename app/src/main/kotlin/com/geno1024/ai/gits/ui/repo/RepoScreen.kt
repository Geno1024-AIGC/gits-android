package com.geno1024.ai.gits.ui.repo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.geno1024.ai.gits.R
import com.geno1024.ai.gits.git.WorkingChange
import com.geno1024.ai.gits.ui.CredentialsDialog
import android.app.Application
import java.text.DateFormat
import java.util.Date
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
    // Held here rather than in the pane so that dismissing the dialog does not throw
    // away a message somebody was halfway through writing.
    var committing by remember { mutableStateOf(false) }
    var commitMessage by remember { mutableStateOf("") }

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
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbars) },
        // Only on the working tree: the other panes have one thing each to do and no
        // menu worth opening, and a button that is sometimes there and sometimes not
        // is worse than one that is only ever where it belongs.
        floatingActionButton = {
            if (state.tab == RepoTab.WORKING_TREE) {
                RepoActions(
                    onCommit = { committing = true },
                    onStash = viewModel::stash,
                    onStashPop = viewModel::stashPop,
                    onStashes = viewModel::openStashes,
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> when (state.tab) {
                    RepoTab.WORKING_TREE -> WorkingTreePane(state, viewModel)
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

    state.viewing?.let { viewing ->
        FileDialog(viewing = viewing, onDismiss = viewModel::closeFile)
    }

    if (committing) {
        CommitDialog(
            state = state,
            viewModel = viewModel,
            message = commitMessage,
            onMessageChange = { commitMessage = it },
            onDismiss = { committing = false },
        )
    }

    if (state.stashesOpen) {
        StashesDialog(state = state, viewModel = viewModel)
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

/**
 * What is on disk, and how to get at it.
 *
 * The commit itself is not here: it happens through the button in the corner, because
 * writing a message and choosing a signature is a separate question from the one this
 * pane answers, and keeping both on screen at once left neither enough room to use.
 */
@Composable
private fun WorkingTreePane(state: RepoUiState, viewModel: RepoViewModel) {
    var making by remember { mutableStateOf<NewEntry?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (state.onlyChanged) {
            OnlyChangedHeader(state, viewModel)
        } else {
            FolderHeader(state = state, viewModel = viewModel, onNew = { making = it })
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = viewModel::stageSelected,
                enabled = state.selected.isNotEmpty(),
            ) { Text(stringResource(R.string.repo_stage)) }
            OutlinedButton(
                onClick = viewModel::unstageSelected,
                enabled = state.staged.any { it.path in state.selected },
            ) { Text(stringResource(R.string.repo_unstage)) }
            OutlinedButton(onClick = viewModel::selectAll, enabled = state.changes.isNotEmpty()) {
                Text(stringResource(R.string.repo_all))
            }
        }

        if (state.staged.isNotEmpty()) {
            Text(
                text = stringResource(R.string.repo_staged_count, state.staged.size),
                style = MaterialTheme.typography.labelSmall,
            )
        }

        HorizontalDivider()

        val nothingHere = if (state.onlyChanged) state.changes.isEmpty() else state.files.isEmpty()
        if (nothingHere) {
            EmptyNote(
                text = stringResource(
                    if (state.onlyChanged) R.string.repo_changes_none else R.string.repo_files_empty,
                ),
                modifier = Modifier.weight(1f),
            )
        } else if (state.onlyChanged) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.changes, key = { "change/${it.path}" }) { change ->
                    ChangeRow(
                        change = change,
                        selected = change.path in state.selected,
                        onToggle = { viewModel.toggleSelected(change.path) },
                        onOpen = { viewModel.view(change.path) },
                        onRename = {
                            viewModel.requestRename(change.path, change.path.substringAfterLast('/'))
                        },
                        onDelete = { viewModel.requestDelete(change.path, directory = false) },
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.files, key = { "file/${it.path}" }) { entry ->
                    val change = state.changes.firstOrNull { it.path == entry.path }
                    // A folder is something to go into and a file is something to look
                    // at, so one tap does whichever of the two the name stands for.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openEntry(entry) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (change == null) {
                            // The gap where a checkbox would sit, so that a folder and a
                            // file name the same place instead of dancing sideways.
                            Spacer(modifier = Modifier.size(48.dp))
                        } else {
                            Checkbox(
                                checked = entry.path in state.selected,
                                onCheckedChange = { viewModel.toggleSelected(entry.path) },
                            )
                        }
                        Text(
                            text = if (entry.directory) "${entry.name}/" else entry.name,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (change != null) {
                            Text(
                                text = change.kind.name.lowercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        EntryMenu(
                            onRename = { viewModel.requestRename(entry.path, entry.name) },
                            onDelete = { viewModel.requestDelete(entry.path, entry.directory) },
                        )
                    }
                }
            }
        }
    }

    making?.let { what ->
        NewEntryDialog(
            title = stringResource(
                if (what == NewEntry.FILE) R.string.repo_files_new_file else R.string.repo_files_new_folder,
            ),
            onDismiss = { making = null },
            onConfirm = { name ->
                making = null
                viewModel.createEntry(name, what == NewEntry.FOLDER)
            },
        )
    }

    when (val action = state.pending) {
        null -> {}
        is EntryAction.Rename -> RenameDialog(action, viewModel)
        is EntryAction.Delete -> DeleteDialog(action, viewModel)
    }
}

/**
 * Everything the working tree is asked to do besides pointing at a file.
 *
 * A list has room for names and not much else, so the things taken *on* the list as a
 * whole sit behind one button instead of holding space beside it the whole time.
 */
@Composable
private fun RepoActions(
    onCommit: () -> Unit,
    onStash: () -> Unit,
    onStashPop: () -> Unit,
    onStashes: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FloatingActionButton(onClick = { open = true }) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.repo_actions),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.repo_commit_title)) },
                onClick = {
                    open = false
                    onCommit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.repo_stash)) },
                onClick = {
                    open = false
                    onStash()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.repo_stash_pop)) },
                onClick = {
                    open = false
                    onStashPop()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.repo_stashes)) },
                onClick = {
                    open = false
                    onStashes()
                },
            )
        }
    }
}

/**
 * Writing the commit, which is a question of its own and not one the list can answer.
 *
 * The identity and the signature live here too: they are decisions made for this
 * particular commit, so asking them here keeps them next to the thing they decide.
 */
@Composable
private fun CommitDialog(
    state: RepoUiState,
    viewModel: RepoViewModel,
    message: String,
    onMessageChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.repo_commit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = message,
                    onValueChange = onMessageChange,
                    label = { Text(stringResource(R.string.repo_commit_message)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { editing = true }) {
                        Text(stringResource(R.string.repo_commit_who))
                    }
                    Text(
                        text = state.identity?.let { "${it.name} <${it.email}>" }
                            ?: stringResource(R.string.repo_no_identity),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

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
                    Text(
                        text = stringResource(R.string.repo_commit_sign),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (!state.signingAvailable) {
                    Text(
                        text = stringResource(R.string.repo_no_signing_key),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    viewModel.commit(message)
                    // Cleared here rather than in the pane, because the pane is not what
                    // opened this: a sent message should not come back next time.
                    onMessageChange("")
                },
                enabled = state.canCommit && message.isNotBlank() && !state.busy,
            ) { Text(stringResource(R.string.repo_commit_go)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

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

/** What has been put aside, and the two things each one of them may be asked for. */
@Composable
private fun StashesDialog(state: RepoUiState, viewModel: RepoViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::closeStashes,
        title = { Text(stringResource(R.string.repo_stashes)) },
        text = {
            if (state.stashes.isEmpty()) {
                Text(stringResource(R.string.repo_stash_empty))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(state.stashes, key = { it.id }) { stash ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stash.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = DateFormat.getDateTimeInstance().format(Date(stash.time)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { viewModel.restoreStash(stash.ref) }) {
                                Text(stringResource(R.string.repo_stash_restore))
                            }
                            IconButton(onClick = { viewModel.dropStash(stash.ref) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.repo_stash_drop),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::closeStashes) {
                Text(stringResource(R.string.action_ok))
            }
        },
    )
}

/** Where this list is, a way back up, and the two things a folder is asked for. */
@Composable
private fun FolderHeader(
    state: RepoUiState,
    viewModel: RepoViewModel,
    onNew: (NewEntry) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = state.directory.ifEmpty { stringResource(R.string.repo_files_root) },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = viewModel::upDirectory, enabled = state.directory.isNotEmpty()) {
            Text(stringResource(R.string.repo_files_up))
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = { onNew(NewEntry.FILE) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.repo_files_new_file))
        }
        OutlinedButton(onClick = { onNew(NewEntry.FOLDER) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.repo_files_new_folder))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.setOnlyChanged(!state.onlyChanged) }
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = state.onlyChanged,
            onCheckedChange = viewModel::setOnlyChanged,
        )
        Text(
            text = stringResource(R.string.repo_files_only_changed),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** The header of the flat list, where the folders are not part of the question. */
@Composable
private fun OnlyChangedHeader(state: RepoUiState, viewModel: RepoViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = state.onlyChanged,
            onCheckedChange = viewModel::setOnlyChanged,
        )
        Text(
            text = stringResource(R.string.repo_files_only_changed),
            style = MaterialTheme.typography.bodyMedium,
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

/** What the new-name dialog is being asked to make. */
private enum class NewEntry { FILE, FOLDER }

@Composable
private fun NewEntryDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.repo_files_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** One line of the flat change list: what changed, how, and what may be done with it. */
@Composable
private fun ChangeRow(
    change: WorkingChange,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Text(
            text = change.path,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = change.kind.name.lowercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        EntryMenu(onRename = onRename, onDelete = onDelete)
    }
}

/**
 * The two things an entry may be asked for, folded behind one button.
 *
 * Both are destructive enough to be behind a second tap, and neither deserves a
 * permanent column of its own in a list where most rows are just names.
 */
@Composable
private fun EntryMenu(onRename: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.repo_files_more),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.repo_files_rename)) },
                onClick = {
                    open = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.repo_files_delete)) },
                onClick = {
                    open = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun RenameDialog(action: EntryAction.Rename, viewModel: RepoViewModel) {
    var name by remember(action.path) { mutableStateOf(action.current) }
    AlertDialog(
        onDismissRequest = viewModel::cancelEntryAction,
        title = { Text(stringResource(R.string.repo_files_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.repo_files_rename_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.renameEntry(name.trim()) },
                enabled = name.isNotBlank() && name.trim() != action.current,
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = viewModel::cancelEntryAction) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * The one question asked before anything is removed: is this really the one.
 *
 * A folder takes its contents with it, and the answer says so, because the difference
 * between deleting a file and deleting a folder is not one anybody should have to
 * remember the app's habit about.
 */
@Composable
private fun DeleteDialog(action: EntryAction.Delete, viewModel: RepoViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::cancelEntryAction,
        title = { Text(stringResource(R.string.repo_files_delete)) },
        text = {
            Text(
                stringResource(
                    if (action.directory) {
                        R.string.repo_files_delete_folder
                    } else {
                        R.string.repo_files_delete_file
                    },
                    action.path,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = viewModel::deleteEntry) {
                Text(stringResource(R.string.repo_files_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::cancelEntryAction) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * One file, read whole, shown as it is.
 *
 * Selectable because a line of a stack trace is worth copying, and monospaced because
 * the file said so first.
 */
@Composable
private fun FileDialog(viewing: Viewing, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                // A dialog is not inside the screen's scaffold, so nothing above it is
                // holding the bars back and the filename would sit under the clock.
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = viewing.path,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.repo_files_close),
                        )
                    }
                }
                HorizontalDivider()
                SelectionContainer(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Text(
                        text = viewing.content,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String, modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
