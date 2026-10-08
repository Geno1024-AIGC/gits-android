package com.geno1024.ai.gits.ui.ssh

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geno1024.ai.gits.R
import com.geno1024.ai.gits.ui.theme.MonoFontFamily

/**
 * The private keys the SSH transport will try, and where to get more.
 *
 * The interesting moment is on the way in: a terminal's keys in `/sdcard/.ssh` are
 * offered for import before anything else happens, because that is where somebody
 * who has cloned over SSH before would expect them to be, and carrying them across
 * is one answer rather than a hunt through a file manager.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshScreen(onBack: () -> Unit, viewModel: SshViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    var offering by remember { mutableStateOf(true) }

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importFrom(uri) }

    state.message?.let { message ->
        LaunchedEffect(message) {
            snackbars.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ssh_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
        // Importing is the whole reason to come here, so the button stays put under
        // a list that is short most of the time.
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { pickFile.launch(arrayOf("*/*")) }) {
                    Text(stringResource(R.string.action_import_key))
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.keys.isEmpty()) {
                Text(
                    text = stringResource(R.string.ssh_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.keys) { key ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = key.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = MonoFontFamily,
                                )
                                // What a person who has used this key would recognise,
                                // in both forms OpenSSH prints: SHA-256, then the older
                                // colon-separated MD5.
                                key.fingerprints?.let { fingerprints ->
                                    Text(
                                        text = fingerprints.sha256,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = MonoFontFamily,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = fingerprints.md5,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = MonoFontFamily,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.remove(key.name) }) {
                                Icon(
                                    painterResource(R.drawable.ic_close),
                                    contentDescription = stringResource(R.string.action_remove_key),
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    val detected = state.detected
    if (offering && detected.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { offering = false },
            title = { Text(stringResource(R.string.ssh_detected_title)) },
            text = {
                Text(
                    buildAnnotatedString {
                        append(stringResource(R.string.ssh_detected_note))
                        append("\n\n")
                        withStyle(SpanStyle(fontFamily = MonoFontFamily)) {
                            append(detected.joinToString())
                        }
                        append("\n\n")
                        append(stringResource(R.string.ssh_replace_note))
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        offering = false
                        viewModel.importDetected()
                    },
                ) { Text(stringResource(R.string.action_import_key)) }
            },
            dismissButton = {
                TextButton(onClick = { offering = false }) { Text(stringResource(R.string.action_cancel)) }
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
