package g.gits.openpgp

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
}

/** Creates OpenPGP keyrings laid out the way `gpg --quick-generate-key` does. */
object KeyGeneration {

    private const val RSA_BITS = 2048

    /**
     * Generates a keyring with a certifying primary key and a dedicated signing subkey.
     *
     * The [userId] is a certification on the primary key, not decoration: a keyring
     * exported without one is rejected by `gpg --import`, so the parameter is required.
     */
    fun generate(
        userId: String,
        algorithm: KeyAlgorithm,
        passphrase: CharArray,
        creationTime: Date = Date(),
    ): PGPSecretKeyRing {
        val provider = BouncyCastle.provider
        val master = generateKeyPair(algorithm, creationTime)
        val signing = generateKeyPair(algorithm, creationTime)

        val sha1 = JcaPGPDigestCalculatorProviderBuilder()
            .setProvider(provider)
            .build()
            .get(HashAlgorithmTags.SHA1)

        val preferences = PGPSignatureSubpacketGenerator().apply {
            setKeyFlags(false, KeyFlags.CERTIFY_OTHER or KeyFlags.AUTHENTICATION)
            setIssuerFingerprint(false, master.publicKey)
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

        val generator = PGPKeyRingGenerator(
            PGPSignature.POSITIVE_CERTIFICATION,
            master,
            userId,
            sha1,
            preferences,
            null,
            JcaPGPContentSignerBuilder(algorithm.pgpId, algorithm.hashAlgorithm)
                .setProvider(provider),
            JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, sha1)
                .setProvider(provider)
                .build(passphrase),
        )

        val subkeyBinding = PGPSignatureSubpacketGenerator().apply {
            setKeyFlags(false, KeyFlags.SIGN_DATA)
            setIssuerFingerprint(false, master.publicKey)
        }.generate()

        generator.addSubKey(signing, subkeyBinding, subkeyBinding)
        return generator.generateSecretKeyRing()
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
