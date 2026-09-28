package com.geno1024.ai.gits.git

import com.geno1024.ai.gits.openpgp.DetachedSignatures
import com.geno1024.ai.gits.openpgp.SignatureCheck
import org.bouncycastle.openpgp.PGPPublicKey
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.blame.BlameResult
import org.eclipse.jgit.lib.BranchConfig
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.lib.Config
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.NullProgressMonitor
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteConfig
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.revwalk.RevCommit
import java.io.Closeable
import java.io.File

/**
 * A local repository and the operations the app exposes on it.
 *
 * Every method does blocking I/O, so callers must keep them off the main thread. That
 * is deliberate: JGit's command builders carry per-command state, so making them safe
 * to share would mean a lock around every call, and hiding that behind a coroutine
 * wrapper would only push the decision to the call site.
 */
class Gits private constructor(
    private val git: Git,
    private val signer: GitsSigner?,
    private var credentials: CredentialsSource,
) : Closeable {

    private val repository get() = git.repository

    /** The working tree directory, or the git directory for a bare repository. */
    val root: File
        get() = if (repository.isBare) repository.directory else repository.workTree!!

    val isBare: Boolean get() = repository.isBare

    val directory: File get() = repository.directory

    // ---------------------------------------------------------------- identity

    /** The configured `user.name` and `user.email`, or null when either is missing. */
    fun identity(): Identity? {
        val config = repository.config
        val name = config.getString("user", null, "name") ?: return null
        val email = config.getString("user", null, "email") ?: return null
        return Identity(name, email)
    }

    /**
     * Writes `user.name` and `user.email` into this repository's own config.
     *
     * Repository level, not global: an Android app has no global git config worth
     * writing, and one repository must not silently reconfigure another.
     */
    fun setIdentity(identity: Identity) {
        withConfig { config ->
            config.setString("user", null, "name", identity.name)
            config.setString("user", null, "email", identity.email)
        }
    }

    /** Sets the key `commit -S` should use, in `user.signingkey` form. */
    fun setSigningKey(spec: String?) {
        withConfig { config ->
            if (spec == null) {
                config.unset("user", null, "signingkey")
            } else {
                config.setString("user", null, "signingkey", spec)
            }
        }
    }

    fun signingKey(): String? = repository.config.getString("user", null, "signingkey")

    /** Whether GitHub and friends should trust signatures made by this repository. */
    fun signCommitsByDefault(): Boolean =
        repository.config.getBoolean("commit", null, "gpgsign", false)

    fun setSignCommitsByDefault(enabled: Boolean) {
        withConfig { config -> config.setBoolean("commit", null, "gpgsign", enabled) }
    }

    // ------------------------------------------------------------------ status

    fun status(): WorkingStatus {
        val status = git.status().call()
        val changes = buildList {
            status.added.mapTo(this) { WorkingChange(it, ChangeKind.STAGED) }
            status.changed.mapTo(this) { WorkingChange(it, ChangeKind.STAGED) }
            status.removed.mapTo(this) { WorkingChange(it, ChangeKind.DELETED) }
            status.missing.mapTo(this) { WorkingChange(it, ChangeKind.MISSING) }
            status.conflicting.mapTo(this) { WorkingChange(it, ChangeKind.CONFLICTED) }
            status.modified.mapTo(this) { WorkingChange(it, ChangeKind.MODIFIED) }
            // untrackedFolders restates paths untracked already covers, so it is skipped
            status.untracked.mapTo(this) { WorkingChange(it, ChangeKind.UNTRACKED) }
        }.sortedBy { it.path }

        return WorkingStatus(
            branch = currentBranch(),
            changes = changes,
            hasConflicts = status.conflicting.isNotEmpty(),
        )
    }

    /** Stages one path, which may be a glob. */
    fun add(pattern: String) {
        git.add().addFilepattern(pattern).call()
    }

    /** Stages every change, including deletions, like `git add -A`. */
    fun addAll() {
        git.add().setAll(true).call()
    }

    /** Moves a path back out of the index, leaving the working tree alone. */
    fun unstage(pattern: String) {
        git.reset().addPath(pattern).call()
    }

    // ------------------------------------------------------------------ commit

    /**
     * Records a commit from whatever is staged.
     *
     * Asking to sign without having a key throws [NoSigningKeyException] rather than
     * quietly writing an unsigned commit that the user believes is signed.
     */
    fun commit(
        message: String,
        author: Identity = identity() ?: throw IllegalStateException(
            "this repository has no user.name and user.email configured",
        ),
        sign: Boolean = false,
        signingKey: String? = null,
    ): CommitResult {
        val command = git.commit()
            .setMessage(message)
            .setAuthor(PersonIdent(author.name, author.email))

        if (sign) {
            val active = signer ?: throw NoSigningKeyException(
                "this repository was opened without a signing key",
            )
            command.setSign(true).setSigner(active)
            signingKey?.let { command.setSigningKey(it) }
        }

        val commit = command.call()
        val issuer = commit.rawGpgSignature?.let(DetachedSignatures::issuerOf)
        return CommitResult(
            id = commit.name,
            shortId = commit.abbreviate(7).name(),
            message = commit.fullMessage.trimEnd(),
            signedByKeyId = issuer?.keyIdHex,
            signaturePresent = commit.rawGpgSignature != null,
            signatureCheck = verify(commit),
        )
    }

    /**
     * The newest [limit] commits, optionally narrowed to one path.
     *
     * A repository that has never been committed to has no HEAD for `git log` to
     * start from, which is the normal state of a folder the user just made. That is
     * an empty history, not a failure.
     */
    fun log(limit: Int = 100, path: String? = null): List<LogEntry> {
        if (repository.resolve(Constants.HEAD) == null) return emptyList()
        val command = git.log().setMaxCount(limit)
        path?.let { command.addPath(it) }
        return command.call().map { commit ->
            val issuer = commit.rawGpgSignature?.let(DetachedSignatures::issuerOf)
            LogEntry(
                id = commit.name,
                shortId = commit.abbreviate(7).name(),
                authorName = commit.authorIdent.name,
                authorEmail = commit.authorIdent.emailAddress,
                committedAtEpochMillis = commit.commitTime * 1000L,
                subject = commit.shortMessage,
                body = commit.fullMessage.removePrefix(commit.shortMessage).trim('\n'),
                signaturePresent = commit.rawGpgSignature != null,
                signedByKeyId = issuer?.keyIdHex,
                signatureCheck = verify(commit),
            )
        }
    }

    /**
     * Checks a commit's signature against the public keys this app holds.
     *
     * Returns null when the commit is unsigned, or when no keys were offered at all —
     * a caller showing "could not check" where the honest answer is "there was nothing
     * to check with" would be telling a person their commit is suspect.
     */
    private fun verify(commit: RevCommit): SignatureCheck? {
        val signature = commit.rawGpgSignature ?: return null
        val keys = verificationKeys
        if (keys.isEmpty()) return null
        val payload = SignedBytes.of(commit) ?: return null
        return DetachedSignatures.verify(signature, payload, keys)
    }

    /**
     * The public keys available to check signatures with.
     *
     * Supplied by whoever opened the repository. Defaults to none, so a repository
     * opened without keys reports no verdict rather than a false one.
     */
    var verificationKeys: List<PGPPublicKey> = emptyList()
        private set

    /** Offers public keys for checking signatures made by other people. */
    fun useVerificationKeys(keys: Iterable<PGPPublicKey>): Gits {
        verificationKeys = keys.toList()
        return this
    }

    /**
     * Replaces where credentials come from.
     *
     * Useful when a repository was opened before the user had told the app about a
     * host, and after they have. Returns this handle so a caller can write
     * `gits.withCredentialsSource(...)` in place of a re-open.
     */
    fun withCredentialsSource(source: CredentialsSource): Gits = apply {
        credentials = source
    }

    // ---------------------------------------------------------------- branches

    fun branches(): List<BranchInfo> {
        val current = currentBranch()
        return git.branchList()
            .setListMode(ListBranchCommand.ListMode.ALL)
            .call()
            .map { ref ->
                val name = shortName(ref.name)
                // Absent for a branch with no upstream, which is most of them.
                val status = BranchTrackingStatus.of(repository, name)
                BranchInfo(
                    name = name,
                    isCurrent = name == current,
                    upstreamName = status?.remoteTrackingBranch?.let(::shortName),
                    aheadBy = status?.aheadCount ?: 0,
                    behindBy = status?.behindCount ?: 0,
                )
            }
            .sortedWith(compareByDescending<BranchInfo> { it.isCurrent }.thenBy { it.name })
    }

    fun currentBranch(): String? = repository.branch?.let(::shortName)

    fun createBranch(name: String, startPoint: String? = null): String {
        val command = git.branchCreate().setName(name)
        startPoint?.let { command.setStartPoint(it) }
        return shortName(command.call().name)
    }

    fun checkout(name: String, create: Boolean = false, force: Boolean = false): String =
        shortName(
            git.checkout()
                .setName(name)
                .setCreateBranch(create)
                .setForced(force)
                .call()
                .name,
        )

    fun renameBranch(from: String, to: String): String =
        shortName(git.branchRename().setOldName(from).setNewName(to).call().name)

    fun deleteBranch(name: String, force: Boolean = false): List<String> =
        git.branchDelete().setBranchNames(name).setForce(force).call()

    // ----------------------------------------------------------------- remotes

    fun remotes(): List<RemoteInfo> = git.remoteList().call().map { config ->
        RemoteInfo(
            name = config.name,
            uris = config.getURIs().map { it.toString() },
            fetchRefSpecs = config.fetchRefSpecs.map { it.toString() },
        )
    }

    fun addRemote(name: String, uri: String) {
        git.remoteAdd().setName(name).setUri(URIish(uri)).call()
    }

    fun setRemoteUrl(name: String, uri: String) {
        git.remoteSetUrl().setRemoteName(name).setRemoteUri(URIish(uri)).call()
    }

    fun removeRemote(name: String) {
        git.remoteRemove().setRemoteName(name).call()
    }

    // --------------------------------------------------------------- transfers

    /**
     * The names credentials for [remote] may be given to, taken from what the remote
     * actually points at rather than from what the caller intended, so that a remote
     * rewritten underneath us cannot redirect a token somewhere new.
     */
    private fun credentialHosts(remote: String): Set<String> =
        RemoteConfig(repository.config, remote).getURIs()
            .mapNotNull { it.credentialHost() }
            .toSet()

    /**
     * Credentials for [remote], resolved now from the host it points at.
     *
     * A remote with several fetch URLs is asked about its first one. That case is rare
     * and the provider still refuses every host but the ones the remote lists, so a
     * token cannot escape to a host the repository was not already configured for.
     */
    private fun providerFor(remote: String): CredentialsProvider? {
        val hosts = credentialHosts(remote)
        // A local path has nothing to authenticate, so the source is not consulted
        // at all. A source that prompts should never be woken for a local transfer.
        if (hosts.isEmpty()) return null
        return credentials.forHost(hosts.first()).toProvider(hosts)
    }

    /** Updates remote tracking refs without touching the working tree. */
    fun fetch(remote: String = "origin", prune: Boolean = true): List<GraphChange> {
        val command = git.fetch()
            .setRemote(remote)
            .setRemoveDeletedRefs(prune)
            .setProgressMonitor(NullProgressMonitor.INSTANCE)
        providerFor(remote)?.let { command.setCredentialsProvider(it) }
        return command.call().trackingRefUpdates.map { update ->
            GraphChange(
                ref = shortName(update.localName),
                summary = update.newObjectId.abbreviate(7).name(),
            )
        }
    }

    /**
     * Fetches and integrates.
     *
     * Rebase is the default: this app opens no merge editor, so a merge would land on
     * the user with conflicts and no way to resolve them.
     */
    fun pull(
        remote: String = "origin",
        branch: String? = null,
        rebase: Boolean = true,
    ): GraphChange? {
        val command = git.pull()
            .setRemote(remote)
            .setRebase(rebase)
            .setProgressMonitor(NullProgressMonitor.INSTANCE)
        branch?.let { command.setRemoteBranchName(it) }
        providerFor(remote)?.let { command.setCredentialsProvider(it) }
        val result = command.call()
        if (!result.isSuccessful) {
            throw TransportException("pull did not complete: ${result.fetchResult}")
        }
        return currentBranch()?.let { branch ->
            GraphChange(branch, repository.resolve(Constants.HEAD).abbreviate(7).name())
        }
    }

    /**
     * Publishes refs.
     *
     * With no [refspecs] the current branch goes to its configured upstream, or to a
     * branch of the same name when it has no upstream yet, which is what a first push
     * of a fresh repository needs.
     */
    fun push(
        remote: String = "origin",
        refspecs: List<String> = emptyList(),
        force: Boolean = false,
        pushTags: Boolean = false,
    ): List<PushRefResult> {
        val command = git.push()
            .setRemote(remote)
            .setForce(force)
            .setProgressMonitor(NullProgressMonitor.INSTANCE)
        if (pushTags) command.setPushTags()
        providerFor(remote)?.let { command.setCredentialsProvider(it) }

        if (refspecs.isEmpty()) {
            command.setRefSpecs(listOf(RefSpec("${upstreamRef()}:${upstreamRef()}")))
        } else {
            command.setRefSpecs(refspecs.map(::RefSpec))
        }

        return command.call().flatMap { it.remoteUpdates }.map { update ->
            PushRefResult(
                localRef = shortName(update.srcRef),
                remoteRef = shortName(update.remoteName),
                status = update.status.name,
                message = update.message,
            )
        }
    }

    /** Where the current branch should land, given what it already tracks. */
    private fun upstreamRef(): String {
        val branch = currentBranch() ?: throw IllegalStateException(
            "HEAD is detached, so there is no branch to push; name the refs to push",
        )
        val tracked = BranchConfig(repository.config, branch).merge
        return tracked ?: Constants.R_HEADS + branch
    }

    // ------------------------------------------------------------------- blame

    /** Attributes each line of [path] to the commit that last changed it. */
    fun blame(path: String): List<BlameLine> = readBlame(
        git.blame()
            .setFilePath(path)
            .setFollowFileRenames(true)
            .call(),
    )

    private fun readBlame(result: BlameResult): List<BlameLine> {
        // BlameCommand already runs the walk to the end and closes the generator, so
        // the streaming computeNext/lastLength pair finds nothing left to do and blame
        // came back empty. computeAll is cheap here and leaves the result readable
        // whether or not it arrived finished.
        result.computeAll()
        val contents = result.resultContents
        return (0 until contents.size()).map { line ->
            val author = result.getSourceAuthor(line)
            val commit = result.getSourceCommit(line)
            BlameLine(
                lineNumber = line + 1,
                commitId = commit.name,
                authorName = author.name,
                authorEmail = author.emailAddress,
                committedAtEpochMillis = commit.commitTime * 1000L,
                text = contents.getString(line),
            )
        }
    }

    // ------------------------------------------------------------------- close

    override fun close() {
        repository.close()
    }

    private fun withConfig(block: (Config) -> Unit) {
        val config = repository.config
        try {
            block(config)
            config.save()
        } catch (e: RuntimeException) {
            config.load()
            throw e
        }
    }

    private fun shortName(refName: String): String = refName
        .removePrefix(Constants.R_HEADS)
        .removePrefix(Constants.R_REMOTES)

    companion object {
        /**
         * Creates a new repository whose first branch is [initialBranch].
         *
         * [signer] is taken here rather than later because signing is the point of the
         * app, and a handle that cannot sign would only be noticed at commit time.
         */
        fun init(
            directory: File,
            initialBranch: String = "main",
            signer: GitsSigner? = null,
        ): Gits = Gits(
            Git.init()
                .setDirectory(directory)
                .setInitialBranch(initialBranch)
                .call(),
            signer = signer,
            credentials = CredentialsSource.None,
        )

        /** Opens an existing repository at [directory]. */
        fun open(
            directory: File,
            signer: GitsSigner? = null,
            credentials: CredentialsSource = CredentialsSource.None,
        ): Gits = Gits(Git.open(directory), signer, credentials)

        /**
         * Clones [uri] into [directory] and returns an open handle.
         *
         * A failure part way through leaves the partial directory for the user to
         * delete: this method has no business removing files it may not have created.
         */
        fun clone(
            uri: String,
            directory: File,
            branch: String? = null,
            credentials: CredentialsSource = CredentialsSource.None,
            signer: GitsSigner? = null,
        ): Gits {
            val host = uri.toHost()
            val provider = if (host == null) {
                null
            } else {
                credentials.forHost(host).toProvider(setOf(host))
            }
            val command = Git.cloneRepository()
                .setURI(uri)
                .setDirectory(directory)
                .setProgressMonitor(NullProgressMonitor.INSTANCE)
            branch?.let { command.setBranch(it) }
            if (provider != null) command.setCredentialsProvider(provider)
            return Gits(command.call(), signer, credentials)
        }
    }
}
