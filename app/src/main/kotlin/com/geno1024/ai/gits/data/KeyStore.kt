package com.geno1024.ai.gits.data

import android.content.Context
import com.geno1024.ai.gits.git.GitsSigner
import com.geno1024.ai.gits.git.PassphraseSource
import com.geno1024.ai.gits.openpgp.KeyAlgorithm
import com.geno1024.ai.gits.openpgp.KeyGeneration
import com.geno1024.ai.gits.openpgp.KeyInfo
import com.geno1024.ai.gits.openpgp.SecretKeyRing
import com.geno1024.ai.gits.openpgp.armored
import com.geno1024.ai.gits.openpgp.armoredPublicKey
import com.geno1024.ai.gits.openpgp.wipe
import org.bouncycastle.openpgp.PGPPublicKey
import java.io.File

/** A key the app holds, as the settings screen lists it. */
data class StoredKey(
    val fingerprintHex: String,
    val userId: String,
    /** Null for an algorithm this app does not generate, which an import may bring. */
    val algorithm: KeyAlgorithm?,
    val canSign: Boolean,
    /** False for a key imported without one, which matters before it is copied anywhere. */
    val isPassphraseProtected: Boolean = true,
)

/**
 * The keys this app signs with, kept as files in its own storage.
 *
 * Keys are stored armored and unlocked from a passphrase the app holds only for the
 * session. Keeping a passphrase on disk would make the key no better protected than the
 * file next to it, so the honest arrangement is that the app has keys only while it is
 * open and the user has unlocked them.
 *
 * Every method blocks and expects to be called off the main thread.
 */
class KeyStore private constructor(context: Context) {

    private val directory = File(context.filesDir, "keys").also { it.mkdirs() }
    private val settings = SharedPreferencesSettings(
        context.getSharedPreferences("gits-keys", Context.MODE_PRIVATE),
    )

    /**
     * Passphrases for the session, keyed by fingerprint. A key with no entry here is
     * unusable, which is the point: the user has to unlock it deliberately.
     */
    private val unlocked = mutableMapOf<String, CharArray>()

    private var cached: List<SecretKeyRing>? = null

    /** True when at least one stored key can actually produce a signature. */
    val hasSigningKey: Boolean
        get() = keyrings().any { it.signingKeys.isNotEmpty() }

    fun keys(): List<StoredKey> = keyrings().flatMap { ring ->
        ring.keys.map { key ->
            StoredKey(
                fingerprintHex = key.fingerprintHex,
                userId = key.primaryUserId ?: ring.primaryUserIds.firstOrNull().orEmpty(),
                algorithm = KeyAlgorithm.of(key.algorithm),
                canSign = key.isSigningKey,
                isPassphraseProtected = key.isPassphraseProtected,
            )
        }
    }

    /**
     * The key commits are signed with when the repository names none.
     *
     * Kept as a fingerprint rather than left to the order keys happen to be read in:
     * with two keys, "whichever comes first" is a choice the user cannot see or undo.
     */
    var selected: String?
        get() = settings.string(KEY_SELECTED)
        set(value) = settings.write(
            strings = if (value == null) emptyMap() else mapOf(KEY_SELECTED to value),
            removed = if (value == null) setOf(KEY_SELECTED) else emptySet(),
        )

    /**
     * A signer over every stored key, or null when there is nothing to sign with.
     *
     * The keyrings are read once and kept: parsing a keyring is slow enough to notice
     * on every commit, and the set of keys changes only when the user changes it.
     */
    fun signer(): GitsSigner? {
        val keyrings = keyrings()
        if (keyrings.none { it.signingKeys.isNotEmpty() }) return null
        return GitsSigner(
            keyrings = keyrings,
            fallbackSpec = selected,
            passphrases = PassphraseSource { key ->
                unlocked[key.fingerprintHex]
                    ?: throw IllegalStateException(
                        "The key ${key.fingerprintAbbreviated} is locked; unlock it to sign.",
                    )
            },
        )
    }

    /** Adds a key generated here, and remembers its passphrase for the session. */
    fun generate(userId: String, algorithm: KeyAlgorithm, passphrase: CharArray): StoredKey {
        val ring = SecretKeyRing(KeyGeneration.generate(userId, algorithm, passphrase))
        write(ring)
        unlocked[ring.keys.first().fingerprintHex] = passphrase.copyOf()
        return ring.keys.first().toStored(ring)
    }

