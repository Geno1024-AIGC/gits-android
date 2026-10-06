package com.geno1024.ai.gits.git

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
        /**
         * Answers what the transport asks while the exchange is under way, or null
         * when nothing may be asked of anyone.
         *
         * An SSH host wants to know whether its key is to be trusted and may want the
         * passphrase of a private key, and neither can be asked about before the
         * connection exists — the fingerprint only exists once the server has sent it.
         */
        val questioner: Questioner? = null,
    ) : Credentials {

        /** Overwrites the secret in place. Does not make copies already handed out safe. */
        fun wipe() {
            secret.fill(' ')
        }
    }
}

/** How a [Question] wants to be answered. */
enum class Answer {

    /** Free text: a username, or anything else said rather than chosen. */
    TEXT,

    /** Text kept off the screen, such as the passphrase of a private key. */
    SECRET,

    /** Yes or no: whether a host key may be trusted, most of all. */
    YES_NO,
}

/**
 * A question put in the middle of an exchange, with everything said beside it.
 *
 * [messages] is what should be read first — a host key and its fingerprints, say —
 * and [prompt] is the question's own wording, when it has any.
 */
class Question(
    val kind: Answer,
    val messages: List<String>,
    val prompt: String?,
)

/**
 * Answers [Question]s from whichever thread the exchange happens to be running on.
 *
 * The exchange waits until there is an answer, so an implementation may put the
 * question to a screen and block until it comes back. Null means the question was
 * dismissed or refused, which the transport reads as a "no".
 */
fun interface Questioner {

    /** What was said: for [Answer.YES_NO], "yes" agrees and anything else does not. */
    fun ask(question: Question): String?
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
 * The host a secret for this address is filed under, whatever form it was written in.
 *
 * [toHost] answers for anything that parses as a remote, and that is the case that
 * decides whether a token works at all: a secret is written down against an address
 * and read back against the name the transport asks for, and if the two spellings
 * differ the secret is stored somewhere nothing will ever look for it.
 *
 * What does not parse is still a host to whoever typed it — a bare `github.com`, a
 * host and a path, a host and a port — so rather than refuse the entry, the parts the
 * transport never asks about are taken off. Empty means no host was there to keep,
 * which is the answer for a local path.
 */
fun String.toCredentialHost(): String {
    val address = trim()
    return address.toHost()
        ?: address.lowercase()
            .substringAfter("://")
            .substringAfterLast('@')
            .substringBefore('/')
            .substringBefore(':')
}

/**
 * Whether this address is spoken over SSH rather than http or a local path.
 *
 * Asked before anything that would rather be told who it is talking to first: an SSH
 * exchange has no token to ask for, and what it does want — whether a host key may be
 * trusted — can only be asked once the server has shown it.
 *
 * An address without a scheme is the scp spelling, `git@example.com:path`, which is
 * SSH by name; a scheme is SSH only when it says so, so `https://git@example.com/o/r`
 * is not.
 */
fun String.isSshAddress(): Boolean {
    val uri = runCatching { URIish(trim()) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase()
    if (scheme != null) return scheme == "ssh" || scheme == "git+ssh"
    return !uri.user.isNullOrEmpty() && !uri.host.isNullOrEmpty()
}

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

    /** Whether anything may be asked at all; a stored secret needs nobody. */
    override fun isInteractive(): Boolean = credentials.questioner != null

    override fun supports(vararg items: CredentialItem): Boolean = items.all { it.answerable }

    private val CredentialItem.answerable: Boolean
        get() = when (this) {
            is CredentialItem.Username, is CredentialItem.Password -> true
            // Served rather than answered: what the question is about is said first.
            is CredentialItem.InformationalMessage -> true
            is CredentialItem.YesNoType, is CredentialItem.StringType -> isInteractive
            else -> false
        }

    override fun get(uri: URIish?, vararg items: CredentialItem): Boolean {
        if (items.isEmpty()) return true
        val host = uri?.credentialHost()
        if (host == null) {
            // JGit's own housekeeping — whether to create known_hosts after a key was
            // accepted — arrives with a path instead of a host. It carries no secret, so
            // it is settled here: the host itself was asked about already, and asking
            // again about the file would make trusting a server a two-step matter.
            if (items.any { it !is CredentialItem.InformationalMessage && it !is CredentialItem.YesNoType }) {
                throw TransportException(
                    "refusing to give credentials for 'an unnamed host'" +
                        "; they were issued for ${hosts.joinToString()}",
                )
            }
            items.filterIsInstance<CredentialItem.YesNoType>().forEach { it.value = true }
            return true
        }
        if (host !in hosts) {
            throw TransportException(
                "refusing to give credentials for '$host'" +
                    "; they were issued for ${hosts.joinToString()}",
            )
        }
        // Said before the question rather than beside it: a host key arrives with its
        // fingerprints in the same answer, and they are the whole reason to say yes.
        val messages = mutableListOf<String>()
        for (item in items) {
            when (item) {
                // The message is filed as the prompt: it has no value of its own.
                is CredentialItem.InformationalMessage -> messages += item.promptText
                is CredentialItem.Username -> {
                    val known = credentials.username
                    if (known.isNotEmpty() || credentials.questioner == null) {
                        item.value = known
                    } else {
                        item.value = ask(messages, item.promptText, Answer.TEXT) ?: return false
                    }
                }
                // The transport clears whatever buffer it is given, so it gets a copy;
                // handing over ours would leave the caller holding a secret wiped clean
                // and silently fail on the next push.
                is CredentialItem.Password -> {
                    val known = credentials.secret
                    if (known.isNotEmpty() || credentials.questioner == null) {
                        item.setValueNoCopy(known.copyOf())
                    } else {
                        val said = ask(messages, item.promptText, Answer.SECRET) ?: return false
                        item.setValueNoCopy(said.toCharArray())
                    }
                }
                is CredentialItem.YesNoType -> {
                    val said = ask(messages, item.promptText, Answer.YES_NO) ?: return false
                    item.value = said.equals("yes", ignoreCase = true)
                }
                is CredentialItem.StringType -> {
                    item.value = ask(messages, item.promptText, Answer.TEXT) ?: return false
                }
                else -> throw UnsupportedCredentialItem(uri, item.javaClass.name)
            }
        }
        return true
    }

    private fun ask(messages: List<String>, prompt: String?, kind: Answer): String? =
        credentials.questioner?.ask(Question(kind, messages.toList(), prompt))
}
