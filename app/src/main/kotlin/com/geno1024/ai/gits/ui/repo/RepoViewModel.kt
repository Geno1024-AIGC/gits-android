package com.geno1024.ai.gits.ui.repo

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geno1024.ai.gits.R
import com.geno1024.ai.gits.git.BranchInfo
import com.geno1024.ai.gits.git.ChangeKind
import com.geno1024.ai.gits.data.CredentialStore
import com.geno1024.ai.gits.data.IdentityStore
import com.geno1024.ai.gits.data.KeyLocked
import com.geno1024.ai.gits.data.KeyStore
import com.geno1024.ai.gits.data.WorkingEntry
import com.geno1024.ai.gits.data.WorkingTree
import com.geno1024.ai.gits.git.Gits
import com.geno1024.ai.gits.git.Identity
import com.geno1024.ai.gits.git.LogEntry
import com.geno1024.ai.gits.git.PushRefResult
import com.geno1024.ai.gits.git.RemoteInfo
import com.geno1024.ai.gits.git.StashEntry
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

/**
 * A signing key that refused to sign, and the commit that is waiting on it.
 *
 * Carried so the passphrase, once given, can finish the very thing that was interrupted
 * instead of leaving the user to write the message out again.
 */
data class UnlockRequest(
    val fingerprintHex: String,
    val message: String,
    /** What the last attempt said, so a wrong passphrase is corrected, not just reported. */
    val note: String? = null,
)

/** Why the commit button cannot be pressed. */
enum class CommitBlock {
    BUSY,
    NOTHING_STAGED,
    NO_MESSAGE,
}

/** Which pane the repository screen is showing. */
enum class RepoTab(@StringRes val label: Int) {
    WORKING_TREE(R.string.repo_tab_working_tree),
    HISTORY(R.string.repo_tab_history),
    BRANCHES(R.string.repo_tab_branches),
    REMOTES(R.string.repo_tab_remotes),
}

/** One file, open for reading. [content] is already refused if it is not text to show. */
data class Viewing(val path: String, val content: String)

/** A rename or a removal waiting on the user's answer. */
sealed interface EntryAction {
    val path: String

    /** [path] under a new name, staying in the folder it is already in. */
    data class Rename(override val path: String, val current: String) : EntryAction

    /** [path], and for a folder everything under it. */
    data class Delete(override val path: String, val directory: Boolean) : EntryAction
}

