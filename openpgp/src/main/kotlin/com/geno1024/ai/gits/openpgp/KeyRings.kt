package com.geno1024.ai.gits.openpgp

import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import java.util.Date

/** Parses OpenPGP keyrings, in either ASCII-armored or binary form. */
object KeyRings {

    fun readSecretKeys(encoded: ByteArray): PGPSecretKeyRingCollection {
        val calculator = JcaKeyFingerprintCalculator()
        encoded.decoderStream().use { return PGPSecretKeyRingCollection(it, calculator) }
    }

    fun readPublicKeys(encoded: ByteArray): PGPPublicKeyRingCollection {
        val calculator = JcaKeyFingerprintCalculator()
        encoded.decoderStream().use { return PGPPublicKeyRingCollection(it, calculator) }
    }

    /**
     * Every public key in [encoded], whatever shape the file is in.
     *
     * Accepts a secret keyring as readily as a public one, because the key someone
     * verifies with is far more often the one sitting in their own keyring than one
     * they were handed. A file holding something that is not a key at all yields
     * nothing rather than throwing, since a keyring the user cannot read is a
     * keystore to sort out, not a reason to refuse to verify anything.
     */
    fun publicKeys(encoded: ByteArray): List<PGPPublicKey> {
        val fromSecret = runCatching { readSecretKeys(encoded) }.getOrNull()
        if (fromSecret != null) {
            return fromSecret.asList().flatMap { it.asList().map(PGPSecretKey::getPublicKey) }
        }
        return runCatching { readPublicKeys(encoded) }
            .getOrNull()
            ?.asList()
            ?.flatMap { it.asList() }
            .orEmpty()
    }
}

internal fun PGPSecretKeyRingCollection.allRings(): List<PGPSecretKeyRing> = asList()

internal fun PGPPublicKeyRingCollection.allRings(): List<PGPPublicKeyRing> = asList()

internal fun PGPSecretKeyRing.allKeys(): List<PGPSecretKey> = asList()

internal fun PGPPublicKeyRing.allKeys(): List<PGPPublicKey> = asList()

/** The user ids certified on this key; empty when it carries none. */
internal fun PGPSecretKey.userIds(): List<String> = userIDs.asList()

internal fun PGPPublicKey.userIds(): List<String> = userIDs.asList()

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

internal fun Long.toKeyIdHex(): String = toULong().toString(16).padStart(16, '0')

/** True when the secret key packet is locked behind a passphrase. */
internal fun PGPSecretKey.isLocked(): Boolean = s2KUsage != 0

/**
 * Whether this key was usable at [moment].
 *
 * BouncyCastle 1.86 offers no accessor for this, so it is worked out from the key's own
 * creation time and lifetime. A key that expires after signing does not retroactively
 * make the signature suspect, which is why the question is asked about the moment of
 * signing rather than about now.
 */
internal fun PGPPublicKey.isValidAt(moment: Date): Boolean {
    if (moment.before(creationTime)) return false
    val lifetime = validSeconds
    if (lifetime <= 0) return true
    return moment.time <= creationTime.time + lifetime * 1000L
}
