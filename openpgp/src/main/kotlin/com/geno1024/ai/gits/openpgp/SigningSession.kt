package com.geno1024.ai.gits.openpgp

import org.bouncycastle.bcpg.SecretKeyPacket
import org.bouncycastle.openpgp.PGPPrivateKey
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder
import java.util.Arrays

/**
 * A passphrase-unlocked secret key, able to produce detached signatures.
 *
 * Instances are short lived by design: the caller unlocks, signs, and closes. Holding
 * a decrypted private key longer than a single signing operation widens the window in
 * which key material sits in memory for no benefit.
 *
 * Closing drops the reference to the key; BouncyCastle exposes no way to overwrite the
 * private key packet, so a decrypted key that reached the heap cannot be reliably
 * erased. Treat unlocking as the risky step, not closing.
 */
class SigningSession internal constructor(
    val key: KeyInfo,
    private val secretKey: PGPSecretKey,
    privateKey: PGPPrivateKey,
) : AutoCloseable {

    private var unlocked: PGPPrivateKey? = privateKey

    /** Produces an ASCII-armored detached signature over [payload]. */
    fun sign(payload: ByteArray): ByteArray {
        val privateKey = unlocked ?: error("signing session is already closed")
        return DetachedSignatures.create(secretKey, privateKey, payload)
    }

    override fun close() {
        unlocked = null
    }
}

/** Overwrites a passphrase held in a char array. */
fun CharArray.wipe() {
    Arrays.fill(this, ' ')
}

/** Builds the describeable view of [this] key, attributing it to [master]. */
internal fun PGPSecretKey.info(master: PGPSecretKeyRing): KeyInfo {
    val primary = master.publicKey
    return KeyInfo(
        fingerprint = publicKey.fingerprint,
        masterFingerprint = primary.fingerprint,
        keyId = publicKey.keyID,
        userIds = primary.userIds(),
        algorithm = publicKey.algorithm,
        isSigningKey = publicKey.canSign(),
        isEncryptionKey = publicKey.isEncryptionKey,
        creationTime = publicKey.creationTime,
        isPassphraseProtected = getS2KUsage() != SecretKeyPacket.USAGE_NONE,
    )
}

internal fun unlockKey(
    secretKey: PGPSecretKey,
    master: PGPSecretKeyRing,
    passphrase: CharArray,
): SigningSession {
    // A key with no passphrase needs no decryptor, and must not be handed one: asking
    // BouncyCastle to decrypt unprotected key material throws, so a key the user chose
    // to leave open would be unusable. The passphrase is ignored here, which is correct
    // — there is nothing to unlock.
    val privateKey = if (secretKey.isLocked()) {
        secretKey.extractPrivateKey(
            JcePBESecretKeyDecryptorBuilder()
                .setProvider(BouncyCastle.provider)
                .build(passphrase),
        )
    } else {
        secretKey.extractPrivateKey(null)
    }
    return SigningSession(
        key = secretKey.info(master),
        secretKey = secretKey,
        privateKey = privateKey,
    )
}