data class RepoUiState(
    val path: String = "",
    val name: String = "",
    val branch: String? = null,
    val tab: RepoTab = RepoTab.WORKING_TREE,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val changes: List<WorkingChange> = emptyList(),
    val selected: Set<String> = emptySet(),
    val history: List<LogEntry> = emptyList(),
    val branches: List<BranchInfo> = emptyList(),
    val remotes: List<RemoteInfo> = emptyList(),
    val files: List<WorkingEntry> = emptyList(),
    /** The folder being shown, relative to the root; empty means the root. */
    val directory: String = "",
    /** The working-tree list as a flat pile of changes instead of a folder by folder list. */
    val onlyChanged: Boolean = false,
    /** The file open for reading, if any. */
    val viewing: Viewing? = null,
    /** The entry action waiting on the user, if any. */
    val pending: EntryAction? = null,
    /** The stash list, newest first, as last read. */
    val stashes: List<StashEntry> = emptyList(),
    /** Whether the stash list is on screen. */
    val stashesOpen: Boolean = false,
    val identity: Identity? = null,
    val signCommits: Boolean = false,
    /** False when the app holds no key that can sign, so the switch can be disabled. */
    val signingAvailable: Boolean = false,
    /** A transfer waiting for the user to name themselves to a host. */
    val awaitingCredentials: CredentialRequest? = null,
    /** A signing key that needs its passphrase before a commit can be made. */
    val awaitingUnlock: UnlockRequest? = null,
) {
    val staged: List<WorkingChange> get() = changes.filter { it.kind == ChangeKind.STAGED }
    val unstaged: List<WorkingChange> get() = changes.filter { it.kind != ChangeKind.STAGED }
    val canCommit: Boolean get() = staged.isNotEmpty() && identity != null

    /**
     * Which of the ways committing is impossible applies right now, or null when it can.
     *
     * The button's own state only knows yes or no; this is the "no" spelled out, so the
     * dialog can say what is missing rather than presenting a button that does nothing.
     * A missing name and email is reported beside the field it belongs to instead.
     */
    fun commitBlock(message: String): CommitBlock? = when {
        busy -> CommitBlock.BUSY
        staged.isEmpty() -> CommitBlock.NOTHING_STAGED
        message.isBlank() -> CommitBlock.NO_MESSAGE
        else -> null
    }
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
                runCatching {
                    Gits.open(File(path), keyStore.signer(), credentials.asSource())
                        // Commits made by other people are checked against every key
                        // held, not the one chosen for signing.
                        .useVerificationKeys(keyStore.verificationKeys())
                }
            }
            gits = opened.getOrNull()
            gits?.adoptSavedIdentity(IdentityStore.getInstance(application).identity())
            state.update {
                it.copy(loading = false, error = opened.exceptionOrNull()?.describe("Could not open"))
            }
            refresh()
        }
    }

    private fun require(): Gits = gits ?: error("The repository is not open.")

    fun selectTab(tab: RepoTab) {
        // A selection belongs to the list it was made in, and the other panes would
        // happily act on paths they do not show.
        clearSelection()
        state.update { it.copy(tab = tab) }
        // The working tree is the pane people come back to, so it is re-read on arrival
        // like any other pane rather than shown as whatever it last happened to be.
        if (tab == RepoTab.WORKING_TREE) readFiles()
    }

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
        // The folder listing travels with every read, because a change made anywhere
        // (a commit, a checkout, a rename) can have rearranged what is on disk.
        readFiles()
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

    // -------------------------------------------------------------------- files

    /** Shows [directory], which is named from the repository root. */
    fun enter(directory: String) {
        clearSelection()
        state.update { it.copy(directory = directory) }
        readFiles()
    }

    /** Moves up one folder, and stays put at the root where there is nothing above. */
    fun upDirectory() {
        clearSelection()
        val parent = state.value.directory.substringBeforeLast('/', "").trimEnd('/')
        state.update { it.copy(directory = parent) }
        readFiles()
    }

    /**
     * Makes an empty file, or a folder, in the folder being shown.
     *
     * Nothing is staged afterwards. The person asked for a file, not for a commit to be
     * prepared out of it, and a file that appeared in the index unasked would be one they
     * had to notice and undo to keep it out.
     */
    fun createEntry(name: String, directory: Boolean) = viewModelScope.launch {
        val inFolder = state.value.directory
        val relative = if (inFolder.isEmpty()) name else "$inFolder/$name"
        state.update { it.copy(busy = true, error = null) }
        val outcome = withContext(Dispatchers.IO) {
            runCatching { WorkingTree.create(File(path), relative, directory) }
        }
        state.update { it.copy(busy = false) }
        outcome.onFailure { failure ->
            state.update { it.copy(error = failure.describe("Could not create")) }
        }
        // Which re-reads this pane too, so the file shows here and in Changes, where
        // somebody will next look for it.
        refresh()
    }

    private fun readFiles() = viewModelScope.launch {
        val directory = state.value.directory
        val outcome = withContext(Dispatchers.IO) {
            runCatching { WorkingTree.entries(File(path), directory) }
        }
        outcome
            .onSuccess { entries -> state.update { it.copy(files = entries) } }
            .onFailure { failure -> state.update { it.copy(error = failure.describe("Could not list")) } }
    }

    /** Follows a tap: into a folder, or open a file so it can be read. */
    fun openEntry(entry: WorkingEntry) {
        if (entry.directory) enter(entry.path) else view(entry.path)
    }

    /**
     * Reads [target] whole so the file can be shown.
     *
     * Read off the main thread and refused in [WorkingTree] when it is too big or not
     * text at all, so a folder full of photographs stays a list of names rather than
     * turning into a screen of nothing.
     */
    fun view(target: String) = viewModelScope.launch {
        state.update { it.copy(busy = true, error = null) }
        val outcome = withContext(Dispatchers.IO) {
            runCatching { WorkingTree.read(File(path), target) }
        }
        state.update { it.copy(busy = false) }
        outcome
            .onSuccess { text -> state.update { it.copy(viewing = Viewing(target, text)) } }
            .onFailure { failure -> state.update { it.copy(error = failure.describe("Could not open")) } }
    }

    fun closeFile() = state.update { it.copy(viewing = null) }

    /** Chooses the folder-by-folder list, or the pile of everything that has changed. */
    fun setOnlyChanged(onlyChanged: Boolean) = state.update {
        // The two lists hold different paths, so a selection made in one would select
        // nothing at all in the other.
        it.copy(onlyChanged = onlyChanged, selected = emptySet())
    }

    /**
     * Asks for [name] as the new name of [path], which stays in its own folder.
     *
     * Both lists can reach this — the folder list knows the name it is showing, and the
     * change list only has a path — so the name arrives from whichever one was tapped.
     */
    fun requestRename(path: String, name: String) = state.update {
        it.copy(pending = EntryAction.Rename(path, name), error = null)
    }

    /**
     * Asks whether [path] really should go.
     *
     * [directory] says which of the two sentences belongs under it: a folder's answer
     * has to include everything under the folder, or the person confirming has agreed
     * to less than they are about to lose.
     */
    fun requestDelete(path: String, directory: Boolean) = state.update {
        it.copy(pending = EntryAction.Delete(path, directory), error = null)
    }

    fun cancelEntryAction() = state.update { it.copy(pending = null) }

    /**
     * Gives the entry being renamed a new name.
     *
     * The dialog holds what was typed, so this takes it rather than the pending state,
     * which only records which entry the answer belongs to.
     */
    fun renameEntry(name: String) {
        val target = state.value.pending as? EntryAction.Rename ?: return
        state.update { it.copy(pending = null, busy = true, error = null) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { WorkingTree.rename(File(path), target.path, name) }
            }
            state.update { it.copy(busy = false) }
            outcome.onFailure { failure ->
                state.update { it.copy(error = failure.describe("Could not rename")) }
            }
            refresh()
        }
    }

    /** Removes the entry being deleted, taking a folder's contents with it. */
    fun deleteEntry() {
        val target = state.value.pending as? EntryAction.Delete ?: return
        state.update { it.copy(pending = null, busy = true, error = null, viewing = null) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { WorkingTree.delete(File(path), target.path) }
            }
            state.update { it.copy(busy = false) }
            outcome.onFailure { failure ->
                state.update { it.copy(error = failure.describe("Could not delete")) }
            }
            refresh()
        }
    }

    // ------------------------------------------------------------------- stash

    fun openStashes() {
        state.update { it.copy(stashesOpen = true) }
        readStashes()
    }

    fun closeStashes() = state.update { it.copy(stashesOpen = false) }

    private fun readStashes() = viewModelScope.launch {
        val outcome = withContext(Dispatchers.IO) { runCatching { require().stashList() } }
        outcome
            .onSuccess { listed -> state.update { it.copy(stashes = listed) } }
            .onFailure { failure -> state.update { it.copy(error = failure.describe("Could not read")) } }
    }

    fun stash() = stashAction(
        failurePrefix = "Could not stash",
        nothingText = R.string.repo_stash_nothing,
        doneText = R.string.repo_stash_made,
    ) { repo -> repo.stash() != null }

    fun stashPop() = stashAction(
        failurePrefix = "Could not restore",
        nothingText = R.string.repo_stash_none,
        doneText = R.string.repo_stash_restored,
    ) { repo ->
        // Asked which one first, because applying a name that names nothing would come
        // back as git's complaint about a ref rather than as an answer to the tap.
        val newest = repo.stashList().firstOrNull()
        if (newest == null) false else {
            repo.stashPop(newest.ref)
            true
        }
    }

    fun restoreStash(ref: String) = stashAction(
        failurePrefix = "Could not restore",
        nothingText = R.string.repo_stash_none,
        doneText = R.string.repo_stash_restored,
    ) { repo ->
        repo.stashPop(ref)
        true
    }

    fun dropStash(ref: String) = stashAction(
        failurePrefix = "Could not drop",
        nothingText = R.string.repo_stash_none,
        doneText = R.string.repo_stash_dropped,
    ) { repo ->
        repo.stashDrop(ref)
        true
    }

    /**
     * Runs one stash operation and reports which of its answers came back: it happened,
     * there was nothing there to act on, or git refused.
     *
     * Both lists are re-read afterwards, because every one of these three answers
     * rearranges the working tree and the stash list along with it.
     */
    private fun stashAction(
        failurePrefix: String,
        nothingText: Int,
        doneText: Int,
        block: (Gits) -> Boolean,
    ) = viewModelScope.launch {
        state.update { it.copy(busy = true, error = null) }
        val outcome = withContext(Dispatchers.IO) { runCatching { block(require()) } }
        state.update { it.copy(busy = false) }
        outcome
            .onSuccess { happened ->
                state.update {
                    if (happened) it.copy(message = text(doneText)) else it.copy(message = text(nothingText))
                }
            }
            .onFailure { failure -> state.update { it.copy(error = failure.describe(failurePrefix)) } }
        refresh()
        readStashes()
    }

    // ------------------------------------------------------------------ staging

    fun toggleSelected(path: String) = state.update {
        it.copy(selected = if (path in it.selected) it.selected - path else it.selected + path)
    }

    /**
     * Selects everything the list is showing, which is a different set in the flat
     * list of changes than in the folder by folder one.
     */
    fun selectAll() = state.update {
        it.copy(selected = selectable(it.onlyChanged, it.changes, it.files).toSet())
    }

    /**
     * Turns over what is on show, leaving anything selected elsewhere as it was.
     *
     * Inverting the whole repository instead would select paths the list never showed,
     * and the buttons that follow would act on files nobody has looked at.
     */
    fun invertSelection() = state.update {
        it.copy(selected = inverted(it.selected, selectable(it.onlyChanged, it.changes, it.files)))
    }

    fun clearSelection() = state.update { it.copy(selected = emptySet()) }

    fun stageSelected() = run("Could not stage") { repo ->
        state.value.selected.forEach(repo::add)
    }

    fun unstageSelected() = run("Could not unstage") { repo ->
        state.value.selected.forEach(repo::unstage)
    }

    /**
     * Stages the whole working tree: the commit dialog's shortcut past the two steps.
     *
     * The two steps stay, because staging is where a change is looked at before it is
     * committed and removing that would make the app quicker and less trustworthy at
     * once. This is for the case where all of it is meant to go in anyway.
     */
    fun stageAll() = run("Could not stage") { repo ->
        repo.addAll()
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
                    // A locked key is not an error to read, it is a question to answer:
                    // the commit is held and the passphrase asked for, then made.
                    val locked = failure.asKeyLocked()
                    if (locked != null) {
                        state.update { it.copy(awaitingUnlock = UnlockRequest(locked.fingerprintHex, text)) }
                    } else {
                        state.update { it.copy(error = failure.describe("The commit did not go through")) }
                    }
                }
            state.update { it.copy(busy = false) }
            clearSelection()
            refresh()
        }
    }

    /** Asks for the passphrase the signing key wanted, and finishes the held commit. */
    fun unlockSigning(request: UnlockRequest, passphrase: CharArray) = viewModelScope.launch {
        state.update { it.copy(busy = true, error = null, awaitingUnlock = null) }
        val outcome = withContext(Dispatchers.IO) {
            runCatching { keyStore.unlock(request.fingerprintHex, passphrase) }
        }
        state.update { it.copy(busy = false) }
        outcome
            .onSuccess { commit(request.message) }
            .onFailure { failure ->
                // Back to the same dialog with the answer written on it: a mistyped
                // passphrase costs a keystroke, not the commit being written out again.
                state.update {
                    it.copy(awaitingUnlock = request.copy(note = failure.describe("Could not unlock the key")))
                }
            }
    }

    /** Gives up on the held commit, leaving whatever was staged exactly as it was. */
    fun cancelUnlock() = state.update { it.copy(awaitingUnlock = null) }

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

    /** A sentence from the string table, which a ViewModel has to reach through its app. */
    private fun text(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

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

/** What a select-all or an invert has to work on: the list as it is being shown. */
internal fun selectable(onlyChanged: Boolean, changes: List<WorkingChange>, files: List<WorkingEntry>): List<String> =
    if (onlyChanged) changes.map { it.path } else files.map { it.path }

/** [selected], with everything on show turned over and everything off show left alone. */
internal fun inverted(selected: Set<String>, visible: List<String>): Set<String> =
    (selected - visible.toSet()) + visible.filterNot { it in selected }

/**
 * The locked key inside this failure, however many wrappers JGit put around it.
 *
 * The cause chain is followed because a refusal to sign arrives as an internal error
 * that names nothing useful, while what can settle it is a passphrase.
 */
private fun Throwable.asKeyLocked(): KeyLocked? =
    generateSequence(this) { it.cause }.filterIsInstance<KeyLocked>().firstOrNull()
