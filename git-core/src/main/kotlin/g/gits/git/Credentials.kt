package g.gits.git

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

/** The provider JGit wants for these credentials, or null when none is needed. */
internal fun Credentials.toProvider(): CredentialsProvider? = when (this) {
    Credentials.None -> null
    is Credentials.UsernamePassword -> GitsCredentialsProvider(this)
}

/**
 * Answers credential prompts for one remote, and refuses to answer for any other.
 *
 * Refusing matters: a provider that hands a token to an unexpected host would leak
 * it. The app passes this provider to a single command with a single URI, so an
 * unexpected prompt means a bug rather than something to paper over.
 */
private class GitsCredentialsProvider(
    private val credentials: Credentials.UsernamePassword,
) : CredentialsProvider() {

    override fun isInteractive(): Boolean = false

    override fun supports(vararg items: CredentialItem): Boolean =
        items.all { it is CredentialItem.Username || it is CredentialItem.Password }

    override fun get(uri: URIish?, vararg items: CredentialItem): Boolean {
        if (items.isEmpty()) return true
        for (item in items) {
            when (item) {
                is CredentialItem.Username -> item.value = credentials.username
                // setValueNoCopy hands the buffer straight to the transport, which
                // clears it when the exchange ends.
                is CredentialItem.Password -> item.setValueNoCopy(credentials.secret)
                else -> throw UnsupportedCredentialItem(uri, item.javaClass.name)
            }
        }
        return true
    }
}
