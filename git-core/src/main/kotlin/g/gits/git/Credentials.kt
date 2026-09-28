package g.gits.git

import org.eclipse.jgit.errors.TransportException
import org.eclipse.jgit.errors.UnsupportedCredentialItem
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.URIish

/** How a remote wants to be authenticated. */
sealed interface Credentials {

    /** Local paths and `file://` need nothing; anything else should carry a secret. */
    data object None : Credentials

    /**
     * An https remote, authenticated with a username and a password or personal
     * access token. GitHub accepts a token as the password with any username, but the
     * conventional pair is kept so other hosts work too.
     *
     * The secret is a [CharArray] rather than a [String] so it can be zeroed once the
     * transport is done with it; a [String] would linger until the GC felt like it.
     */
    class UsernamePassword(
        val username: String,
        val secret: CharArray,
    ) : Credentials {

        /** Overwrites the secret in place. Does not make copies already handed out safe. */
        fun wipe() {
            secret.fill(' ')
        }
    }
}

/**
 * Supplies the credentials for whichever host a remote happens to point at.
 *
 * Keyed by host because that is the boundary a token is good within, and a remote can
 * be pointed somewhere new by a config edit, a mirror, or a redirect. Resolving at the
 * moment of use rather than when the repository was opened is what lets the app ask
 * for a secret only once the user has actually chosen to talk to that host.
 *
 * [forHost] is called off the main thread, and may prompt the user, so it should be
 * cheap to call more than once for the same host and should not prompt repeatedly
 * within one operation.
 */
fun interface CredentialsSource {

    /** What to offer a remote on [host], which is null for a local path. */
    fun forHost(host: String?): Credentials

    companion object {
        /** Never authenticates, which is what a local repository wants. */
        val None: CredentialsSource = CredentialsSource { Credentials.None }

        /** Always offers the same credentials, whatever the host. */
        fun of(credentials: Credentials): CredentialsSource = CredentialsSource { credentials }
    }
}

/**
 * The provider JGit wants for these credentials, or null when none is needed.
 *
 * [hosts] is the set of names the secret is allowed to reach. It is not decoration: a
 * provider that answers every prompt will hand a token to whatever host a redirect or
 * a rewritten remote points at.
 */
internal fun Credentials.toProvider(hosts: Set<String>): CredentialsProvider? = when (this) {
    Credentials.None -> null
    is Credentials.UsernamePassword -> GitsCredentialsProvider(this, hosts)
}

/**
 * The name a credential may be given to for this URI, or null when it names no host.
 *
 * Lower-cased because hosts are not case-sensitive and two spellings of one host
 * should not be two different accounts.
 */
fun URIish.credentialHost(): String? = host?.lowercase()?.takeIf { it.isNotEmpty() }

/** [credentialHost] for a URI written as text, for the cases where only a string is held. */
fun String.toHost(): String? = runCatching { URIish(this).credentialHost() }.getOrNull()

/**
 * Answers credential prompts for one remote, and refuses to answer for any other.
 *
 * Refusing matters: a provider that hands a token to an unexpected host would leak
 * it. The app passes this provider to a single command with a single remote, so an
 * unexpected host means a bug rather than something to paper over.
 */
private class GitsCredentialsProvider(
    private val credentials: Credentials.UsernamePassword,
    private val hosts: Set<String>,
) : CredentialsProvider() {

    override fun isInteractive(): Boolean = false

    override fun supports(vararg items: CredentialItem): Boolean =
        items.all { it is CredentialItem.Username || it is CredentialItem.Password }

    override fun get(uri: URIish?, vararg items: CredentialItem): Boolean {
        if (items.isEmpty()) return true
        val host = uri?.credentialHost()
        if (host == null || host !in hosts) {
            throw TransportException(
                "refusing to give credentials for '${uri?.host ?: "an unnamed host"}'" +
                    "; they were issued for ${hosts.joinToString()}",
            )
        }
        for (item in items) {
            when (item) {
                is CredentialItem.Username -> item.value = credentials.username
                // The transport clears whatever buffer it is given, so it gets a copy;
                // handing over ours would leave the caller holding a secret wiped clean
                // and silently fail on the next push.
                is CredentialItem.Password -> item.setValueNoCopy(credentials.secret.copyOf())
                else -> throw UnsupportedCredentialItem(uri, item.javaClass.name)
            }
        }
        return true
    }
}
