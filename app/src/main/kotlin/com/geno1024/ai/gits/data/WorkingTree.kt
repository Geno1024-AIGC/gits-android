package com.geno1024.ai.gits.data

import java.io.File
import java.io.IOException

/** One thing in the working tree: a file, or a folder that can be opened. */
data class WorkingEntry(
    /** The path from the repository root, which is how Git names the same thing. */
    val path: String,
    val name: String,
    val directory: Boolean,
)

/**
 * Reads and grows the working tree of a repository.
 *
 * A repository's files are ordinary files, so this is ordinary file work rather than
 * anything Git has to be asked. What Git does have a stake in is *where* the work
 * happens: every path is resolved against the repository root and refused if it lands
 * outside, and nothing is allowed under `.git`, because a repository that wrote into its
 * own bookkeeping would stop being one to notice only later.
 *
 * Paths are relative to the root throughout, both here and in the UI, so what the screen
 * shows is the name `git status` would print.
 */
object WorkingTree {

    private const val GIT = ".git"

    /**
     * What is in [directory], folders first and then by name.
     *
     * `.git` is left out: it is the one folder here a person never means to open, and
     * leaving it in would put a way to walk into the repository's own innards one tap
     * away from a list that is otherwise theirs.
     */
    fun entries(root: File, directory: String): List<WorkingEntry> {
        val base = root.canonicalFile
        val folder = inside(base, directory)
        if (touchesGit(base, folder)) {
            throw IllegalArgumentException("\"$directory\" is the repository's own .git folder.")
        }
        if (!folder.isDirectory) {
            throw IllegalArgumentException("\"$directory\" is not a folder in this repository.")
        }

        val children = folder.listFiles() ?: return emptyList()
        return children
            .filter { it.name != GIT }
            .map { WorkingEntry(path = it.toRelativeString(base), name = it.name, directory = it.isDirectory) }
            .sortedWith(compareByDescending<WorkingEntry> { it.directory }.thenBy { it.name.lowercase() })
    }

    /**
     * Makes an empty file, or a folder and any folder above it that is not there yet.
     *
     * Writing `notes/todo.md` into a folder with no `notes` in it is what the person
     * meant, not an error about a missing parent, so the parent is made. What is *not*
     * made is a name that already exists: creating over something would be a way to lose
     * a file that is not even the thing being complained about.
     */
    fun create(root: File, path: String, directory: Boolean) {
        val name = path.trim()
        if (name.isEmpty()) throw IllegalArgumentException("A name is needed.")

        val base = root.canonicalFile
        val target = inside(base, name)
        if (touchesGit(base, target)) {
            throw IllegalArgumentException("\"$name\" would write into the repository's own .git folder.")
        }
        if (target.exists()) throw IllegalArgumentException("\"$name\" already exists.")

        val parent = target.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
            throw IOException("\"$parent\" could not be made.")
        }

        if (directory) {
            if (!target.isDirectory && !target.mkdirs()) throw IOException("\"$target\" could not be made.")
        } else {
            if (!target.createNewFile()) throw IOException("\"$target\" could not be created.")
        }
    }

    /**
     * The path [relative] names inside [base], or a refusal if it names somewhere else.
     *
     * Resolved rather than checked as written: `..` is not a trespass while it is text,
     * only after a filesystem has worked out where it lands.
     */
    private fun inside(base: File, relative: String): File {
        val target = File(base, relative.trim()).canonicalFile
        if (target != base && !target.path.startsWith(base.path + File.separator)) {
            throw IllegalArgumentException("\"$relative\" reaches outside this repository.")
        }
        return target
    }

    /** Whether any folder between the root and [target] is the repository's own. */
    private fun touchesGit(base: File, target: File): Boolean {
        var cursor: File? = target
        while (cursor != null && cursor != base) {
            if (cursor.name == GIT) return true
            cursor = cursor.parentFile
        }
        return false
    }
}
