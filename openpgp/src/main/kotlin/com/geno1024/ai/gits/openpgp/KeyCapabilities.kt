package com.geno1024.ai.gits.openpgp

import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPSignature

/**
 * The capabilities a key's own signatures declare for it, as a bitmask of `KeyFlags`.
 *
 * BouncyCastle's `PGPSecretKey.isSigningKey` answers a different question: it reports
 * whether the *algorithm* can sign at all, so an RSA primary key and an RSA signing
 * subkey both answer `true`. Choosing between them requires reading the key flags from
 * the self signature, which is what this does.
 *
 * Returns an empty set when the key carries no self signature that states capabilities.
 */
internal fun PGPPublicKey.capabilities(): Set<Int> {
    val declared = stateCapabilities()
    if (declared.isNotEmpty()) return declared

    // A key with no declared flags predates the key flags subpacket and is assumed
    // capable of everything its algorithm allows.
    return when {
        algorithm == org.bouncycastle.bcpg.PublicKeyAlgorithmTags.RSA_GENERAL ||
            algorithm == org.bouncycastle.bcpg.PublicKeyAlgorithmTags.EDDSA_LEGACY ||
            algorithm == org.bouncycastle.bcpg.PublicKeyAlgorithmTags.ECDSA ->
            ALL_FLAGS
        else -> emptySet()
    }
}

private fun PGPPublicKey.stateCapabilities(): Set<Int> {
    val preferred = if (isMasterKey) {
        MASTER_SELF_SIGNATURES
    } else {
        SUBKEY_BINDING_SIGNATURES
    }
    val signatures = signatures.asList()
    for (type in preferred) {
        for (signature in signatures) {
            if (signature.signatureType != type) continue
            val flags = signature.hashedSubPackets.keyFlags
            if (flags != 0) return decode(flags)
        }
    }
    return emptySet()
}

internal fun PGPPublicKey.canSign(): Boolean =
    KeyFlags.SIGN_DATA in capabilities()

internal fun PGPPublicKey.canCertify(): Boolean =
    KeyFlags.CERTIFY_OTHER in capabilities()

internal fun PGPPublicKey.canAuthenticate(): Boolean =
    KeyFlags.AUTHENTICATION in capabilities()

private fun decode(flags: Int): Set<Int> =
    FLAGS.filter { flags and it != 0 }.toSet()

private val FLAGS = listOf(
    KeyFlags.CERTIFY_OTHER,
    KeyFlags.SIGN_DATA,
    KeyFlags.ENCRYPT_COMMS,
    KeyFlags.ENCRYPT_STORAGE,
    KeyFlags.SPLIT,
    KeyFlags.AUTHENTICATION,
    KeyFlags.SHARED,
)

private val ALL_FLAGS = FLAGS.toSet()

private val MASTER_SELF_SIGNATURES = listOf(
    PGPSignature.DIRECT_KEY,
    PGPSignature.POSITIVE_CERTIFICATION,
    PGPSignature.CASUAL_CERTIFICATION,
    PGPSignature.DEFAULT_CERTIFICATION,
)

private val SUBKEY_BINDING_SIGNATURES = listOf(
    PGPSignature.SUBKEY_BINDING,
    PGPSignature.PRIMARYKEY_BINDING,
)
