package com.geno1024.ai.gits.data

import android.app.Application
import com.geno1024.ai.gits.update.Updater

/**
 * The handful of preferences that are not a secret and not a repository.
 *
 * Reading and writing go through [Settings] so that a test can stand this over a map.
 */
class AppSettings(private val settings: Settings) {

    /**
     * The branch a new repository starts on.
     *
     * A default rather than a rule: the create dialog still shows the branch and still
     * takes an edit, because someone who makes one repository on `trunk` and the next on
     * `main` should not have to come here in between. What lives here is what the dialog
     * opens with, so the answer is the same on the next visit as it was on the last.
     */
    var initialBranch: String
        get() = settings.string(KEY_INITIAL_BRANCH)?.takeIf { it.isNotBlank() } ?: DEFAULT_BRANCH
        set(value) = settings.write(
            strings = mapOf(KEY_INITIAL_BRANCH to value.trim().ifEmpty { DEFAULT_BRANCH }),
        )

    /** Where builds are downloaded from; the feed itself is always GitHub's. */
    var updateSource: Updater.Source
        get() = Updater.sourceFrom(settings.string(KEY_SOURCE))
        set(value) = settings.write(strings = mapOf(KEY_SOURCE to value.id))

    /**
     * The newest build the user has been told about.
     *
     * Remembering it means an update notice appears once for a build rather than on
     * every visit, and it is what makes "you are up to date" mean something on a
     * channel that never stops moving.
     */
    var dismissedVersion: String?
        get() = settings.string(KEY_DISMISSED)
        set(value) = settings.write(
            strings = if (value == null) emptyMap() else mapOf(KEY_DISMISSED to value),
            removed = if (value == null) setOf(KEY_DISMISSED) else emptySet(),
        )

    /**
     * Which face the app is drawn in, as an id: the system's answer, light, or dark.
     *
     * Held as an id rather than a flag because there are three answers and a flag
     * cannot say "ask the system".
     */
    var themeMode: String
        get() = settings.string(KEY_THEME_MODE)?.takeIf { it.isNotBlank() } ?: DEFAULT_THEME_MODE
        set(value) = settings.write(strings = mapOf(KEY_THEME_MODE to value))

    /** Which prepared scheme the colours come from, as an id the theme knows. */
    var themePreset: String
        get() = settings.string(KEY_THEME_PRESET)?.takeIf { it.isNotBlank() } ?: DEFAULT_THEME_PRESET
        set(value) = settings.write(strings = mapOf(KEY_THEME_PRESET to value))

    /**
     * The colours the custom scheme is mixed from, keyed by the slot each one fills.
     *
     * Packed into a single string rather than given a key apiece because the slots are
     * the theme's business: a preference that had to know them would have to be changed
     * whenever they were. An entry that is not a colour is dropped rather than failing
     * the read, so a value partly written by another build is still read for what it
     * does hold; a slot the scheme never asks for is carried through untouched.
     */
    var themeColors: Map<String, Long>
        get() = settings.string(KEY_THEME_COLORS).orEmpty()
            .split(';')
            .mapNotNull { entry ->
                val parts = entry.split('=', limit = 2)
                if (parts.size != 2 || parts[0].isEmpty()) return@mapNotNull null
                parts[1].toLongOrNull(16)?.let { parts[0] to it }
            }
            .toMap()
        set(value) {
            val packed = value.entries.joinToString(";") { (slot, color) ->
                "$slot=${color.toString(16).uppercase().padStart(8, '0')}"
            }
            settings.write(
                strings = if (value.isEmpty()) emptyMap() else mapOf(KEY_THEME_COLORS to packed),
                removed = if (value.isEmpty()) setOf(KEY_THEME_COLORS) else emptySet(),
            )
        }

    companion object {
        fun of(application: Application) =
            AppSettings(SharedPreferencesSettings(application.getSharedPreferences("gits", Application.MODE_PRIVATE)))

        /** What a repository is given before anyone has said otherwise. */
        const val DEFAULT_BRANCH = "main"

        /** What the theme is before anyone has said otherwise. */
        const val DEFAULT_THEME_MODE = "system"
        const val DEFAULT_THEME_PRESET = "default"

        const val KEY_INITIAL_BRANCH = "repo.initialBranch"
        const val KEY_SOURCE = "update.source"
        const val KEY_DISMISSED = "update.dismissed"
        const val KEY_THEME_MODE = "theme.mode"
        const val KEY_THEME_PRESET = "theme.preset"
        const val KEY_THEME_COLORS = "theme.customColors"
    }
}
