package com.geno1024.ai.gits.ui.repo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geno1024.ai.gits.git.BranchInfo
import com.geno1024.ai.gits.git.ChangeKind
import com.geno1024.ai.gits.data.CredentialStore
import com.geno1024.ai.gits.data.KeyStore
import com.geno1024.ai.gits.git.Gits
import com.geno1024.ai.gits.git.Identity
import com.geno1024.ai.gits.git.LogEntry
import com.geno1024.ai.gits.git.PushRefResult
import com.geno1024.ai.gits.git.RemoteInfo
import com.geno1024.ai.gits.git.WorkingChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** A transfer that cannot start until the user supplies a name and token for a host. */
data class CredentialRequest(
    val remote: String,
    val host: String,
    val transfer: Transfer,
)

/** The two operations that talk to a remote and can therefore need a secret. */
enum class Transfer { PUSH, PULL }

/** Which pane the repository screen is showing. */
enum class RepoTab(val label: String) {
    CHANGES("Changes"),
    HISTORY("History"),
    BRANCHES("Branches"),
    REMOTES("Remotes"),
}

data class RepoUiState(
    val path: String = "",
    val name: String = "",
    val branch: String? = null,
    val tab: RepoTab = RepoTab.CHANGES,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val changes: List<WorkingChange> = emptyList(),
    val selected: Set<String> = emptySet(),
    val history: List<LogEntry> = emptyList(),
    val branches: List<BranchInfo> = emptyList(),
    val remotes: List<RemoteInfo> = emptyList(),
    val identity: Identity? = null,
    val signCommits: Boolean = false,
    /** False when the app holds no key that can sign, so the switch can be disabled. */
    val signingAvailable: Boolean = false,
    /** A transfer waiting for the user to name themselves to a host. */
    val awaitingCredentials: CredentialRequest? = null,
) {
    val staged: List<WorkingChange> get() = changes.filter { it.kind == ChangeKind.STAGED }
    val unstaged: List<WorkingChange> get() = changes.filter { it.kind != ChangeKind.STAGED }
    val canCommit: Boolean get() = staged.isNotEmpty() && identity != null
}

/**
 * Drives one open repository.
 *
 * Every git-core call blocks, so all of them run on [Dispatchers.IO] and the handle is
 * opened once and kept. It is closed in [onCleared], which is the one moment the
 * repository is not on screen.
 */