    /** Adds keys from an armored export, as produced by `gpg --armor --export`. */
    fun importArmored(encoded: ByteArray, passphrase: CharArray): List<StoredKey> {
        val rings = SecretKeyRing.read(encoded)
        if (rings.isEmpty()) throw IllegalArgumentException("That file contains no OpenPGP keys.")
        rings.forEach { write(it) }
        // Unlocking here is the honest test of the passphrase: an import that is
        // accepted but cannot later be unlocked is a key the user thinks they have.
        rings.forEach { ring ->
            ring.signingKeys.firstOrNull()?.let { key ->
                runCatching { ring.unlock(null, passphrase) }
                    .onSuccess { unlocked[key.fingerprintHex] = passphrase.copyOf() }
            }
        }
        return rings.flatMap { ring -> ring.keys.map { it.toStored(ring) } }
    }

    /**
     * The armored public key, as `gpg --armor --export` writes it.
     *
     * This is the file that lets anyone else check the signatures this key makes, and
     * it holds nothing secret.
     */
    fun publicKeyOf(fingerprintHex: String): ByteArray = ringFor(fingerprintHex).armoredPublicKey()

    /**
     * Every public key held, for checking commits signed by other people.
     *
     * All of them, not just the selected one: the selected key is for making signatures,
     * and holding a key says nothing about who made any given commit. A key that is
     * expired now may well have signed something while it was current, so nothing here
     * is filtered on validity — that is judged per signature, against the moment it was
     * made.
     */
    fun verificationKeys(): List<PGPPublicKey> = keyrings().flatMap { it.publicKeys }

    /**
     * The armored secret key, for keeping somewhere else.
     *
     * Handed back exactly as it is held, which means a key that was imported without a
     * passphrase leaves without one too. The caller is told whether that is the case
     * and is expected to say so.
     */
    fun secretKeyOf(fingerprintHex: String): ByteArray = ringFor(fingerprintHex).ring.armored()

    fun forget(fingerprintHex: String) {
        if (selected.equals(fingerprintHex, ignoreCase = true)) selected = null
        fileFor(fingerprintHex).delete()
        unlocked.remove(fingerprintHex)?.let { it.fill(' ') }
        cached = null
    }

    /** Drops every passphrase, so the keys lock again without being deleted. */
    fun lockAll() {
        unlocked.values.forEach { it.fill(' ') }
        unlocked.clear()
    }

    private fun keyrings(): List<SecretKeyRing> = cached ?: synchronized(this) {
        cached ?: directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.endsWith(SUFFIX) }
            .sortedBy { it.name }
            .mapNotNull { file ->
                runCatching { SecretKeyRing.read(file.readBytes()).firstOrNull() }.getOrNull()
            }
            .also { cached = it }
    }

    private fun write(ring: SecretKeyRing) {
        val name = ring.keys.firstOrNull()?.fingerprintHex ?: return
        fileFor(name).writeBytes(ring.ring.armored())
        cached = null
    }

    private fun fileFor(fingerprintHex: String) = File(directory, "$fingerprintHex$SUFFIX")

    private fun ringFor(fingerprintHex: String): SecretKeyRing =
        keyrings().firstOrNull { ring ->
            ring.keys.any { it.fingerprintHex.equals(fingerprintHex, ignoreCase = true) }
        } ?: error("This app holds no key ${fingerprintHex.takeLast(16)}.")

    private fun KeyInfo.toStored(ring: SecretKeyRing) = StoredKey(
        fingerprintHex = fingerprintHex,
        userId = primaryUserId ?: ring.primaryUserIds.firstOrNull().orEmpty(),
        algorithm = KeyAlgorithm.of(algorithm),
        canSign = isSigningKey,
        isPassphraseProtected = isPassphraseProtected,
    )

    companion object {
        private const val SUFFIX = ".asc"
        private const val KEY_SELECTED = "signing.key"

        @Volatile
        private var instance: KeyStore? = null

        /**
         * One store for the process.
         *
         * A key unlocked on the settings screen has to be unlocked on the repository
         * screen too, and a per-screen instance would give each its own idea of which
         * keys are open. The passphrase map is the app's session state, not a cache.
         */
        fun getInstance(context: Context): KeyStore = instance ?: synchronized(this) {
            instance ?: KeyStore(context.applicationContext).also { instance = it }
        }
    }
}
