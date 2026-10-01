package com.geno1024.ai.gits.openpgp

import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import java.io.IOException
import java.util.Date

/** Parses OpenPGP keyrings, in either ASCII-armored or binary form. */
object KeyRings {

    fun readSecretKeys(encoded: ByteArray): PGPSecretKeyRingCollection {
        val calculator = JcaKeyFingerprintCalculator()
        return try {
            encoded.decoderStream().use { PGPSecretKeyRingCollection(it, calculator) }
        } catch (failure: IOException) {
            throw IllegalArgumentException(plainWordsFor(encoded, failure), failure)
        } catch (failure: PGPException) {
            throw IllegalArgumentException(plainWordsFor(encoded, failure), failure)
        }
    }

    fun readPublicKeys(encoded: ByteArray): PGPPublicKeyRingCollection {
        val calculator = JcaKeyFingerprintCalculator()
        return try {
            encoded.decoderStream().use { PGPPublicKeyRingCollection(it, calculator) }
        } catch (failure: IOException) {
            throw IllegalArgumentException(plainWordsFor(encoded, failure), failure)
        } catch (failure: PGPException) {
            throw IllegalArgumentException(plainWordsFor(encoded, failure), failure)
        }
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

/**
 * What to tell the person about a file OpenPGP refused to read.
 *
 * The message BouncyCastle raises is about armour headers and packet tags, which names
 * nothing a person holding a key file has ever seen. What they need to know is which of
 * the several formats called a "key" this one is, because all the rest are answers to a
 * question this app does not ask. The original is kept as the cause rather than dropped,
 * so a genuine OpenPGP file that is merely broken still says why.
 */
private fun plainWordsFor(encoded: ByteArray, failure: Throwable): String {
    val label = armorLabel(encoded) ?: return "That file does not look like an OpenPGP key."
    if (label.contains("PGP", ignoreCase = true)) {
        return "That OpenPGP key file could not be read: ${failure.message ?: "unknown reason"}."
    }
    val named = when {
        label.contains("OPENSSH", ignoreCase = true) -> "an SSH key"
        label.endsWith("PRIVATE KEY", ignoreCase = true) ||
            label.equals("PUBLIC KEY", ignoreCase = true) -> "an OpenSSL key"
        label.contains("CERTIFICATE", ignoreCase = true) -> "a certificate"
        else -> null
    }
    return if (named == null) {
        "That file is not an OpenPGP key."
    } else {
        "That file is $named, not an OpenPGP key. Commits here are signed with OpenPGP keys."
    }
}

/**
 * The label between `-----BEGIN ` and the next `-----`, if the file starts with one.
 *
 * Read as Latin-1 rather than UTF-8 so that a binary key, whose bytes are arbitrary,
 * is inspected rather than decoded away.
 */
private fun armorLabel(encoded: ByteArray): String? {
    val head = String(encoded, 0, minOf(encoded.size, HEAD_LIMIT), Charsets.ISO_8859_1)
    val at = head.indexOf(BEGIN_MARKER)
    if (at < 0) return null
    val from = at + BEGIN_MARKER.length
    val to = head.indexOf(ARMOR_CLOSE, from)
    if (to < 0) return null
    return head.substring(from, to).trim()
}

private const val HEAD_LIMIT = 4096
private const val BEGIN_MARKER = "-----BEGIN "
private const val ARMOR_CLOSE = "-----"

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
