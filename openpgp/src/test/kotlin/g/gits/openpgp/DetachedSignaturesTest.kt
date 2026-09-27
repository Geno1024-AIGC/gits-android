package g.gits.openpgp

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class DetachedSignaturesTest {

    private val userId = "Gits Test <test@gits.invalid>"
    private val passphrase = "correct horse battery staple".toCharArray()
    private val payload = "tree deadbeef\n\nsigned payload\n".toByteArray()

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `produces an armored detached signature`(algorithm: KeyAlgorithm) {
        val text = signWith(algorithm).decodeToString()

        assertTrue(text.startsWith("-----BEGIN PGP SIGNATURE-----"), text.take(40))
        assertTrue(text.contains("-----END PGP SIGNATURE-----"))
        assertEquals(1, Regex("-----BEGIN ").findAll(text).count(), "one signature per payload")
    }

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `signs as a binary document`(algorithm: KeyAlgorithm) {
        val signature = DetachedSignatures.parse(signWith(algorithm))

        assertEquals(
            0,
            signature.signatureType,
            "git signs raw bytes, a canonical-text signature never verifies",
        )
    }

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `names the signing key in the issuer fingerprint`(algorithm: KeyAlgorithm) {
        val ring = SecretKeyRing(KeyGeneration.generate(userId, algorithm, passphrase))
        val expected = ring.select(null).publicKey.fingerprint

        val signature = DetachedSignatures.parse(ring.unlock(null, passphrase).sign(payload))

        assertArrayEquals(
            expected,
            signature.issuerFingerprint(),
            "the issuer fingerprint subpacket is what resolves the verifying key",
        )
    }

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `rejects the wrong passphrase`(algorithm: KeyAlgorithm) {
        val ring = SecretKeyRing(KeyGeneration.generate(userId, algorithm, passphrase))

        assertThrows(Exception::class.java) { ring.unlock(null, "wrong".toCharArray()) }
    }

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `refuses to sign after close`(algorithm: KeyAlgorithm) {
        val session = SecretKeyRing(KeyGeneration.generate(userId, algorithm, passphrase))
            .unlock(null, passphrase)
        session.close()

        assertThrows(IllegalStateException::class.java) { session.sign(payload) }
    }

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `distinct payloads produce distinct signatures`(algorithm: KeyAlgorithm) {
        val session = SecretKeyRing(KeyGeneration.generate(userId, algorithm, passphrase))
            .unlock(null, passphrase)

        val a = session.sign("first".toByteArray())
        val b = session.sign("second".toByteArray())

        assertNotEquals(
            a.toList().toString(),
            b.toList().toString(),
            "distinct payloads must not share a signature",
        )
    }

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `repeating a sign yields signatures from the same key`(algorithm: KeyAlgorithm) {
        val ring = KeyGeneration.generate(userId, algorithm, passphrase).armored()
        val reimported = SecretKeyRing.read(ring).single()

        val a = DetachedSignatures.parse(reimported.unlock(null, passphrase).sign(payload))
        val b = DetachedSignatures.parse(reimported.unlock(null, passphrase).sign(payload))

        // An armored signature embeds its creation time, so the bytes are not stable
        // across a second boundary. What must hold is that both name one signer.
        assertEquals(a.keyID, b.keyID, "both signatures must come from the same key")
        assertTrue(a.issuerFingerprint() != null, "gpg needs the issuer fingerprint subpacket")
    }

    private fun signWith(algorithm: KeyAlgorithm): ByteArray =
        SecretKeyRing(KeyGeneration.generate(userId, algorithm, passphrase))
            .unlock(null, passphrase)
            .sign(payload)
}
