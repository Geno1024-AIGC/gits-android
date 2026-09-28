package com.geno1024.ai.gits.openpgp

import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.CompressionAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.openpgp.PGPKeyPair
import org.bouncycastle.openpgp.PGPKeyRingGenerator
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.spec.RSAKeyGenParameterSpec
import java.util.Date

/** Public key algorithms this module is willing to mint. */
enum class KeyAlgorithm {
    RSA,
    ED25519,
    ;

    /**
     * PGP algorithm id used to encode the key.
     *
     * Ed25519 keys are emitted as `EDDSA_LEGACY` (22) rather than the RFC 9580 id
     * (27) because GnuPG 2.4 and older reject the newer id outright, which would make
     * keys generated here unreadable by the desktop tools they are destined for.
     */
    internal val pgpId: Int
        get() = when (this) {
            RSA -> PublicKeyAlgorithmTags.RSA_GENERAL
            ED25519 -> PublicKeyAlgorithmTags.EDDSA_LEGACY
        }

    internal val hashAlgorithm: Int
        get() = when (this) {
            RSA -> HashAlgorithmTags.SHA256
            ED25519 -> HashAlgorithmTags.SHA512
        }

    companion object {
        /**
         * The algorithm with this PGP id, or null for one this app does not generate.
         *
         * Keys arrive from imports as a bare id, and a key the app cannot name is
         * still a key it may have to sign with, so this reports null rather than
         * guessing at the closest match.
         */
        fun of(pgpId: Int): KeyAlgorithm? = entries.firstOrNull { it.pgpId == pgpId }
    }
}

/** Creates OpenPGP keyrings, the shape `gpg --quick-generate-key` used before subkeys. */
object KeyGeneration {

    private const val RSA_BITS = 2048

    /**
     * Generates a single keyring holding one key that can both certify and sign.
     *
     * [userId] is a certification on the key, not decoration: a keyring exported
     * without one is rejected by `gpg --import`.
     *
     * The modern GnuPG layout splits certification onto the primary key and signing
     * onto a subkey, which additionally requires the primary to carry a direct key
     * signature cross-certifying the subkey binding. BouncyCastle exposes no way to
     * attach that signature to a generated ring, and GnuPG refuses to use a subkey
     * that is not cross-certified, so a subkey layout here would mint keys that
     * silently fail to sign everywhere outside this app. One key needs no
     * cross-certification and interoperates cleanly. Keys imported from a desktop
     * GnuPG still carry subkeys, and [SecretKeyRing] resolves those.
     */
    fun generate(
        userId: String,
        algorithm: KeyAlgorithm,
        passphrase: CharArray,
        creationTime: Date = Date(),
    ): PGPSecretKeyRing {
        val provider = BouncyCastle.provider
        val key = generateKeyPair(algorithm, creationTime)

        val sha1 = JcaPGPDigestCalculatorProviderBuilder()
            .setProvider(provider)
            .build()
            .get(HashAlgorithmTags.SHA1)

        val capabilities = PGPSignatureSubpacketGenerator().apply {
            setKeyFlags(false, KeyFlags.CERTIFY_OTHER or KeyFlags.SIGN_DATA or KeyFlags.AUTHENTICATION)
            setIssuerFingerprint(false, key.publicKey)
            setPreferredHashAlgorithms(
                false,
                intArrayOf(HashAlgorithmTags.SHA512, HashAlgorithmTags.SHA256),
            )
            setPreferredCompressionAlgorithms(
                false,
                intArrayOf(CompressionAlgorithmTags.ZLIB),
            )
            setPreferredSymmetricAlgorithms(
                false,
                intArrayOf(
                    SymmetricKeyAlgorithmTags.AES_256,
                    SymmetricKeyAlgorithmTags.AES_192,
                    SymmetricKeyAlgorithmTags.AES_128,
                ),
            )
        }.generate()

        return PGPKeyRingGenerator(
            PGPSignature.POSITIVE_CERTIFICATION,
            key,
            userId,
            sha1,
            capabilities,
            null,
            JcaPGPContentSignerBuilder(algorithm.pgpId, algorithm.hashAlgorithm)
                .setProvider(provider),
            JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, sha1)
                .setProvider(provider)
                .build(passphrase),
        ).generateSecretKeyRing()
    }

    private fun generateKeyPair(algorithm: KeyAlgorithm, creationTime: Date): PGPKeyPair {
        val provider = BouncyCastle.provider
        val generator = when (algorithm) {
            KeyAlgorithm.RSA -> KeyPairGenerator.getInstance("RSA", provider).apply {
                initialize(RSAKeyGenParameterSpec(RSA_BITS, BigInteger.valueOf(65537)))
            }
            KeyAlgorithm.ED25519 -> KeyPairGenerator.getInstance("Ed25519", provider)
        }
        return JcaPGPKeyPair(algorithm.pgpId, generator.generateKeyPair(), creationTime)
    }
}
