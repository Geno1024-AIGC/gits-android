package com.geno1024.ai.gits.git

import com.geno1024.ai.gits.openpgp.KeyInfo
import com.geno1024.ai.gits.openpgp.SecretKeyRing
import com.geno1024.ai.gits.openpgp.SigningSession
import com.geno1024.ai.gits.openpgp.wipe

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
    /**
     * The key to sign with when the repository does not name one.
     *
     * A repository's own `user.signingkey` still wins, because that is what the person
     * who made the repository asked for. This is for the far more common case of a
     * repository that names nothing, where the choice would otherwise fall to whichever
     * key happened to be first — an answer nobody can see, let alone change. It is not
     * written into the repository's config, since a setting this app holds is not
     * something other tools should inherit as though the user had set it.
     */
    private val fallbackSpec: String? = null,
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
     * of a repository this app just created. Treating that as "use the one I was told
     * to prefer" is what a key store with a choice in it should do.
     *
     * A preference that names a key this app does not hold is treated as no preference
     * at all. It can be stale — a key removed by hand, or a store that lost the file —
     * and a stale setting is no reason to leave the user unable to sign.
     */
    private fun String?.asKeySpec(): String? {
        val asked = this?.trim()?.takeIf { it.isNotEmpty() }
        if (asked != null) return asked
        val preferred = fallbackSpec?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val held = keyrings.any { ring -> ring.signingKeys.any { matches(it, preferred) } }
        return preferred.takeIf { held }
    }

    private fun matches(key: KeyInfo, needle: String): Boolean =
        key.fingerprintHex.equals(needle, ignoreCase = true) ||
            key.keyIdHex.endsWith(needle, ignoreCase = true) ||
            key.userIds.any { it.contains(needle, ignoreCase = true) }
}
