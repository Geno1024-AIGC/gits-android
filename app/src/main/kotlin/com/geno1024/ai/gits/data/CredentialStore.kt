package com.geno1024.ai.gits.data

import android.content.Context
import com.geno1024.ai.gits.git.Credentials
import com.geno1024.ai.gits.git.CredentialsSource
import com.geno1024.ai.gits.git.Questioner
import java.util.Base64

/** A host the app holds a secret for, and the name that goes with it. */
data class StoredAccount(
    val host: String,
    val username: String,
)

/**
 * Tokens for remote hosts, encrypted at rest.
 *
 * Keyed by host because that is the boundary the transport will accept them within,
 * and because a token is issued by one host for one host. A repository may have any
 * number of remotes; the app asks which host it is talking to and uses what was given
 * for that host alone.
 *
 * The secret is stored encrypted and the name beside it, because a name is not a
 * secret and asking for it again would be needless friction.
 */
class CredentialStore(
    storage: Settings,
    private val box: SecretBox,
) {

    private val prefs = storage

    fun accounts(): List<StoredAccount> =
        hosts().map { StoredAccount(host = it, username = prefs.string(userKey(it)).orEmpty()) }

    fun hosts(): Set<String> =
        prefs.strings(HOSTS).orEmpty().filter { it.isNotBlank() }.toSet()

    fun has(host: String?): Boolean = host != null && host in hosts()

    /** The credentials for [host], or none when nothing has been given for it. */
    fun credentialsFor(host: String?): Credentials {
        if (host == null) return Credentials.None
        val sealed = prefs.string(tokenKey(host)) ?: return Credentials.None
        val token = runCatching {
            box.open(Base64.getDecoder().decode(sealed)).decodeToString().toCharArray()
        }.getOrElse {
            // A secret that cannot be unsealed was written by another keystore, or the
            // key was lost. Asking again is the only recovery that leaves the user in
            // control; returning an empty token would fail later and say nothing.
            forget(host)
            return Credentials.None
        }
        return Credentials.UsernamePassword(prefs.string(userKey(host)).orEmpty(), token)
    }

    fun remember(host: String, username: String, token: CharArray) {
        val sealed = Base64.getEncoder().encodeToString(box.seal(token.concatToString().toByteArray()))
        prefs.write(
            strings = mapOf(userKey(host) to username, tokenKey(host) to sealed),
            stringsSet = mapOf(HOSTS to (hosts() + host)),
            removed = emptySet(),
        )
    }

    fun forget(host: String) {
        prefs.write(
            strings = emptyMap(),
            stringsSet = mapOf(HOSTS to (hosts() - host)),
            removed = setOf(userKey(host), tokenKey(host)),
        )
    }

    /** Drops every token, for signing out of everything at once. */
    fun forgetAll() {
        val all = hosts()
        prefs.write(
            strings = emptyMap(),
            stringsSet = mapOf(HOSTS to emptySet()),
            removed = all.flatMap { listOf(userKey(it), tokenKey(it)) }.toSet(),
        )
    }

    /**
     * Reads the store per host, with [questioner] able to answer what is missing.
     *
     * A stored secret needs nobody: it is there and it is handed over. What has no
     * secret to store — an SSH host wanting its key trusted, a key wanting its
     * passphrase — has to ask while the exchange runs, and this is how the exchange
     * gets hold of whoever can ask.
     */
    fun asSource(questioner: Questioner? = null): CredentialsSource = CredentialsSource { host ->
        if (host == null) Credentials.None else credentialsFor(host).asking(questioner)
    }

    /** The stored secret for a host, with somebody beside it to ask when there is none. */
    private fun Credentials.asking(questioner: Questioner?): Credentials = when (this) {
        is Credentials.UsernamePassword -> Credentials.UsernamePassword(username, secret, questioner)
        // Nothing stored, but a host is being talked to: an empty credential that can
        // ask stands for that. Without anyone to ask, nothing is on offer — the same
        // as before.
        Credentials.None -> questioner?.let { Credentials.UsernamePassword("", charArrayOf(), it) } ?: this
    }

    private fun userKey(host: String) = "user:$host"

    private fun tokenKey(host: String) = "token:$host"

    companion object {
        private const val HOSTS = "hosts"

        @Volatile
        private var instance: CredentialStore? = null

        /**
         * One store for the process, so a token entered on one screen is the same token
         * on every other.
         */
        fun getInstance(context: Context): CredentialStore = instance ?: synchronized(this) {
            instance ?: CredentialStore(
                SharedPreferencesSettings(
                    context.applicationContext.getSharedPreferences("credentials", Context.MODE_PRIVATE),
                ),
                AndroidKeystoreBox("gits.credentials.v1"),
            ).also { instance = it }
        }
    }
}
