package com.geno1024.ai.gits.ui.repo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.geno1024.ai.gits.ui.keys.PassphraseDialog
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
    // The three-corner menu asks which of two or three things is meant, so the choice
    // lives here rather than in the button that asks for it.
    var stashPicker by remember { mutableStateOf(false) }
    var stashAsk by remember { mutableStateOf<StashAsk?>(null) }
    var filePicker by remember { mutableStateOf(false) }
    var making by remember { mutableStateOf<NewEntry?>(null) }

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
                // A selection is a state to finish rather than a screen to leave, so it
                // takes the corner over: the remote can wait until the choice is made.
                actions = {
                    if (state.selected.isNotEmpty()) {
                        SelectionActions(state = state, viewModel = viewModel)
                    } else {
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
                // A selection swaps the corner for what a selection is for, and swaps
                // back the moment the last box is unticked.
                if (state.selected.isEmpty()) {
                    FileActions(
                        onCommit = { committing = true },
                        onStash = { stashPicker = true },
                        onNew = { filePicker = true },
                    )
                } else {
                    StageActions(state = state, viewModel = viewModel)
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> when (state.tab) {
                    RepoTab.WORKING_TREE -> WorkingTreePane(state, viewModel)
                    RepoTab.HISTORY -> HistoryPane(state, viewModel)
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

    // A tap on a file may mean either of two things, and only the person tapping knows
    // which, so the question is asked here rather than guessed at from the file's name.
    state.opening?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::cancelOpen,
            title = { Text(target.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.repo_open_note))
                    TextButton(onClick = { viewModel.preview(target) }) {
                        Text(stringResource(R.string.repo_open_preview))
                    }
                    TextButton(onClick = { viewModel.editElsewhere(target) }) {
                        Text(stringResource(R.string.repo_open_edit))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::cancelOpen) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    state.viewing?.let { viewing ->
        TextDialog(title = viewing.path, content = viewing.content, onDismiss = viewModel::closeFile)
    }

    state.diffing?.let { diff ->
        TextDialog(title = diff.title, content = diff.content, onDismiss = viewModel::closeDiff)
    }

    // The commit that asked for this is still being waited on, so the passphrase ends
    // it rather than reporting on it.
    state.awaitingUnlock?.let { request ->
        PassphraseDialog(
            title = stringResource(R.string.unlock_signing_title),
            note = listOfNotNull(
                stringResource(R.string.unlock_note),
                request.note,
            ).joinToString("\n"),
            optional = false,
            onDismiss = viewModel::cancelUnlock,
            onConfirm = { viewModel.unlockSigning(request, it) },
        )
    }

    if (stashPicker) {
        // One button for the stash and two for what may be done with it, because they
        // are one question in three parts rather than three unrelated actions.
        AlertDialog(
            onDismissRequest = { stashPicker = false },
            title = { Text(stringResource(R.string.repo_stash_action)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { stashPicker = false; stashAsk = StashAsk.STASH }) {
                        Text(stringResource(R.string.repo_stash))
                    }
                    TextButton(onClick = { stashPicker = false; stashAsk = StashAsk.POP }) {
                        Text(stringResource(R.string.repo_stash_pop))
                    }
                    TextButton(onClick = { stashPicker = false; viewModel.openStashes() }) {
                        Text(stringResource(R.string.repo_stashes_action))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { stashPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    stashAsk?.let { ask ->
        // Each of the two has its own page, so what is about to happen can be read
        // before it happens rather than guessed at from a one-word button.
        AlertDialog(
            onDismissRequest = { stashAsk = null },
            title = {
                Text(
                    stringResource(
                        if (ask == StashAsk.STASH) R.string.repo_stash else R.string.repo_stash_pop,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (ask == StashAsk.STASH) R.string.repo_stash_ask else R.string.repo_stash_pop_ask,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        stashAsk = null
                        if (ask == StashAsk.STASH) viewModel.stash() else viewModel.stashPop()
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { stashAsk = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (filePicker) {
        AlertDialog(
            onDismissRequest = { filePicker = false },
            title = { Text(stringResource(R.string.action_new)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            filePicker = false
                            making = NewEntry.FILE
                        },
                    ) { Text(stringResource(R.string.repo_files_new_file)) }
                    TextButton(
                        onClick = {
                            filePicker = false
                            making = NewEntry.FOLDER
                        },
                    ) { Text(stringResource(R.string.repo_files_new_folder)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { filePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
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
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (state.onlyChanged) {
            OnlyChangedHeader(state, viewModel)
        } else {
            FolderHeader(state = state, viewModel = viewModel)
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
                        picking = state.selected.isNotEmpty(),
                        onToggle = { viewModel.toggleSelected(change.path) },
                        onOpen = { viewModel.requestOpen(change.path) },
                        onRename = {
                            viewModel.requestRename(change.path, change.path.substringAfterLast('/'))
                        },
                        onDelete = { viewModel.requestDelete(change.path, directory = false) },
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                // The way back up, named the way every file list names it. It sits in
                // the list because that is where the folders are, and it is only there
                // when there is a folder above to go to.
                if (state.directory.isNotEmpty()) {
                    item(key = "..") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.upDirectory() }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spacer(modifier = Modifier.size(48.dp))
                            Text(
                                text = "..",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                items(state.files, key = { "file/${it.path}" }) { entry ->
                    val change = state.changes.firstOrNull { it.path == entry.path }
                    // A folder is something to go into and a file is something to look
                    // at, so one tap does whichever of the two the name stands for, and
                    // a long press picks it instead — the other way to ask for a stage.
                    // Once a selection is under way the tap joins it rather than opens
                    // the entry, because opening would leave the selection behind
                    // instead of adding to it.
                    val picking = state.selected.isNotEmpty()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (picking) viewModel.toggleSelected(entry.path) else viewModel.openEntry(entry)
                                },
                                onLongClick = { viewModel.toggleSelected(entry.path) },
                            )
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // A box appears once there is something to tick: a changed file
                        // from the start, every row while a selection is under way, or
                        // any row the long press has picked.
                        if (change == null && !picking) {
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

    when (val action = state.pending) {
        null -> {}
        is EntryAction.Rename -> RenameDialog(action, viewModel)
        is EntryAction.Delete -> DeleteDialog(action, viewModel)
    }
}

/**
 * The three things the working tree asks of the corner, one tap each.
 *
 * Three buttons rather than one behind a menu: commit, a stash and a new entry are
 * what this corner is for, and nobody needed the extra choice in between.
 */
@Composable
private fun FileActions(
    onCommit: () -> Unit,
    onStash: () -> Unit,
    onNew: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Ordered so the one taken most often, the commit, sits nearest the thumb.
        ActionButton(icon = Icons.AutoMirrored.Filled.NoteAdd, label = stringResource(R.string.action_new), onClick = onNew)
        ActionButton(icon = Icons.Default.Save, label = stringResource(R.string.repo_stash_action), onClick = onStash)
        ActionButton(icon = Icons.Default.Commit, label = stringResource(R.string.repo_commit_go), onClick = onCommit)
    }
}

/**
 * The stage buttons, which are what a selection is for.
 *
 * Which of them shows is read off the selection itself: files waiting to be staged ask
 * for the first, already staged files ask for the other, and a selection holding both
 * asks for both. With nothing selected the corner goes back to [FileActions].
 */
@Composable
private fun StageActions(state: RepoUiState, viewModel: RepoViewModel) {
    val staged = state.staged.mapTo(mutableSetOf()) { it.path }
    val waiting = state.selected.any { it !in staged }
    val already = state.selected.any { it in staged }
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (waiting) {
            ActionButton(
                icon = Icons.Default.Add,
                label = stringResource(R.string.repo_stage),
                onClick = viewModel::stageSelected,
            )
        }
        if (already) {
            ActionButton(
                icon = Icons.Default.Remove,
                label = stringResource(R.string.repo_unstage),
                onClick = viewModel::unstageSelected,
            )
        }
    }
}

/** One labelled button in the corner. The label is the point, so it is not hidden from a screen reader. */
@Composable
private fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        text = { Text(label) },
    )
}

/**
 * What a selection is asked for, in place of the buttons that talk to a remote.
 *
 * A selection is a state to finish, not a screen to leave: the refresh and the two
 * transfers are hidden until the last box is unticked, so a pull cannot start under a
 * half-made choice.
 */
@Composable
private fun SelectionActions(state: RepoUiState, viewModel: RepoViewModel) {
    TextButton(onClick = viewModel::selectAll) {
        Text(stringResource(R.string.repo_select_all))
    }
    TextButton(onClick = viewModel::clearSelection) {
        Text(stringResource(R.string.repo_select_none))
    }
    TextButton(onClick = viewModel::invertSelection) {
        Text(stringResource(R.string.repo_select_invert))
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

                // What the commit button is waiting for, so a greyed-out button is an
                // answered question rather than one the user has to guess at.
                state.commitBlock(message)?.let { block ->
                    Text(
                        text = stringResource(
                            when (block) {
                                CommitBlock.BUSY -> R.string.repo_commit_busy
                                CommitBlock.NOTHING_STAGED -> R.string.repo_commit_not_staged
                                CommitBlock.NO_MESSAGE -> R.string.repo_commit_message_empty
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Staging stays two taps where two taps make sense; this is where they
                // happen at once, and the reason above says when that is needed.
                if (state.unstaged.isNotEmpty()) {
                    TextButton(onClick = viewModel::stageAll) {
                        Text(stringResource(R.string.repo_commit_stage_all))
                    }
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
                // One answer to "why is this off", so the button and the reason beneath
                // it can never disagree.
                enabled = state.canCommit && state.commitBlock(message) == null,
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

/** Where this list is, and the one question the list itself is asked. */
@Composable
private fun FolderHeader(state: RepoUiState, viewModel: RepoViewModel) {
    Text(
        text = state.directory.ifEmpty { stringResource(R.string.repo_files_root) },
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )

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
private fun HistoryPane(state: RepoUiState, viewModel: RepoViewModel) {
    if (state.history.isEmpty()) {
        EmptyNote("This repository has no commits yet.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(state.history, key = { it.id }) { entry ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // The whole line is the handle: a commit is one thing, and there is
                    // no part of its row worth tapping separately from the rest.
                    .clickable { viewModel.openDiff(entry.id, "${entry.shortId.take(7)} · ${entry.subject}") }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
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

/** Which of the two stash operations the confirmation is for. */
private enum class StashAsk { STASH, POP }

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

/**
 * One line of the flat change list: what changed, how, and what may be done with it.
 *
 * A tap opens the change while there is no selection to join, and becomes one more
 * pick as soon as there is — the two cannot both happen at once.
 */
@Composable
private fun ChangeRow(
    change: WorkingChange,
    selected: Boolean,
    picking: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (picking) onToggle() else onOpen() },
                onLongClick = onToggle,
            )
            .padding(vertical = 2.dp),
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
private fun TextDialog(title: String, content: String, onDismiss: () -> Unit) {
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
                        text = title,
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
                        text = content,
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
