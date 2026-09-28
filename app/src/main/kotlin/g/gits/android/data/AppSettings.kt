package g.gits.android.data

import android.app.Application
import g.gits.android.update.Updater

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

    companion object {
        fun of(application: Application) =
            AppSettings(SharedPreferencesSettings(application.getSharedPreferences("gits", Application.MODE_PRIVATE)))

        const val KEY_SOURCE = "update.source"
        const val KEY_DISMISSED = "update.dismissed"
    }
}
