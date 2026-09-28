package com.geno1024.ai.gits.openpgp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class KeyGenerationTest {

    private val userId = "Gits Test <test@gits.invalid>"
    private val passphrase = "correct horse battery staple".toCharArray()

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `generates a keyring carrying a user id`(algorithm: KeyAlgorithm) {
        val ring = KeyGeneration.generate(userId, algorithm, passphrase)

        assertEquals(
            listOf(userId),
            ring.publicKey.userIds(),
            "gpg rejects a keyring with no user id",
        )
    }

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `generates one key that can both certify and sign`(algorithm: KeyAlgorithm) {
        val ring = KeyGeneration.generate(userId, algorithm, passphrase)
        val keys = ring.allKeys()

        assertNotNull(ring.publicKey)
        assertEquals(1, ring.size(), "one key, so gpg never needs a cross-certified subkey")
        assertTrue(keys[0].publicKey.canCertify(), "the key must certify its own user id")
        assertTrue(keys[0].publicKey.canSign(), "the key must be usable for signing directly")
        assertTrue(
            keys[0].publicKey.canAuthenticate(),
            "phishable keys should still authenticate",
        )
    }

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `passphrase protects the secret key material`(algorithm: KeyAlgorithm) {
        val ring = KeyGeneration.generate(userId, algorithm, passphrase)

        val locked = ring.allKeys().filter { it.isLocked() }
        assertEquals(ring.size(), locked.size, "every secret key must be locked")
    }

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `round trips through ascii armor`(algorithm: KeyAlgorithm) {
        val ring = KeyGeneration.generate(userId, algorithm, passphrase)
        val armored = ring.armored()

        assertTrue(
            armored.decodeToString().startsWith("-----BEGIN PGP PRIVATE KEY BLOCK-----"),
            "expected an armored private key block",
        )

        val parsed = KeyRings.readSecretKeys(armored)
        assertEquals(1, parsed.size())
        assertEquals(
            ring.publicKey.fingerprint.toHex(),
            parsed.allRings()[0].publicKey.fingerprint.toHex(),
        )
    }

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `emits ed25519 as the legacy algorithm id`(algorithm: KeyAlgorithm) {
        val ring = KeyGeneration.generate(userId, algorithm, passphrase)

        val expected = if (algorithm == KeyAlgorithm.ED25519) 22 else 1
        ring.allKeys().forEach { key ->
            assertEquals(expected, key.publicKey.algorithm, "pgp algorithm id")
        }
    }
}
