package com.geno1024.ai.gits.ui.ssh

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geno1024.ai.gits.data.Ssh
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SshUiState(
    val keys: List<String> = emptyList(),
    val detected: List<String> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

/**
 * The private keys the SSH transport is offered when it connects.
 *
 * Only the four names the transport tries on its own are ever held, so a key picked
 * from anywhere is filed under the name that makes it get used. What sits in
 * `/sdcard/.ssh` is watched for the same reason a person would look there: a terminal
 * put it there, and this app should offer to bring it across rather than pretend it
 * cannot see it.
 */
class SshViewModel(application: Application) : AndroidViewModel(application) {

    private val state = MutableStateFlow(SshUiState())
    val uiState: StateFlow<SshUiState> = state.asStateFlow()

    init {
        refresh()
    }

    /**
     * Lists what is held, and what in `/sdcard/.ssh` is new or has changed.
     *
     * A key already held byte for byte is not offered again: the offer is for something
     * this app does not yet have, not a standing accusation that copies exist.
     */
    fun refresh() = viewModelScope.launch {
        val context = getApplication<Application>()
        val (keys, detected) = withContext(Dispatchers.IO) {
            val held = Ssh.identities(context)
            val folder = Ssh.directory(context)
            val found = Ssh.externalIdentities(context)
                .filter { external ->
                    val stored = File(folder, external.name)
                    !stored.isFile || !stored.readBytes().contentEquals(external.readBytes())
                }
                .map { it.name }
            held to found
        }
        state.update { it.copy(keys = keys, detected = detected) }
    }

    /** Reads the picked file and files it under the name that makes it get used. */
    fun importFrom(uri: Uri) = run("Could not import the key") {
        val context = getApplication<Application>()
        val bytes = context.contentResolver
            .openInputStream(uri)
            ?.use { it.readBytes() }
            ?: error("That file could not be opened.")
        Ssh.importIdentity(context, bytes, displayName(uri))
    }

    /** Brings across every key in `/sdcard/.ssh` that was offered. */
    fun importDetected() = run("Could not import the keys") {
        val context = getApplication<Application>()
        Ssh.externalIdentities(context).forEach { external ->
            Ssh.importIdentity(context, external.readBytes(), external.name)
        }
    }

    fun remove(name: String) = run("Could not remove the key") { Ssh.removeIdentity(getApplication(), name) }

    fun dismissError() = state.update { it.copy(error = null) }

    fun dismissMessage() = state.update { it.copy(message = null) }

    /** The name the file went by before it was picked, for when the bytes say nothing. */
    private fun displayName(uri: Uri): String? = getApplication<Application>().contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }

    private fun run(prefix: String, block: () -> Unit) = viewModelScope.launch {
        state.update { it.copy(busy = true, error = null) }
        val outcome = withContext(Dispatchers.IO) { runCatching { block() } }
        outcome
            .onSuccess { state.update { it.copy(message = "Done.") } }
            .onFailure { failure ->
                state.update { it.copy(error = failure.message?.let { m -> "$prefix: $m" } ?: prefix) }
            }
        state.update { it.copy(busy = false) }
        refresh()
    }
}
