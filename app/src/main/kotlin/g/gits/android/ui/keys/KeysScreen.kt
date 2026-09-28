package g.gits.android.ui.keys

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import android.net.Uri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import g.gits.android.R
import g.gits.android.data.StoredAccount
import g.gits.android.data.StoredKey
import g.gits.openpgp.KeyAlgorithm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen(onBack: () -> Unit, viewModel: KeysViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    var generating by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var pendingExport by remember { mutableStateOf<ExportRequest?>(null) }

    // The name is decided here so the exported file is recognisable, and the picker
    // starts already pointed at it.
    val exportTarget = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pgp-keys"),
    ) { uri ->
        val request = pendingExport
        pendingExport = null
        if (uri != null && request != null) viewModel.exportTo(uri, request)
    }

    // The picker only chooses the file; the passphrase is asked for once one is
    // chosen, so a mispick does not also cost a typed passphrase.
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingImport = uri }

    state.message?.let { message ->
        LaunchedEffect(message) {
            snackbars.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.keys_title)) },
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
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.keys.isEmpty() && state.accounts.isEmpty()) {
                Text(
                    text = stringResource(R.string.keys_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        SectionHeader(stringResource(R.string.keys_section))
                    }
                    items(state.keys, key = { it.fingerprintHex }) { key ->
                        KeyRow(
                            key = key,
                            signing = key.fingerprintHex == state.selected,
                            onSelect = { viewModel.select(key) },
                            onExportPublic = { pendingExport = ExportRequest(key, public = true) },
                            onExportSecret = { pendingExport = ExportRequest(key, public = false) },
                            onForget = { viewModel.forget(key) },
                        )
                        HorizontalDivider()
                    }
                    if (state.accounts.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.accounts_section)) }
                        items(state.accounts, key = { it.host }) { account ->
                            AccountRow(
                                account = account,
                                onForget = { viewModel.forgetAccount(account) },
                            )
                            HorizontalDivider()
                        }
                        item {
                            TextButton(
                                onClick = viewModel::forgetAllAccounts,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            ) { Text(stringResource(R.string.action_forget_all_accounts)) }
                        }
                    }
                }
            }

            if (state.busy) {
                CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(16.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { generating = true }) {
                Text(stringResource(R.string.action_new_key))
            }
            OutlinedButton(onClick = { pickFile.launch(arrayOf("application/pgp-keys", "text/plain", "*/*")) }) {
                Text(stringResource(R.string.action_import_key))
            }
            if (state.canSign) {
                TextButton(onClick = viewModel::lockAll) { Text(stringResource(R.string.action_lock)) }
            }
        }
    }

    if (generating) {
        GenerateKeyDialog(
            onDismiss = { generating = false },
            onCreate = { name, email, algorithm, passphrase ->
                generating = false
                viewModel.generate(name, email, algorithm, passphrase)
            },
        )
    }

    pendingExport?.let { request ->
        ExportDialog(
            key = request.key,
            public = request.public,
            onDismiss = { pendingExport = null },
            onConfirm = { name ->
                val request = pendingExport
                pendingExport = null
                if (request != null) {
                    exportTarget.launch("${name.ifBlank { request.defaultName() }}.asc")
                }
            },
        )
    }

    pendingImport?.let { uri ->
        PassphraseDialog(
            title = stringResource(R.string.action_import_key),
            onDismiss = { pendingImport = null },
            onConfirm = { passphrase ->
                pendingImport = null
                viewModel.importFrom(uri, passphrase)
            },
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
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** A host the app holds a token for, and the name that goes with it. */
@Composable
private fun AccountRow(account: StoredAccount, onForget: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(account.host, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = account.username.ifBlank { stringResource(R.string.accounts_no_name) },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onForget) { Text(stringResource(R.string.action_forget)) }
    }
}

@Composable
private fun KeyRow(
    key: StoredKey,
    signing: Boolean,
    onSelect: () -> Unit,
    onExportPublic: () -> Unit,
    onExportSecret: () -> Unit,
    onForget: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Card(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = key.userId.ifBlank { stringResource(R.string.keys_unnamed) },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = key.fingerprintHex.chunked(4).joinToString(" "),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = buildString {
                        append(key.algorithm?.name ?: stringResource(R.string.keys_other_algorithm))
                        if (key.canSign) append(" · can sign")
                        if (!key.isPassphraseProtected) append(" · no passphrase")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (signing) {
                    Text(
                        text = stringResource(R.string.keys_signs_with),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Box {
            TextButton(onClick = { menu = true }) { Text("⋯") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (key.canSign && !signing) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_sign_with)) },
                        onClick = {
                            menu = false
                            onSelect()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_export_public)) },
                    onClick = {
                        menu = false
                        onExportPublic()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_export_secret)) },
                    onClick = {
                        menu = false
                        onExportSecret()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_remove_key)) },
                    onClick = {
                        menu = false
                        onForget()
                    },
                )
            }
        }
    }
}

/** Which key is about to be written out, and whether it is the secret half. */
data class ExportRequest(val key: StoredKey, val public: Boolean) {
    fun defaultName(): String = (if (public) "public" else "secret") + "-" + key.fingerprintHex.takeLast(16)
}

/**
 * Names the file, and says plainly what is in it.
 *
 * An export is a copy of a signing key leaving the only place this app is careful about
 * it, so the secret case is labelled rather than left for someone to find out later.
 */
@Composable
private fun ExportDialog(
    key: StoredKey,
    public: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(ExportRequest(key, public).defaultName()) }
    val unprotected = !public && !key.isPassphraseProtected
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_export_key)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_file_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(
                        if (public) R.string.export_public_detail else R.string.export_secret_detail,
                        key.userId.ifBlank { stringResource(R.string.keys_unnamed) },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (unprotected) {
                    Text(
                        text = stringResource(R.string.export_secret_unprotected),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun GenerateKeyDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, KeyAlgorithm, CharArray) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var algorithm by remember { mutableStateOf(KeyAlgorithm.ED25519) }
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val passphraseError = passphrase.isNotEmpty() && passphrase != confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_new_key)) },
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
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyAlgorithm.entries.forEach { option ->
                        TextButton(
                            onClick = { algorithm = option },
                            colors = if (option == algorithm) {
                                androidx.compose.material3.ButtonDefaults.textButtonColors()
                            } else {
                                androidx.compose.material3.ButtonDefaults.textButtonColors()
                                    .copy(
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                            },
                        ) { Text(option.name) }
                    }
                }
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.field_passphrase)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text(stringResource(R.string.field_passphrase_again)) },
                    isError = passphraseError,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                if (passphraseError) {
                    Text(
                        text = stringResource(R.string.keys_passphrase_mismatch),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, email, algorithm, passphrase.toCharArray()) },
                enabled = name.isNotBlank() && email.isNotBlank() &&
                    passphrase.isNotEmpty() && !passphraseError,
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun PassphraseDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text(stringResource(R.string.field_passphrase)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(passphrase.toCharArray()) },
                enabled = passphrase.isNotEmpty(),
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
