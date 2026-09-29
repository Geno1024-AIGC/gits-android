package com.geno1024.ai.gits.ui.update

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geno1024.ai.gits.R
import com.geno1024.ai.gits.update.ApkInstaller
import com.geno1024.ai.gits.update.Updater

/**
 * Where newer builds are found, fetched from, and installed.
 *
 * The source is offered as a choice rather than a fallback because whether GitHub's
 * file hosts are reachable is a property of the network a person is on, not of the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdatePanel(
    viewModel: UpdateViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val noActivityMessage = stringResource(R.string.update_install_no_activity)
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { InstalledRow(state.installed) }

            item {
                ReleaseCard(
                    state = state,
                    onDownload = viewModel::download,
                    onInstall = { apk ->
                        // A tap that finds no activity used to return here and vanish.
                        // It is a genuine failure, and the one that matters most is the
                        // device with nothing able to open an APK, which is exactly when
                        // a person needs to be told rather than left tapping.
                        val activity = context.findActivity()
                        if (activity == null) {
                            viewModel.showMessage(noActivityMessage)
                        } else {
                            ApkInstaller.install(activity, apk, viewModel::showMessage)
                        }
                    },
                    onDismissNotice = viewModel::dismiss,
                )
            }

            item { SourceHeading() }

            items(Updater.SOURCES, key = { it.id }) { source ->
                SourceRow(
                    source = source,
                    selected = source.id == state.source.id,
                    onSelect = { viewModel.setSource(source) },
                )
            }

            item { FeedNote() }
        }

        state.error?.let { message ->
            ErrorBar(message = message, onDismiss = viewModel::dismissError)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    onBack: () -> Unit,
    viewModel: UpdateViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The ViewModel is kept by the navigation entry, so a return visit finds the same
    // one that was built the first time. Checking has to be asked for again from here,
    // or the screen would show a stale answer while looking like it had just looked.
    LaunchedEffect(Unit) { viewModel.recheckIfStale() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.update_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::check, enabled = !state.checking) {
                        Text(stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { padding ->
        UpdatePanel(viewModel = viewModel, modifier = Modifier.padding(padding))
    }
}

@Composable
private fun InstalledRow(installed: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.update_installed_label),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = installed.ifBlank { stringResource(R.string.update_unknown_version) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReleaseCard(
    state: UpdateUiState,
    onDownload: () -> Unit,
    onInstall: (java.io.File) -> Unit,
    onDismissNotice: () -> Unit,
) {
    val release = state.available
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.update_available_label),
            style = MaterialTheme.typography.titleSmall,
        )

        // A spinner alone, replacing the card, is what made a slow check look like a
        // dead screen. The previous answer stays, with the check shown as a side note.
        if (state.checking) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.update_checking),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when {
            release == null && !state.checking -> Text(
                text = stringResource(R.string.update_up_to_date),
                style = MaterialTheme.typography.bodyMedium,
            )

            else -> if (release != null) {
                Text(text = release.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = release.version?.toString().orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.downloading) {
                    val total = state.totalBytes.takeIf { it > 0 } ?: 1L
                    LinearProgressIndicator(
                        progress = { (state.downloadedBytes.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(
                            R.string.update_downloading,
                            percent(state.downloadedBytes, state.totalBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (state.downloaded != null) {
                    Button(onClick = { state.downloaded?.let(onInstall) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.update_install))
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onDownload, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.update_download))
                        }
                        TextButton(onClick = onDismissNotice) {
                            Text(stringResource(R.string.update_not_now))
                        }
                    }
                }
            }
        }

        state.message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SourceHeading() {
    Text(
        text = stringResource(R.string.update_source_heading),
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun SourceRow(source: Updater.Source, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = source.label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun FeedNote() {
    Text(
        text = stringResource(R.string.update_feed_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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

/** A whole percent, or a dash while the size is still unknown. */
private fun percent(done: Long, total: Long): String =
    if (total <= 0) "—" else "${(done * 100 / total)}%"

private fun android.content.Context.findActivity(): Activity? {
    var current: android.content.Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
