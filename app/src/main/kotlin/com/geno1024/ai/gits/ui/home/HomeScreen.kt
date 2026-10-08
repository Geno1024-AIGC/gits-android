package com.geno1024.ai.gits.ui.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geno1024.ai.gits.R
import com.geno1024.ai.gits.data.DocumentTree
import com.geno1024.ai.gits.data.RecentRepository
import com.geno1024.ai.gits.ui.CommandLabel
import com.geno1024.ai.gits.ui.CommandNote
import com.geno1024.ai.gits.ui.CredentialsDialog
import com.geno1024.ai.gits.ui.PromptDialog
import com.geno1024.ai.gits.ui.repo.inverted
import com.geno1024.ai.gits.ui.theme.MonoFontFamily
import java.io.File

/** What the folder picker was opened for, so the answer can be put to that use. */
private enum class FolderWant { OPEN, CREATE, CLONE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpen: (File) -> Unit,
    onManageKeys: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prompt by viewModel.questions.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var creating by remember { mutableStateOf(false) }
    var cloning by remember { mutableStateOf(false) }
    // The entries being taken off the list, while the answer is being thought about.
    var forgetting by remember { mutableStateOf(listOf<RecentRepository>()) }
    var menu by remember { mutableStateOf(false) }

    /**
     * What the list has picked, by repository path.
     *
     * The long press no longer takes an entry off the list by itself: it picks, and
     * what a pick is for is shown afterwards, so nothing is ever removed by a gesture
     * that says nothing about where it leads.
     */
    var selected by remember { mutableStateOf(setOf<String>()) }

    // A selection belongs to this list, so an entry that went away stops counting.
    LaunchedEffect(state.repositories) {
        val paths = state.repositories.mapTo(mutableSetOf()) { it.path }
        selected = selected.intersect(paths)
    }

    val picking = selected.isNotEmpty()

    fun toggle(path: String) {
        selected = if (path in selected) selected - path else selected + path
    }

    /**
     * The folder a new repository would be made inside, when one was picked.
     *
     * Held here rather than passed down so that picking a folder is the same gesture
     * whether it ends in opening something or making something, and so that both
     * dialogs see the same answer without being opened together.
     */
    var parent by remember { mutableStateOf<File?>(null) }

