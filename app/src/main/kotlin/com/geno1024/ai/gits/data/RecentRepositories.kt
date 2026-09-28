package com.geno1024.ai.gits.data

import android.content.Context
import java.io.File

/** A repository the user has opened, as listed on the home screen. */
data class RecentRepository(
    val path: String,
    val name: String,
    val lastOpenedAtMillis: Long,
)

/**
 * Remembers which repositories the user has opened.
 *
 * Paths are all this needs. A repository on Android is a directory the app can already
 * reach, and re-reading it is what makes the list trustworthy after the user deletes or
 * moves something, so nothing is cached beyond the name shown next to the path.
 */
class RecentRepositories(context: Context) {

    private val prefs = context.getSharedPreferences("recent-repositories", Context.MODE_PRIVATE)

    /** Most recently opened first. */
    fun all(): List<RecentRepository> =
        paths()
            .map { path ->
                RecentRepository(
                    path = path,
                    name = prefs.getString(nameKey(path), null) ?: File(path).name,
                    lastOpenedAtMillis = prefs.getLong(openedKey(path), 0L),
                )
            }
            .sortedByDescending { it.lastOpenedAtMillis }

    fun remember(path: String, name: String) {
        prefs.edit()
            .putString(nameKey(path), name)
            .putLong(openedKey(path), System.currentTimeMillis())
            // Reopening moves a repository to the front, so the list is ordered by
            // recency rather than by when each key happened to be written.
            .putStringSet(PATHS, (paths() - path + path).toSet())
            .apply()
    }

    fun forget(path: String) {
        prefs.edit()
            .remove(nameKey(path))
            .remove(openedKey(path))
            .putStringSet(PATHS, (paths() - path).toSet())
            .apply()
    }

    private fun paths(): Set<String> =
        prefs.getStringSet(PATHS, emptySet()).orEmpty().filter { it.isNotBlank() }.toSet()

    private fun nameKey(path: String) = "name:$path"

    private fun openedKey(path: String) = "opened:$path"

    private companion object {
        const val PATHS = "paths"
    }
}
