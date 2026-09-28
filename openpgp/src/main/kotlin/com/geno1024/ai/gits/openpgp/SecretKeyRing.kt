package com.geno1024.ai.gits.openpgp

import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing

/** Raised when a signing key spec matches nothing usable in a keyring. */
class NoSigningKey(spec: String?) : IllegalArgumentException(
    if (spec == null) "no key in this keyring is designated for signing"
    else "no signing key matches \"$spec\"",
)

/**
 * A parsed secret keyring, with the key selection rules Git applies to
 * `user.signingkey` layered on top.
 *
 * The spec may be a full fingerprint, a 64-bit key id, a 32-bit short key id, a
 * substring of a user id, or null to take the keyring's own default. A spec naming a
 * primary key resolves to that key's designated signing subkey, which is the whole
 * point of splitting certification and signing; appending `!` pins the exact key
 * instead, matching `gpg` and `git` behaviour.
 */
class SecretKeyRing(val ring: PGPSecretKeyRing) {

    private val master = ring.publicKey
    private val secrets = ring.allKeys()

    /** Every key in the ring, primary key and subkeys alike. */
    val keys: List<KeyInfo> = secrets.map { it.info(ring) }

    /** Keys that the ring designates for creating signatures. */
    val signingKeys: List<KeyInfo> = keys.filter { it.isSigningKey }

    val primaryUserIds: List<String> = master.userIds()

    /** The public half of every key here, for verifying signatures made by them. */
    val publicKeys: List<PGPPublicKey> = secrets.map { it.publicKey }

    /**
     * Resolves [spec] to the key that should actually sign.
     *
     * @throws NoSigningKey when nothing matches
     */
    fun select(spec: String?): PGPSecretKey {
        val pinned = spec?.endsWith(EXACT_SUFFIX) == true
        val needle = spec?.removeSuffix(EXACT_SUFFIX)?.trim().orEmpty()

        val matched = when {
            needle.isEmpty() -> preferredSigningKey()
            else -> match(needle) ?: throw NoSigningKey(spec)
        }

        if (pinned) return matched
        return signingSubkeyOf(matched) ?: matched
    }

    fun infoFor(secretKey: PGPSecretKey): KeyInfo = secretKey.info(ring)

    /** Unlocks the key [spec] designates, ready to sign. */
    fun unlock(spec: String?, passphrase: CharArray): SigningSession {
        val secretKey = select(spec)
        return unlockKey(secretKey, ring, passphrase)
    }

    private fun preferredSigningKey(): PGPSecretKey {
        val candidates = secrets.filter { it.publicKey.canSign() }
        if (candidates.isEmpty()) throw NoSigningKey(null)
        return candidates.firstOrNull { !it.publicKey.isMasterKey }
            ?: candidates.first()
    }

    private fun match(needle: String): PGPSecretKey? {
        val normalized = normalize(needle)
        secrets.firstOrNull { fingerprintMatches(it, normalized) }
            ?.let { return it }
        secrets.firstOrNull { keyIdMatches(it, normalized) }
            ?.let { return it }
        return secrets.firstOrNull { it.publicKey.userIds().any { id -> id.contains(needle, ignoreCase = true) } }
    }

    private fun normalize(spec: String): String = spec
        .removePrefix("0x")
        .replace(" ", "")
        .filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }

    private fun fingerprintMatches(secretKey: PGPSecretKey, candidate: String): Boolean {
        val hex = secretKey.publicKey.fingerprint.toHex()
        return hex.length >= candidate.length && candidate.isNotEmpty() && hex.endsWith(candidate, ignoreCase = true)
    }

    private fun keyIdMatches(secretKey: PGPSecretKey, candidate: String): Boolean {
        if (candidate.length !in 8..16) return false
        val keyId = secretKey.publicKey.keyID.toKeyIdHex()
        return keyId.endsWith(candidate, ignoreCase = true)
    }

    /** The signing subkey bound to [secretKey], when it is a primary key that has one. */
    private fun signingSubkeyOf(secretKey: PGPSecretKey): PGPSecretKey? {
        if (!secretKey.publicKey.isMasterKey) return null
        val masterFingerprint = master.fingerprint
        return secrets.firstOrNull {
            it.publicKey.fingerprint != masterFingerprint && it.publicKey.canSign()
        }
    }

    companion object {
        private const val EXACT_SUFFIX = "!"

        fun read(encoded: ByteArray): List<SecretKeyRing> =
            KeyRings.readSecretKeys(encoded).allRings().map(::SecretKeyRing)
    }
}