    var wanted by remember { mutableStateOf<FolderWant?>(null) }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        val ask = wanted
        wanted = null
        if (uri == null) return@rememberLauncherForActivityResult
        // Without this the grant is gone the next time the app starts, and the entry
        // in the recents list would point at a folder this app can no longer read.
        DocumentTree.takePersistablePermission(context, uri)
        DocumentTree.requireDirectoryOf(uri)
            .onSuccess { picked ->
                parent = picked
                when (ask) {
                    FolderWant.OPEN -> viewModel.open(picked.path, onOpen)
                    // The dialogs are already up when the pick came from inside one, so
                    // this only has to make sure they stay up now that there is an answer.
                    FolderWant.CREATE -> creating = true
                    FolderWant.CLONE -> cloning = true
                    null -> Unit
                }
            }
            .onFailure { viewModel.report(it.message ?: "That folder cannot be used.") }
    }

    fun pick(want: FolderWant) {
        wanted = want
        pickFolder.launch(DocumentTree.initialUri())
    }

    fun selectAll() {
        selected = state.repositories.mapTo(mutableSetOf()) { it.path }
    }

    fun invertSelection() {
        selected = inverted(selected, state.repositories.map { it.path })
    }

    // A selection is a state to finish rather than a screen to leave, so the back
    // key unticks it instead of walking out from under a half-made choice.
    BackHandler(enabled = picking) { selected = emptySet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    // What a selection is for takes the corner over the way it does in
                    // the repository list: the settings, the keys and the folder picker
                    // wait until the choice is made.
                    if (picking) {
                        TextButton(onClick = { selectAll() }) {
                            Text(stringResource(R.string.selection_all))
                        }
                        TextButton(onClick = { selected = emptySet() }) {
                            Text(stringResource(R.string.selection_none))
                        }
                        TextButton(onClick = { invertSelection() }) {
                            Text(stringResource(R.string.selection_invert))
                        }
                    } else {
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                painterResource(R.drawable.ic_settings),
                                contentDescription = stringResource(R.string.settings_title),
                            )
                        }
                        IconButton(onClick = onManageKeys) {
                            Icon(
                                painterResource(R.drawable.ic_vpn_key),
                                contentDescription = stringResource(R.string.keys_title),
                            )
                        }
                        IconButton(onClick = { pick(FolderWant.OPEN) }) {
                            Icon(
                                painterResource(R.drawable.ic_folder_open),
                                contentDescription = stringResource(R.string.action_open_repository),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (picking) {
                // The corner belongs to the selection while there is one: the way off
                // the list sits where the choice can still be looked at first, and one
                // repository picked is a repository that can be opened straight away.
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (selected.size == 1) {
                        ActionButton(
                            icon = painterResource(R.drawable.ic_folder_open),
                            label = stringResource(R.string.action_open_repository),
                            onClick = {
                                val only = state.repositories.firstOrNull { it.path in selected }
                                    ?: return@ActionButton
                                selected = emptySet()
                                viewModel.open(only.path, onOpen)
                            },
                        )
                    }
                    ActionButton(
                        icon = painterResource(R.drawable.ic_delete),
                        label = stringResource(R.string.action_forget),
                        onClick = { forgetting = state.repositories.filter { it.path in selected } },
                    )
                }
            } else {
                Box {
                    FloatingActionButton(onClick = { menu = true }) {
                        Icon(
                            painterResource(R.drawable.ic_add),
                            contentDescription = stringResource(R.string.action_new),
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { CommandLabel(stringResource(R.string.create_repository_action), "init") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_create_new_folder), contentDescription = null) },
                            onClick = {
                                menu = false
                                creating = true
                            },
                        )
                        DropdownMenuItem(
                            text = { CommandLabel(stringResource(R.string.action_clone_repository), "clone") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_cloud_download), contentDescription = null) },
                            onClick = {
                                menu = false
                                cloning = true
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.busy -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.repositories.isEmpty() -> EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    detail = stringResource(R.string.home_empty_detail),
                    onOpen = { pick(FolderWant.OPEN) },
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
                            picking = picking,
                            picked = repository.path in selected,
                            onOpen = { viewModel.open(repository.path, onOpen) },
                            onToggle = { toggle(repository.path) },
                        )
                    }
                }
            }

            state.error?.let { message ->
                ErrorBar(message = message, onDismiss = viewModel::clearError)
            }
        }
    }

    if (forgetting.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { forgetting = emptyList() },
            title = {
                Text(
                    text = forgetting.joinToString { it.name },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = MonoFontFamily,
                )
            },
            text = { Text(stringResource(R.string.home_forget_note)) },
            confirmButton = {
                TextButton(onClick = {
                    val taken = forgetting
                    forgetting = emptyList()
                    selected = emptySet()
                    taken.forEach(viewModel::forget)
                }) { Text(stringResource(R.string.action_forget)) }
            },
            dismissButton = {
                TextButton(onClick = { forgetting = emptyList() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (creating) {
        // Read once as the dialog opens, so that a default changed in settings is
        // taken up here rather than on the next keystroke, and so that an edit made
        // in the dialog is not overwritten by the setting it started from.
        val defaultBranch = remember { viewModel.defaultBranch() }
        CreateRepositoryDialog(
            suggestedFolder = parent?.let { "${it.name}/" } ?: viewModel.suggestedFolder,
            parent = parent,
            defaultBranch = defaultBranch,
            onPickParent = { pick(FolderWant.CREATE) },
            onDismiss = { creating = false },
            // Left open until the repository exists: a failure that closes the dialog
            // takes the address, the folder and the branch with it, and asking for all
            // three again is the whole price of a typo.
            onCreate = { target, branch -> viewModel.create(target, branch, onOpen) },
        )
    }

    if (cloning) {
        CloneRepositoryDialog(
            suggestedFolder = parent?.let { "${it.name}/" } ?: viewModel.suggestedFolder,
            parent = parent,
            onPickParent = { pick(FolderWant.CLONE) },
            onDismiss = { cloning = false },
            onClone = { address, target, branch ->
                viewModel.clone(address, target, branch, onOpen)
            },
        )
    }

    state.awaitingCredentials?.let { host ->
        CredentialsDialog(
            host = host,
            action = stringResource(R.string.action_clone),
            onDismiss = viewModel::cancelCredentials,
            onConfirm = viewModel::provideCredentials,
        )
    }

    prompt?.let { asking ->
        PromptDialog(prompt = asking, onAnswer = asking::answer)
    }
}

@Composable
private fun RepositoryRow(
    repository: RecentRepository,
    picking: Boolean,
    picked: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    // The long press picks rather than takes away: a gesture that removes an entry is
    // one nobody can see coming, so what a pick is for is shown afterwards, in the
    // corner for the one and along the top for the set. Once a selection is under way
    // the tap joins it instead of opening, because opening would leave the selection
    // behind rather than add to it.
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (picking) onToggle() else onOpen() },
                onLongClick = onToggle,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The folder becomes a box while there is something to tick: one says
            // "open me", the other says "picked", and a row shows only one of them.
            if (picking) {
                Checkbox(checked = picked, onCheckedChange = { onToggle() })
            } else {
                Icon(painterResource(R.drawable.ic_folder), contentDescription = null)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repository.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = MonoFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = repository.path,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = MonoFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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

/**
 * Makes a repository somewhere, in one of two shapes: straight into the folder the
 * user picked, or into a folder named under it.
 *
 * The line under the name is what keeps the two honest. Before it, the picked folder
 * was shown and then quietly ignored unless a name went with it, and the button said
 * nothing about where the repository would end up; now the place is spelled out before
 * the tap rather than discovered afterwards.
 */
@Composable
private fun CreateRepositoryDialog(
    suggestedFolder: String,
    parent: File?,
    defaultBranch: String,
    onPickParent: () -> Unit,
    onDismiss: () -> Unit,
    onCreate: (File, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf(defaultBranch) }
    val target = repositoryTarget(parent, name)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_create_repository)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ParentField(parent = parent, onPickParent = onPickParent)
                FolderField(path = name, onPathChange = { name = it }, suggested = suggestedFolder)
                FolderHint(text = stringResource(R.string.create_folder_hint))
                TargetLine(target = target)
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text(stringResource(R.string.field_initial_branch)) },
                    supportingText = { CommandNote("config init.defaultBranch") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { target?.let { onCreate(it, branch) } },
                enabled = target != null,
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Copies an address into a folder, which is where a clone differs from the dialog
 * beside it: the repository already exists somewhere else and only has to land.
 *
 * The folder name is read off the address the way `git clone` does it, and goes on
 * being read off it until the user writes one of their own — an edit is never taken
 * back out from under them by the next character they type. What is guessed is shown
 * as a value rather than as a placeholder because it is the answer most of the time,
 * and it sits above the line saying where the repository will go, so a wrong guess is
 * corrected before the tap instead of found afterwards.
 */
@Composable
private fun CloneRepositoryDialog(
    suggestedFolder: String,
    parent: File?,
    onPickParent: () -> Unit,
    onDismiss: () -> Unit,
    onClone: (String, File, String?) -> Unit,
) {
    var address by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var namedIt by remember { mutableStateOf(false) }
    var branch by remember { mutableStateOf("") }

    val shown = if (namedIt) name else repositoryNameOf(address).orEmpty()
    val target = repositoryTarget(parent, shown)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_clone_repository)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.field_address)) },
                    placeholder = { Text(stringResource(R.string.clone_address_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ParentField(parent = parent, onPickParent = onPickParent)
                FolderField(
                    path = shown,
                    onPathChange = {
                        namedIt = true
                        name = it
                    },
                    suggested = suggestedFolder,
                )
                FolderHint(text = stringResource(R.string.clone_folder_hint))
                TargetLine(target = target)
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text(stringResource(R.string.field_clone_branch)) },
                    supportingText = { CommandNote("clone --branch") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val place = target ?: return@TextButton
                    onClone(address, place, branch)
                },
                enabled = address.isNotBlank() && target != null,
            ) { CommandLabel(stringResource(R.string.clone_action), "clone") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ParentField(parent: File?, onPickParent: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onPickParent) {
            Icon(
                painterResource(R.drawable.ic_create_new_folder),
                contentDescription = stringResource(R.string.action_pick_parent),
            )
        }
        Text(
            text = buildAnnotatedString {
                val path = parent?.path
                if (path != null) {
                    withStyle(SpanStyle(fontFamily = MonoFontFamily)) { append(path) }
                } else {
                    append(stringResource(R.string.create_no_parent))
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One labelled button in the corner. The label is the point, so it is not hidden from a screen reader. */
@Composable
private fun ActionButton(icon: Painter, label: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        text = { Text(label) },
    )
}

/** Names where the repository will actually go, before the button is pressed. */
@Composable
private fun TargetLine(target: File?) {
    if (target == null) return
    Text(
        text = stringResource(R.string.create_target, target.path),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
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
private fun FolderHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