class RepoViewModel(
    application: Application,
    private val path: String,
) : AndroidViewModel(application) {

    private val state = MutableStateFlow(
        RepoUiState(path = path, name = File(path).name),
    )
    val uiState: StateFlow<RepoUiState> = state.asStateFlow()

    private var gits: Gits? = null

    /**
     * Keys the app owns, once a key store exists. Empty means nothing can be signed,
     * and the UI says so rather than offering a switch that would only fail.
     */
    private val keyStore = KeyStore.getInstance(application)
    private val credentials = CredentialStore.getInstance(application)

    init {
        viewModelScope.launch {
            val opened = withContext(Dispatchers.IO) {
                runCatching { Gits.open(File(path), keyStore.signer(), credentials.asSource()) }
            }
            gits = opened.getOrNull()
            state.update {
                it.copy(loading = false, error = opened.exceptionOrNull()?.describe("Could not open"))
            }
            refresh()
        }
    }

    private fun require(): Gits = gits ?: error("The repository is not open.")

    fun selectTab(tab: RepoTab) = state.update { it.copy(tab = tab) }

    fun dismissError() = state.update { it.copy(error = null) }

    fun dismissMessage() = state.update { it.copy(message = null) }

    // ------------------------------------------------------------------ reading

    fun refresh() = viewModelScope.launch {
        val repo = gits ?: return@launch
        val outcome = withContext(Dispatchers.IO) {
            runCatching { snapshot(repo) }
        }
        outcome
            .onSuccess { read ->
                state.update { current ->
                    current.copy(
                        branch = read.branch,
                        changes = read.changes,
                        // Selection is by path, so a path that went away simply stops
                        // being selected instead of naming whatever replaced it.
                        selected = current.selected.intersect(read.paths()),
                        history = read.history,
                        branches = read.branches,
                        remotes = read.remotes,
                        identity = read.identity,
                        signCommits = read.signCommits,
                        signingAvailable = keyStore.hasSigningKey,
                    )
                }
            }
            .onFailure { failure ->
                state.update { it.copy(error = failure.describe("Could not read")) }
            }
    }

    /** Everything the screen shows, read in one go so the panes cannot disagree. */
    private class Read(
        val branch: String?,
        val changes: List<WorkingChange>,
        val history: List<LogEntry>,
        val branches: List<BranchInfo>,
        val remotes: List<RemoteInfo>,
        val identity: Identity?,
        val signCommits: Boolean,
    ) {
        fun paths(): Set<String> = changes.mapTo(mutableSetOf()) { it.path }
    }

    private fun snapshot(repo: Gits): Read {
        val status = repo.status()
        return Read(
            branch = status.branch,
            changes = status.changes,
            history = repo.log(limit = 200),
            branches = repo.branches(),
            remotes = repo.remotes(),
            identity = repo.identity(),
            signCommits = repo.signCommitsByDefault(),
        )
    }

    // ------------------------------------------------------------------ staging

    fun toggleSelected(path: String) = state.update {
        it.copy(selected = if (path in it.selected) it.selected - path else it.selected + path)
    }

    fun selectAll() = state.update { it.copy(selected = it.changes.mapTo(mutableSetOf()) { c -> c.path }) }

    fun clearSelection() = state.update { it.copy(selected = emptySet()) }

    fun stageSelected() = run("Could not stage") { repo ->
        state.value.selected.forEach(repo::add)
    }

    fun unstageSelected() = run("Could not unstage") { repo ->
        state.value.selected.forEach(repo::unstage)
    }

    // ---------------------------------------------------------------- committing

    fun setIdentity(name: String, email: String) = run("Could not save your name") { repo ->
        repo.setIdentity(Identity(name.trim(), email.trim()))
    }

    fun setSignCommits(enabled: Boolean) = run("Could not change the signing setting") { repo ->
        if (enabled && !keyStore.hasSigningKey) {
            error("This app holds no OpenPGP key that can sign yet.")
        }
        repo.setSignCommitsByDefault(enabled)
    }

    /**
     * Commits what is staged.
     *
     * With signing on and no key available the commit is refused rather than quietly
     * made unsigned: a commit that says nothing about its integrity is easy to miss
     * and expensive to discover later.
     */
    fun commit(message: String) {
        val text = message.trim()
        if (text.isEmpty()) {
            state.update { it.copy(error = "A commit needs a message.") }
            return
        }
        viewModelScope.launch {
            state.update { it.copy(busy = true, error = null) }
            // Asked for again here, because the switch and the commit are separate
            // taps and the key store may have changed in between.
            val sign = state.value.signCommits && keyStore.hasSigningKey
            val outcome = withContext(Dispatchers.IO) {
                runCatching { require().commit(text, sign = sign) }
            }
            outcome
                .onSuccess { state.update { it.copy(message = "Committed.") } }
                .onFailure { failure ->
                    state.update { it.copy(error = failure.describe("The commit did not go through")) }
                }
            state.update { it.copy(busy = false) }
            clearSelection()
            refresh()
        }
    }

    // ------------------------------------------------------------------ branches

    fun checkout(name: String) = run("Could not switch to $name") { repo ->
        repo.checkout(name)
    }

    fun createBranch(name: String) = run("Could not create $name") { repo ->
        repo.createBranch(name)
    }

    // ------------------------------------------------------------------- remotes

    /**
     * Pushes the current branch.
     *
     * A rejected ref is not an exception: Git answers with a result per ref, and the
     * usual rejection (not fast-forward) is a sentence the user needs to read, not a
     * stack trace.
     */
    fun push() = transfer(Transfer.PUSH) { it.push() }

    /**
     * Starts a transfer, or asks who the user is first.
     *
     * A host with no token would fail with a 401 that names nothing useful, and the
     * user cannot tell from that what the app wanted. Asking before the exchange says
     * exactly which host is being authenticated to and for what.
     */
    private fun transfer(
        transfer: Transfer,
        block: (Gits) -> Unit,
    ) = viewModelScope.launch {
        val remote = "origin"
        val host = withContext(Dispatchers.IO) { hostOf(remote) }
        if (host != null && !credentials.has(host)) {
            state.update {
                it.copy(awaitingCredentials = CredentialRequest(remote, host, transfer))
            }
            return@launch
        }
        run(transfer.failurePrefix, block)
    }

    /** Records what the user typed and continues the transfer that needed it. */
    fun provideCredentials(username: String, token: CharArray) = viewModelScope.launch {
        val request = state.value.awaitingCredentials ?: return@launch
        state.update { it.copy(busy = true, error = null, awaitingCredentials = null) }
        val failure = withContext(Dispatchers.IO) {
            runCatching { credentials.remember(request.host, username.trim(), token) }
                .exceptionOrNull()
        }
        if (failure != null) {
            state.update {
                it.copy(busy = false, error = failure.describe("Could not save the token"))
            }
            return@launch
        }
        state.update { it.copy(busy = false) }
        // The token is only wrong if the server says so, which the transfer will.
        transfer(request.transfer) { repo ->
            when (request.transfer) {
                Transfer.PUSH -> repo.push(request.remote)
                Transfer.PULL -> repo.pull(request.remote)
            }
        }
    }

    fun cancelCredentials() = state.update { it.copy(awaitingCredentials = null) }

    private fun hostOf(remote: String): String? =
        gits?.remotes()?.firstOrNull { it.name == remote }?.hosts?.firstOrNull()

    private val Transfer.failurePrefix: String
        get() = if (this == Transfer.PUSH) "Could not push" else "Could not pull"

    private fun reportPush(results: List<PushRefResult>) {
        val rejected = results.filterNot { it.isSuccess }
        state.update {
            if (rejected.isEmpty()) {
                it.copy(message = "Pushed ${results.size} ref(s).")
            } else {
                it.copy(
                    error = rejected.joinToString("\n") { failed ->
                        "${failed.localRef}: ${failed.message ?: failed.status}"
                    },
                )
            }
        }
    }

    fun pull() = transfer(Transfer.PULL) { it.pull() }

    fun addRemote(name: String, uri: String) = run("Could not add the remote") { repo ->
        repo.addRemote(name.trim(), uri.trim())
    }

    fun removeRemote(name: String) = run("Could not remove the remote") { repo ->
        repo.removeRemote(name)
    }

    // ------------------------------------------------------------------ plumbing

    /**
     * Runs a blocking git-core call off the main thread and reports whatever it throws.
     *
     * Git's own messages read as sentences ("remote not found", "nothing to commit"),
     * so they are shown as they are rather than replaced by something vaguer.
     */
    private fun run(prefix: String, block: (Gits) -> Unit) = viewModelScope.launch {
        state.update { it.copy(busy = true, error = null) }
        val outcome = withContext(Dispatchers.IO) { runCatching { block(require()) } }
        outcome
            .onSuccess { state.update { it.copy(message = "Done.") } }
            .onFailure { failure -> state.update { it.copy(error = failure.describe(prefix)) } }
        state.update { it.copy(busy = false) }
        refresh()
    }

    override fun onCleared() {
        runCatching { gits?.close() }
        gits = null
    }

    /** Re-reads which keys the app can sign with, after the keys screen changed them. */
    fun refreshKeys() = viewModelScope.launch {
        state.update { it.copy(signingAvailable = keyStore.hasSigningKey) }
    }
}

/** Git's own wording when it has one, which is usually the most useful part. */
private fun Throwable.describe(prefix: String): String =
    message?.takeIf { it.isNotBlank() }?.let { "$prefix: $it" } ?: prefix
