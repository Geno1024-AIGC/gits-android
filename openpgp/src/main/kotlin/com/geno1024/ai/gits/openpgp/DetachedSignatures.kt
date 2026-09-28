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
            keyIdHex = signature.keyID.toULong().toString(16).padStart(16, '0').uppercase(),
            fingerprintHex = signature.issuerFingerprint()?.toHex()?.uppercase(),
            creationTimeEpochMillis = signature.creationTime.time,
        )
    }
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
