package g.gits.git

/** How a path differs from HEAD, collapsed to what a UI needs to show. */
enum class ChangeKind {
    /** Tracked and modified, not yet staged. */
    MODIFIED,

    /** Present in the working tree and not in the index. */
    UNTRACKED,

    /** Staged for the next commit. */
    STAGED,

    /** Staged for deletion in the next commit. */
    DELETED,

    /** Present in the index and the commit, with content in conflict. */
    CONFLICTED,

    /** Tracked in the index, absent from the working tree. */
    MISSING,
}

/** One path in the working tree together with how it is staged. */
data class WorkingChange(
    val path: String,
    val kind: ChangeKind,
)

/** A snapshot of what a commit would pick up right now. */
data class WorkingStatus(
    val branch: String?,
    val changes: List<WorkingChange>,
    val hasConflicts: Boolean,
) {
    val isClean: Boolean get() = changes.isEmpty()
    val staged: List<WorkingChange> get() = changes.filter { it.kind == ChangeKind.STAGED }
}

/** The result of creating a commit. */
data class CommitResult(
    val id: String,
    val shortId: String,
    val message: String,
    val signedByKeyId: String?,
    val signaturePresent: Boolean,
)

/** One entry of `git log`, flattened for display. */
data class LogEntry(
    val id: String,
    val shortId: String,
    val authorName: String,
    val authorEmail: String,
    val committedAtEpochMillis: Long,
    val subject: String,
    val body: String,
    /** True when the commit carries a signature at all, verified or not. */
    val signaturePresent: Boolean,
    /**
     * The key the signature claims to come from, or null when unsigned.
     *
     * This is a claim read out of the signature packet, not a proof: [signaturePresent]
     * does not imply the key is known or that the maths checks out. Verifying is a
     * separate step this layer deliberately does not fake.
     */
    val signedByKeyId: String?,
)

/** A local branch and where it tracks. */
data class BranchInfo(
    val name: String,
    /** The branch checked out right now, if any. */
    val isCurrent: Boolean,
    val upstreamName: String?,
    val aheadBy: Int,
    val behindBy: Int,
)

/** A configured remote. */
data class RemoteInfo(
    val name: String,
    val uris: List<String>,
    val fetchRefSpecs: List<String>,
)

/** One line of `git blame`, with the commit that last touched it. */
data class BlameLine(
    val lineNumber: Int,
    val commitId: String,
    val authorName: String,
    val authorEmail: String,
    val committedAtEpochMillis: Long,
    val text: String,
)

/** What actually moved, per ref, after a push. */
data class PushRefResult(
    val localRef: String,
    val remoteRef: String,
    val status: String,
    val message: String?,
) {
    /** Git's own wording for "this is fine". */
    val isSuccess: Boolean get() = status == "OK"
}

/** A change in the commit graph that needs saying out loud, as a user would. */
data class GraphChange(
    val ref: String,
    val summary: String,
)

/** A person as Git records them, for `user.name` and `user.email`. */
data class Identity(
    val name: String,
    val email: String,
)
