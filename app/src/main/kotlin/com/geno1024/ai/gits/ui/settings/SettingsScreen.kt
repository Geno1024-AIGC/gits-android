package com.geno1024.ai.gits.ui.settings

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geno1024.ai.gits.R
import com.geno1024.ai.gits.data.AppSettings
import com.geno1024.ai.gits.data.CredentialStore
import com.geno1024.ai.gits.data.IdentityStore
import com.geno1024.ai.gits.data.KeyStore
import com.geno1024.ai.gits.data.StoredAccount
import com.geno1024.ai.gits.data.StoredKey
import com.geno1024.ai.gits.git.Identity
import com.geno1024.ai.gits.git.toCredentialHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everything the app is configured with that is not a repository's own settings.
 *
 * The update check lives here rather than on its own screen: it is a setting in the
 * sense that a person changes it once and forgets, and a bare icon on the home screen
 * for it gave it more prominence than a feature that runs on its own anyway.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenKeys: () -> Unit,
    onOpenUpdates: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editingIdentity by remember { mutableStateOf(false) }
    var editingBranch by remember { mutableStateOf(false) }
    var editingAccounts by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { SectionHeader(stringResource(R.string.settings_section_you)) }

            item {
                SettingsRow(
                    title = stringResource(R.string.settings_identity),
                    subtitle = state.identity?.let { "${it.name} <${it.email}>" }
                        ?: stringResource(R.string.settings_identity_unset),
                    onClick = { editingIdentity = true },
                )
            }

            item {
                SettingsRow(
                    title = stringResource(R.string.keys_title),
                    subtitle = when {
                        state.keys.isEmpty() -> stringResource(R.string.settings_no_keys)
                        state.selectedFingerprint == null ->
                            stringResource(R.string.settings_keys_no_selection, state.keys.size)

                        else -> stringResource(R.string.settings_keys_selected, state.keys.size)
                    },
                    onClick = onOpenKeys,
                )
            }

            item {
                SettingsRow(
                    title = stringResource(R.string.accounts_section),
                    subtitle = if (state.accounts.isEmpty()) {
                        stringResource(R.string.settings_accounts_none)
                    } else {
                        state.accounts.joinToString { it.host }
                    },
                    onClick = { editingAccounts = true },
                )
            }

            item { SectionHeader(stringResource(R.string.settings_section_repositories)) }

            item {
                SettingsRow(
                    title = stringResource(R.string.settings_default_branch),
                    subtitle = state.defaultBranch,
                    onClick = { editingBranch = true },
                )
            }

            item { SectionHeader(stringResource(R.string.settings_section_app)) }

            item {
                SettingsRow(
                    title = stringResource(R.string.update_title),
                    subtitle = stringResource(R.string.settings_update_subtitle),
                    onClick = onOpenUpdates,
                )
            }
        }
    }

    if (editingIdentity) {
        IdentityDialog(
            existing = state.identity,
            onDismiss = { editingIdentity = false },
            onSave = {
                viewModel.saveIdentity(it)
                editingIdentity = false
            },
        )
    }

    if (editingBranch) {
        DefaultBranchDialog(
            current = state.defaultBranch,
            onDismiss = { editingBranch = false },
            onSave = {
                viewModel.saveDefaultBranch(it)
                editingBranch = false
            },
        )
    }

    if (editingAccounts) {
        AccountsDialog(
            accounts = state.accounts,
            onDismiss = { editingAccounts = false },
            onAdd = { host, username, token ->
                viewModel.addAccount(host, username, token)
                editingAccounts = false
            },
            onRemove = viewModel::removeAccount,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        content = content,
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IdentityDialog(
    existing: Identity?,
    onDismiss: () -> Unit,
    onSave: (Identity) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var email by remember { mutableStateOf(existing?.email.orEmpty()) }
    val valid = name.isNotBlank() && email.isNotBlank() && email.contains('@')

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_identity)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.field_email)) },
                    singleLine = true,
                    isError = email.isNotEmpty() && !email.contains('@'),
                )
                Text(
                    text = stringResource(R.string.settings_identity_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(Identity(name.trim(), email.trim())) },
                enabled = valid,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Sets what a new repository starts on.
 *
 * Held here rather than decided at the moment of creation because it is the kind of
 * answer that should stay put: someone who works on `trunk` should not have to
 * remember to say so on every repository, and someone who does not should be able to
 * see that the app was going to.
 */
@Composable
private fun DefaultBranchDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var branch by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_default_branch)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text(stringResource(R.string.field_initial_branch)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.settings_default_branch_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(branch) },
                enabled = branch.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Adds and removes the secrets the app holds for remote hosts.
 *
 * The form is here rather than only at the moment a host demands something, because
 * a token entered under time pressure against a failing push is the one most likely
 * to be wrong, and because a secret you cannot see the back of is one you cannot
 * revoke. Which host a secret belongs to is asked for as an address, since that is
 * what the user has in front of them, and it is filed under the name the transport
 * will ask about.
 */
@Composable
private fun AccountsDialog(
    accounts: List<StoredAccount>,
    onDismiss: () -> Unit,
    onAdd: (String, String, CharArray) -> Unit,
    onRemove: (String) -> Unit,
) {
    var address by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.accounts_section)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (accounts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_accounts_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    accounts.forEach { account ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(account.host, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = account.username.ifEmpty {
                                        stringResource(R.string.accounts_no_name)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onRemove(account.host) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.action_forget_account),
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.field_host)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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
                onClick = { onAdd(address, username, token.toCharArray()) },
                enabled = address.isNotBlank() && username.isNotBlank() && token.isNotBlank(),
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        },
    )
}

data class SettingsUiState(
    val identity: Identity? = null,
    val keys: List<StoredKey> = emptyList(),
    val selectedFingerprint: String? = null,
    val accounts: List<StoredAccount> = emptyList(),
    val defaultBranch: String = AppSettings.DEFAULT_BRANCH,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val identityStore = IdentityStore.getInstance(application)
    private val keyStore = KeyStore.getInstance(application)
    private val credentialStore = CredentialStore.getInstance(application)
    private val appSettings = AppSettings.of(application)

    val uiState = kotlinx.coroutines.flow.MutableStateFlow(SettingsUiState())

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        val keys = withContext(Dispatchers.IO) { keyStore.keys() }
        uiState.value = SettingsUiState(
            identity = identityStore.identity(),
            keys = keys,
            selectedFingerprint = keyStore.selected,
            accounts = credentialStore.accounts().sortedBy { it.host },
            defaultBranch = appSettings.initialBranch,
        )
    }

    fun saveIdentity(identity: Identity) {
        identityStore.remember(identity)
        refresh()
    }

    fun saveDefaultBranch(branch: String) {
        appSettings.initialBranch = branch
        refresh()
    }

    /**
     * Keeps a name and a secret for a host.
     *
     * The field takes an address rather than a bare host, because an address is what
     * the user has in front of them — and the entry has to be filed under the name
     * the transport will ask for, or the secret sits somewhere nothing looks for it.
     * A bare host, a host and a port, a host and a path all reach the same answer;
     * something with no host in it at all, such as a local path, is not stored.
     */
    fun addAccount(address: String, username: String, token: CharArray) {
        val host = address.toCredentialHost()
        if (host.isNotEmpty()) credentialStore.remember(host, username.trim(), token)
        refresh()
    }

    fun removeAccount(host: String) {
        credentialStore.forget(host)
        refresh()
    }
}
