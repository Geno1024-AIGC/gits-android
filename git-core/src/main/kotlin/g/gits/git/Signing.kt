package g.gits.git

import g.gits.openpgp.KeyInfo
import g.gits.openpgp.SecretKeyRing
import g.gits.openpgp.SigningSession
import g.gits.openpgp.wipe

import org.eclipse.jgit.lib.GpgConfig
import org.eclipse.jgit.lib.GpgSignature
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.Signer
import org.eclipse.jgit.transport.CredentialsProvider
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Supplies the passphrase for a key, however the app chooses to ask for it: a
 * prompt, a biometric unlock, or a value already held in memory.
 *
 * Implementations may be called off the main thread and more than once per commit,
 * because JGit signs the commit object and a tag separately.
 */
fun interface PassphraseSource {
    /** Returns the passphrase for [key], or throws to abort the signature. */
    fun passphraseFor(key: KeyInfo): CharArray
}

/** Raised when signing was asked for but no usable key could be found. */
class NoSigningKeyException(message: String) : IllegalStateException(message)

/**
 * Signs Git objects with the keys this app owns, instead of a `gpg` binary.
 *
 * JGit's own BouncyCastle signer shells out to a `gpg` executable, which an Android
 * app cannot assume exists and would have to be a second, divergent key store. Doing
 * it in process keeps one key store and lets the passphrase come from wherever the
 * app wants.
 */
class GitsSigner(
    private val keyrings: List<SecretKeyRing>,
    private val passphrases: PassphraseSource,
) : Signer {

    private val lock = ReentrantLock()

    override fun sign(
        repository: Repository,
        config: GpgConfig,
        payload: ByteArray,
        signingIdent: PersonIdent,
        signingKey: String?,
        credentialsProvider: CredentialsProvider?,
    ): GpgSignature {
        val session = sessionFor(signingKey.asKeySpec())
        val armored = session.sign(payload)
        // GpgSignature reads the bytes as US-ASCII, which is what armor is.
        return GpgSignature(armored)
    }

    override fun canLocateSigningKey(
        repository: Repository,
        config: GpgConfig,
        signingIdent: PersonIdent,
        signingKey: String?,
        credentialsProvider: CredentialsProvider?,
    ): Boolean = try {
        selectRing(signingKey.asKeySpec())
        true
    } catch (_: NoSigningKeyException) {
        false
    }

    /**
     * JGit may sign from several threads at once, and the key objects hold the
     * unlocked private key material, so unlocking has to be serialised.
     */
    private fun sessionFor(spec: String?): SigningSession = lock.withLock {
        val ring = selectRing(spec)
        val selected = ring.select(spec)
        val passphrase = passphrases.passphraseFor(ring.infoFor(selected))
        try {
            ring.unlock(spec, passphrase)
        } finally {
            passphrase.wipe()
        }
    }

    /** Picks which stored keyring to sign with, without yet choosing a key inside it. */
    private fun selectRing(spec: String?): SecretKeyRing {
        if (keyrings.isEmpty()) {
            throw NoSigningKeyException("no OpenPGP key is available in this app")
        }
        val withSigners = keyrings.filter { it.signingKeys.isNotEmpty() }
        if (withSigners.isEmpty()) {
            throw NoSigningKeyException("no OpenPGP key in this app can sign")
        }
        if (spec == null) return withSigners.first()

        val needle = spec.trim()
        return withSigners.firstOrNull { ring ->
            ring.signingKeys.any { matches(it, needle) }
        } ?: throw NoSigningKeyException("no signing key matches '$spec'")
    }

    /**
     * JGit passes null whenever `user.signingkey` is unset, which is the normal state
     * of a repository this app just created. Treating that as "use my only key" is
     * what a single-key store should do.
     */
    private fun String?.asKeySpec(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun matches(key: KeyInfo, needle: String): Boolean =
        key.fingerprintHex.equals(needle, ignoreCase = true) ||
            key.keyIdHex.endsWith(needle, ignoreCase = true) ||
            key.userIds.any { it.contains(needle, ignoreCase = true) }
}
