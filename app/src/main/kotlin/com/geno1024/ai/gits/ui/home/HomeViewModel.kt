package com.geno1024.ai.gits.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geno1024.ai.gits.R
import com.geno1024.ai.gits.data.AppSettings
import com.geno1024.ai.gits.data.CredentialStore
import com.geno1024.ai.gits.data.ExternalStorage
import com.geno1024.ai.gits.data.RecentRepositories
import com.geno1024.ai.gits.data.RecentRepository
import com.geno1024.ai.gits.git.Gits
import com.geno1024.ai.gits.git.toHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class HomeUiState(
    val repositories: List<RecentRepository> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    /**
     * The host a clone is waiting to be signed in to, or null when none is.
     *
     * Asking before the exchange rather than letting the server refuse says which
     * host the token is for, and a 401 on its own would say nothing about that.
     */
    val awaitingCredentials: String? = null,
)

/** A clone the app has agreed to make and is now waiting to be allowed to make. */
private class CloneRequest(
    val address: String,
    val directory: File,
    val branch: String?,
    val onCreated: (File) -> Unit,
)

/**
 * Lists the repositories the user has opened, and opens new ones.
 *
 * Opening reads the repository only far enough to fail early on a path that is not one,
 * so a stale entry disappears from the list instead of reappearing on every launch.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val recents = RecentRepositories(application)
    private val settings = AppSettings.of(application)
    private val credentials = CredentialStore.getInstance(application)

    /**
     * A folder this app can always reach, whatever the device's storage rules are.
     *
     * Scoped storage means an arbitrary path typed by hand usually is not one the app
     * may touch, so this is offered as the default rather than left to be guessed.
     */
    val suggestedFolder: String = (
        application.getExternalFilesDir(null) ?: application.filesDir
        ).absolutePath

    private val state = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = state.asStateFlow()

    private var pending: CloneRequest? = null

    init {
        refresh()
    }

    /**
     * The branch the create dialog opens with, read now rather than held.
     *
     * The settings screen can be visited while this view model is still alive, and a
     * value captured at construction would go on offering a branch the user has since
     * changed.
     */
    fun defaultBranch(): String = settings.initialBranch

    fun refresh() {
        state.update { it.copy(repositories = recents.all(), error = null) }
    }

    fun forget(repository: RecentRepository) {
        recents.forget(repository.path)
        refresh()
    }

    /** Returns the opened repository, or null after reporting why it could not be. */
    fun open(path: String, onOpened: (File) -> Unit) = viewModelScope.launch {
        val directory = File(path.trim()).absoluteFile
        state.update { it.copy(busy = true, error = null) }
        val failure = withContext(Dispatchers.IO) {
            if (!directory.isDirectory) {
                return@withContext "\"$directory\" is not a folder"
            }
            if (!File(directory, ".git").exists()) {
                return@withContext "\"$directory\" is not a Git repository"
            }
            null
        }
        if (failure != null) {
            state.update { it.copy(busy = false, error = failure) }
            return@launch
        }
        recents.remember(directory.path, directory.name)
        state.update { it.copy(busy = false, repositories = recents.all()) }
        onOpened(directory)
    }

    /**
     * What stands between [directory] and a repository made in it, or null if nothing does.
     *
     * Asked before the work rather than reported from inside it. Git's own account of a
     * refused write names a lock file and an errno, and neither tells a person anything
     * they can act on. When it is the grant that is missing, the page that grants it is
     * opened on the way through, because a message with no way to answer it is a dead end.
     */
    private suspend fun refusal(directory: File): String? {
        if (withContext(Dispatchers.IO) { ExternalStorage.isWritable(directory) }) return null

        val app = getApplication<Application>()
        val permitted = ExternalStorage.isPermitted(app)
        if (!permitted) {
            withContext(Dispatchers.Main) { ExternalStorage.openPermissionSettings(app) }
        }
        return if (permitted) {
            app.getString(R.string.home_folder_not_writable, directory)
        } else {
            app.getString(R.string.home_storage_permission_needed)
        }
    }

    fun create(directory: File, initialBranch: String, onCreated: (File) -> Unit) {
        viewModelScope.launch {
            val branch = initialBranch.trim().ifEmpty { AppSettings.DEFAULT_BRANCH }
            state.update { it.copy(busy = true, error = null) }
            refusal(directory)?.let { reason ->
                state.update { it.copy(busy = false, error = reason) }
                return@launch
            }
            val failure = withContext(Dispatchers.IO) {
                runCatching { Gits.init(directory, initialBranch = branch).close() }
                    .exceptionOrNull()
                    ?.message
                    ?: if (File(directory, ".git").exists()) null else "no repository appeared"
            }
            if (failure != null) {
                state.update { it.copy(busy = false, error = failure) }
                return@launch
            }
            recents.remember(directory.path, directory.name)
            state.update { it.copy(busy = false, repositories = recents.all()) }
            onCreated(directory)
        }
    }

    /**
     * Copies [address] into [directory], asking who the user is first if it must.
     *
     * An address with no host — a local path, `file://` — is a transfer nothing can
     * authenticate, so the question is never asked for one.
     */
    fun clone(
        address: String,
        directory: File,
        branch: String?,
        onCreated: (File) -> Unit,
    ) {
        val request = CloneRequest(
            address = address.trim(),
            directory = directory,
            branch = branch?.trim()?.takeIf { it.isNotEmpty() },
            onCreated = onCreated,
        )
        if (request.address.isEmpty()) {
            state.update { it.copy(error = "That is not an address.") }
            return
        }
        val host = request.address.toHost()
        if (host != null && !credentials.has(host)) {
            pending = request
            state.update { it.copy(awaitingCredentials = host) }
            return
        }
        cloneNow(request)
    }

    /** Records what the user typed and continues the clone that needed it. */
    fun provideCredentials(username: String, token: CharArray) = viewModelScope.launch {
        val host = state.value.awaitingCredentials ?: return@launch
        val request = pending ?: return@launch
        pending = null
        state.update { it.copy(busy = true, error = null, awaitingCredentials = null) }
        val failure = withContext(Dispatchers.IO) {
            runCatching { credentials.remember(host, username.trim(), token) }.exceptionOrNull()
        }
        if (failure != null) {
            state.update { it.copy(busy = false, error = failure.message) }
            return@launch
        }
        // The token is only wrong if the server says so, which the clone will.
        cloneNow(request)
    }

    fun cancelCredentials() {
        pending = null
        state.update { it.copy(awaitingCredentials = null) }
    }

    private fun cloneNow(request: CloneRequest) {
        viewModelScope.launch {
            state.update { it.copy(busy = true, error = null, awaitingCredentials = null) }
            refusal(request.directory)?.let { reason ->
                state.update { it.copy(busy = false, error = reason) }
                return@launch
            }
            val failure = withContext(Dispatchers.IO) {
                // Checked here rather than left to JGit: picking the folder the user
                // already keeps things in is the natural thing to do, and the refusal
                // that follows has to say so rather than name a transport error.
                val taken = request.directory.list()?.isNotEmpty() == true
                if (taken) {
                    "\"${request.directory}\" already holds files; a clone wants a folder of its own"
                } else {
                    runCatching {
                        Gits.clone(
                            uri = request.address,
                            directory = request.directory,
                            branch = request.branch,
                            credentials = credentials.asSource(),
                        ).close()
                    }.exceptionOrNull()?.message
                        ?: if (File(request.directory, ".git").exists()) {
                            null
                        } else {
                            "no repository appeared"
                        }
                }
            }
            if (failure != null) {
                state.update { it.copy(busy = false, error = failure) }
                return@launch
            }
            val directory = request.directory
            recents.remember(directory.path, directory.name)
            state.update { it.copy(busy = false, repositories = recents.all()) }
            request.onCreated(directory)
        }
    }

    /** Shows a failure the screen found rather than one an operation reported. */
    fun report(message: String) = state.update { it.copy(error = message) }

    fun clearError() = state.update { it.copy(error = null) }
}
