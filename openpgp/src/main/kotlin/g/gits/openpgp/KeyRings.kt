package g.gits.openpgp

import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator

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
