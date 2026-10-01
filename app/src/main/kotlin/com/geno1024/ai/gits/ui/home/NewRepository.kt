package com.geno1024.ai.gits.ui.home

import java.io.File

/**
 * Where a new repository would be made, or null when nothing names a place yet.
 *
 * The two ways in are a folder the picker chose and a path typed by hand, and they
 * are not symmetric. A picked folder is a place on its own, so an empty name means
 * "here" rather than "nowhere": the whole point of picking it is that the repository
 * goes into it, and demanding a name on top would turn the pick back into a
 * suggestion. A typed name means nothing without a folder to put it under, because
 * the app's working directory is `/` and a bare name would land there, where nothing
 * may be written — so the two only combine, and neither answers for the other.
 *
 * An absolute path is the exception: it has said where on its own and overrides a
 * pick, since someone pasting one has already made the decision the picker would
 * have been for.
 *
 * Pure so the rule can be tested without a picker, a ViewModel, or a device.
 */
fun repositoryTarget(parent: File?, name: String): File? {
    val typed = name.trim()
    if (typed.isEmpty()) return parent
    val absolute = File(typed)
    if (absolute.isAbsolute) return absolute
    return parent?.let { File(it, typed) }
}

/**
 * The folder name a clone of [address] belongs in, or null when it names none.
 *
 * Read off the address the way `git clone` does it, so a clone needs no answer to a
 * question the address already contains. The dialog shows it as a value rather than
 * writing it in behind the user's back: it is what they would have typed anyway, it
 * sits above the line saying where the repository will go, and it stops following the
 * address the moment they write one of their own.
 *
 * Both the path form (`https://host/owner/repo.git`) and the scp form
 * (`git@host:owner/repo.git`) end in the repository, so whichever separator comes
 * last is the one that separates the owner from it.
 */
fun repositoryNameOf(address: String): String? {
    val trimmed = address.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    val last = if (trimmed.contains("://")) {
        // The part after the authority, so an address that is nothing but a host
        // names no repository rather than naming the host itself as one.
        val path = trimmed.substringAfter("://")
        if ('/' !in path) return null
        path.substringAfterLast('/')
    } else {
        // The scp form puts the path after a colon, and may have no slash at all.
        trimmed.substringAfterLast('/').substringAfterLast(':')
    }.removeSuffix(".git").trim()
    return last.takeIf { it.isNotEmpty() && it != "." && it != ".." }
}
