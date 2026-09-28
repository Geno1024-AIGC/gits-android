package g.gits.android.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import g.gits.android.data.RecentRepositories
import g.gits.android.data.RecentRepository
import g.gits.git.Gits
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
)

/**
 * Lists the repositories the user has opened, and opens new ones.
 *
 * Opening reads the repository only far enough to fail early on a path that is not one,
 * so a stale entry disappears from the list instead of reappearing on every launch.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val recents = RecentRepositories(application)

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

    init {
        refresh()
    }

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

    fun create(path: String, initialBranch: String, onCreated: (File) -> Unit) {
        viewModelScope.launch {
            val directory = File(path.trim()).absoluteFile
            val branch = initialBranch.trim().ifEmpty { "main" }
            state.update { it.copy(busy = true, error = null) }
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

    /** Shows a failure the screen found rather than one an operation reported. */
    fun report(message: String) = state.update { it.copy(error = message) }

    fun clearError() = state.update { it.copy(error = null) }
}
