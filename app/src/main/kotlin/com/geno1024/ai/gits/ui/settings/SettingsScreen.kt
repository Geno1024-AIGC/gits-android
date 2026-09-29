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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geno1024.ai.gits.R
import com.geno1024.ai.gits.data.IdentityStore
import com.geno1024.ai.gits.data.KeyStore
import com.geno1024.ai.gits.data.StoredKey
import com.geno1024.ai.gits.git.Identity
import com.geno1024.ai.gits.ui.keys.KeysScreen
import com.geno1024.ai.gits.ui.update.UpdatePanel
import com.geno1024.ai.gits.ui.update.UpdateViewModel
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
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val updateViewModel: UpdateViewModel = viewModel()
    var editingIdentity by remember { mutableStateOf(false) }
    var inKeys by remember { mutableStateOf(false) }
    var showingUpdates by remember { mutableStateOf(false) }

    if (inKeys) {
        // Reached by pushing rather than routing, so that going back lands on the
        // settings list still scrolled to where it was, not on a fresh home screen.
        KeysScreen(onBack = { inKeys = false })
        return
    }

    if (showingUpdates) {
        SettingsScaffold(title = stringResource(R.string.update_title), onBack = { showingUpdates = false }) { padding ->
            UpdatePanel(
                viewModel = updateViewModel,
                modifier = Modifier.padding(padding),
                showInstalled = true,
            )
        }
        return
    }

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
                    onClick = { inKeys = true },
                )
            }

            item { SectionHeader(stringResource(R.string.settings_section_app)) }

            item {
                SettingsRow(
                    title = stringResource(R.string.update_title),
                    subtitle = stringResource(R.string.settings_update_subtitle),
                    onClick = { showingUpdates = true },
                )
            }
        }
    }

    if (editingIdentity) {
        IdentityDialog(
            existing = state.identity,
            onDismiss = { editingIdentity = false },
            onSave = { viewModel.saveIdentity(it) },
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

data class SettingsUiState(
    val identity: Identity? = null,
    val keys: List<StoredKey> = emptyList(),
    val selectedFingerprint: String? = null,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val identityStore = IdentityStore.getInstance(application)
    private val keyStore = KeyStore.getInstance(application)

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
        )
    }

    fun saveIdentity(identity: Identity) {
        identityStore.remember(identity)
        refresh()
    }
}
