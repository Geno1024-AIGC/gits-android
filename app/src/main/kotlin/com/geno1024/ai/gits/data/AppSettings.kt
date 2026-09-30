package com.geno1024.ai.gits.data

import android.app.Application
import com.geno1024.ai.gits.update.Updater

/**
 * The handful of preferences that are not a secret and not a repository.
 *
 * Reading and writing go through [Settings] so that a test can stand this over a map.
 */
class AppSettings(private val settings: Settings) {

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
     * How the last install ended, kept so the update screen can show it.
     *
     * A session install outlives this process, so the notification is the only thing
     * that survives to report the outcome. A person who taps Install and sees nothing
     * afterwards has no way to tell a refusal from a build that never arrived, so the
     * result is written where the app will read it on the next visit.
     */
    var lastInstallOutcome: String?
        get() = settings.string(KEY_LAST_INSTALL)
        set(value) = settings.write(
            strings = if (value == null) emptyMap() else mapOf(KEY_LAST_INSTALL to value),
            removed = if (value == null) setOf(KEY_LAST_INSTALL) else emptySet(),
        )

    companion object {
        fun of(application: Application) =
            AppSettings(SharedPreferencesSettings(application.getSharedPreferences("gits", Application.MODE_PRIVATE)))

        const val KEY_SOURCE = "update.source"
        const val KEY_DISMISSED = "update.dismissed"
        const val KEY_LAST_INSTALL = "update.lastInstall"
    }
}
