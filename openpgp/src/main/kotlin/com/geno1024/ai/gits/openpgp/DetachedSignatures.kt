package com.geno1024.ai.gits.openpgp

import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.openpgp.PGPCompressedData
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureGenerator
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider
import java.io.ByteArrayOutputStream

/**
 * Creates and reads detached OpenPGP signatures, the form Git stores in a `gpgsig`
 * header.
 */
object DetachedSignatures {

    /**
     * Signs [payload], returning an ASCII-armored detached signature.
     *
     * Two details here are load-bearing and easy to get wrong:
     *
     *  * The signature type is [PGPSignature.BINARY_DOCUMENT]. Git signs the raw commit
     *    bytes, so a canonical-text signature would never verify.
     *  * No one-pass signature packet is emitted. Detached signatures carry only the
     *    trailing signature packet, and a one-pass header here makes the armor
     *    unparseable as a detached signature.
     */
    fun create(
        secretKey: PGPSecretKey,
        privateKey: org.bouncycastle.openpgp.PGPPrivateKey,
        payload: ByteArray,
        hashAlgorithm: Int = HashAlgorithmTags.SHA512,
    ): ByteArray {
        val publicKey = secretKey.publicKey
        val builder = JcaPGPContentSignerBuilder(publicKey.algorithm, hashAlgorithm)
            .setProvider(BouncyCastle.provider)

        val generator = PGPSignatureGenerator(builder, publicKey)
        generator.init(PGPSignature.BINARY_DOCUMENT, privateKey)

        val subpackets = PGPSignatureSubpacketGenerator().apply {
            setIssuerFingerprint(false, publicKey)
        }.generate()
        generator.setHashedSubpackets(subpackets)

        generator.update(payload)
        return generator.generate().armored()
    }

    /** Parses the first signature out of an armored or binary detached signature. */
    fun parse(encoded: ByteArray): PGPSignature {
        val calculator = org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator()
        encoded.decoderStream().use { stream ->
            var factory = PGPObjectFactory(stream, calculator)
            var next = factory.nextObject()

            if (next is PGPCompressedData) {
                factory = PGPObjectFactory(next.dataStream, calculator)
                next = factory.nextObject()
            }

            if (next !is PGPSignatureList || next.isEmpty()) {
                throw IllegalArgumentException("no OpenPGP signature found in input")
            }
            return next[0]
        }
    }

    private val provider = JcaPGPContentVerifierBuilderProvider()
        .setProvider(BouncyCastle.provider)

    /**
     * Checks [encoded] against [payload] using only the keys given.
     *
     * A signature is a claim about bytes. This is the one function that turns it into a
     * fact, and it is worth being careful about what it refuses to conclude: a key that
     * is not in [candidateKeys] is reported as missing rather than as a pass or a
     * failure, because "we could not check it" and "it is not valid" are different
     * answers and a caller showing either to a person would be misleading one of them.
     */
    fun verify(encoded: ByteArray, payload: ByteArray, candidateKeys: Iterable<PGPPublicKey>): SignatureCheck {
        val signature = try {
            parse(encoded)
        } catch (failure: IllegalArgumentException) {
            return SignatureCheck.Malformed(failure.message ?: "the signature could not be read")
        }
        // The same rendering everywhere a key id reaches a person, so that the id beside
        // a verdict and the id read out of the signature are the same string.
        val keyId = signature.keyID.toKeyIdHex().uppercase()
        val candidates = candidateKeys.filter { it.keyID == signature.keyID }
        if (candidates.isEmpty()) return SignatureCheck.NoKey(keyId)

        // A signature made while its key was valid is worth something even if that key
        // has since expired, which is the same reading GnuPG takes.
        val signedAt = signature.creationTime
        val usable = candidates.filter { it.isValidAt(signedAt) }
        if (usable.isEmpty()) return SignatureCheck.KeyNotValidThen(keyId)

        val verifiers = usable.map { key ->
            runCatching {
                signature.init(provider, key)
                signature.update(payload)
                signature.verify() to key
            }.getOrNull()
        }
        val verified = verifiers.firstOrNull { it?.first == true }
        if (verified != null) {
            return SignatureCheck.Verified(
                keyIdHex = keyId,
                fingerprintHex = verified.second.fingerprint.toHex().uppercase(),
            )
        }
        // Everything went wrong rather than everything checking out: a usable key of the
        // right id was offered and the bytes did not match. That is a broken or forged
        // signature, not a missing key.
        return SignatureCheck.Invalid(keyId)
    }

    /**
     * Who a signature claims to be from, without checking that claim.
     *
     * This reads the issuer subpackets and nothing else, so it is only good for
     * showing a user which key Git says signed something. It proves nothing on its
     * own; [DetachedSignatures.verify] is what turns the claim into a fact.
     */
    fun issuerOf(encoded: ByteArray): SignatureIssuer? {
        val signature = parse(encoded)
        return SignatureIssuer(
            keyIdHex = signature.keyID.toKeyIdHex().uppercase(),
            fingerprintHex = signature.issuerFingerprint()?.toHex()?.uppercase(),
            creationTimeEpochMillis = signature.creationTime.time,
        )
    }
}

/**
 * The result of checking a signature, keeping the reasons apart.
 *
 * [NoKey] and [Invalid] are both "not verified" and neither is "verified", and the
 * difference is the whole point: one asks the person to hand over a public key, the
 * other says the thing they were sent is not what it claims to be.
 */
sealed interface SignatureCheck {

    /** The signature checks out against a key that was offered. */
    data class Verified(val keyIdHex: String, val fingerprintHex: String) : SignatureCheck

    /** No key with this id was among those offered, so nothing was checked. */
    data class NoKey(val keyIdHex: String) : SignatureCheck

    /** A key with this id was offered but was not valid when the signature was made. */
    data class KeyNotValidThen(val keyIdHex: String) : SignatureCheck

    /** A key with this id was offered, and the payload does not match the signature. */
    data class Invalid(val keyIdHex: String) : SignatureCheck

    /** The bytes are not a signature this can read. */
    data class Malformed(val reason: String) : SignatureCheck
}

/** The signer's identity as a signature states it, checked or not. */
data class SignatureIssuer(
    val keyIdHex: String,
    val fingerprintHex: String?,
    val creationTimeEpochMillis: Long,
)

/** ASCII-armors a signature, wrapped exactly as GnuPG writes it. */
internal fun PGPSignature.armored(): ByteArray =
    ByteArrayOutputStream().also { out ->
        ArmoredOutputStream(out).use { encode(it) }
    }.toByteArray()

/** The fingerprint this signature claims to be made by, if it states one. */
internal fun PGPSignature.issuerFingerprint(): ByteArray? =
    hashedSubPackets.issuerFingerprint?.fingerprint
