package com.geno1024.ai.gits.data

import android.content.Context
import com.geno1024.ai.gits.git.Identity

/**
 * The identity this app signs and commits as by default.
 *
 * Git keeps `user.name` and `user.email` in a repository's own config, and an Android
 * app has nowhere to put a global one worth putting: there is no home directory to
 * write `~/.gitconfig` into, and a global config an app made up would be read by no
 * other tool. So the app holds its own answer here and writes it into each repository
 * when that repository is opened, which is the point at which the value is needed and
 * the point at which nothing is overwritten.
 *
 * Deliberately not keyed to a signing key. People commit as one identity and sign with
 * another all the time — a work address with a personal key is the ordinary case — and
 * tying the two together would mean changing your email silently changes which key your
 * commits are signed with, which is a surprise in the direction that gets noticed.
 */
class IdentityStore private constructor(context: Context) {

    private val settings = SharedPreferencesSettings(
        context.getSharedPreferences("gits-identity", Context.MODE_PRIVATE),
    )

    /** The saved identity, or null when none has been given yet. */
    fun identity(): Identity? {
        val name = settings.string(KEY_NAME)?.takeIf { it.isNotBlank() } ?: return null
        val email = settings.string(KEY_EMAIL)?.takeIf { it.isNotBlank() } ?: return null
        return Identity(name, email)
    }

    /** Remembers the identity for repositories opened from now on. */
    fun remember(identity: Identity) {
        settings.write(
            strings = mapOf(
                KEY_NAME to identity.name,
                KEY_EMAIL to identity.email,
            ),
            removed = emptySet(),
        )
    }

    fun forget() {
        settings.write(strings = emptyMap(), removed = setOf(KEY_NAME, KEY_EMAIL))
    }

    companion object {
        private const val KEY_NAME = "user.name"
        private const val KEY_EMAIL = "user.email"

        @Volatile
        private var instance: IdentityStore? = null

        fun getInstance(context: Context): IdentityStore =
            instance ?: synchronized(this) {
                instance ?: IdentityStore(context.applicationContext).also { instance = it }
            }
    }
}
