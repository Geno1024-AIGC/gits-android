package com.geno1024.ai.gits.openpgp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * A key with no passphrase.
 *
 * Not a corner case: plenty of keys are made that way, and they used to be unusable
 * here in two separate ways. Generating one handed an empty array to the encryptor,
 * which produced s2k usage 254 — encrypted with the empty string, needing a passphrase
 * to open, and looking protected to anything reading s2k usage. And unlocking went
 * through a decryptor unconditionally, so an unprotected key could not be opened at all.
 */
class UnprotectedKeyTest {

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `an empty passphrase yields a key stored unprotected`(algorithm: KeyAlgorithm) {
        val ring = SecretKeyRing(
            KeyGeneration.generate("No Pass <n@gits.invalid>", algorithm, CharArray(0)),
        )
        val key = ring.keys.single()

        // s2k usage 0 is what makes the key open without one, and what stops it being
        // mistaken for a protected key.
        assertFalse(key.isPassphraseProtected, "an unprotected key should not claim to need a passphrase")
        assertEquals(0, ring.ring.allKeys().single().s2KUsage)
    }

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `an unprotected key opens and signs with any passphrase`(algorithm: KeyAlgorithm) {
        val ring = SecretKeyRing(
            KeyGeneration.generate("No Pass <n@gits.invalid>", algorithm, CharArray(0)),
        )
        val payload = "signed with an unprotected key".toByteArray()

        // A wrong passphrase is ignored rather than fatal: there is nothing to unlock, so
        // refusing would be inventing a password the key does not have.
        for (attempted in listOf(CharArray(0), "not the passphrase".toCharArray())) {
            val signature = ring.unlock(null, attempted).sign(payload)
            assertInstanceOfVerified(DetachedSignatures.verify(signature, payload, ring.publicKeys))
        }
    }

    @Test
    fun `a key with a passphrase still needs the right one`() {
        val ring = SecretKeyRing(
            KeyGeneration.generate("Locked <l@gits.invalid>", KeyAlgorithm.ED25519, "s3cret".toCharArray()),
        )
        assertTrue(ring.keys.single().isPassphraseProtected)

        val wrong = runCatching { ring.unlock(null, "wrong".toCharArray()) }
        assertTrue(wrong.isFailure, "a protected key must not open on the wrong passphrase")
        ring.unlock(null, "s3cret".toCharArray())
    }

    @Test
    fun `a protected key and an unprotected one are told apart`() {
        val protectedKey = SecretKeyRing(
            KeyGeneration.generate("P <p@gits.invalid>", KeyAlgorithm.ED25519, "x".toCharArray()),
        )
        val open = SecretKeyRing(
            KeyGeneration.generate("O <o@gits.invalid>", KeyAlgorithm.ED25519, CharArray(0)),
        )
        assertTrue(protectedKey.keys.single().isPassphraseProtected)
        assertFalse(open.keys.single().isPassphraseProtected)
    }

    @Test
    fun `an unprotected key survives a round trip through armored form`() {
        val original = SecretKeyRing(
            KeyGeneration.generate("Round <r@gits.invalid>", KeyAlgorithm.ED25519, CharArray(0)),
        )
        // Import is a read of someone else's file, and the passphrase typed at the prompt
        // has to be ignored for it to be importable at all.
        val reloaded = SecretKeyRing.read(original.ring.armored()).single()

        assertFalse(reloaded.keys.single().isPassphraseProtected)
        val payload = "round trip".toByteArray()
        val signature = reloaded.unlock(null, "anything".toCharArray()).sign(payload)
        assertInstanceOfVerified(DetachedSignatures.verify(signature, payload, reloaded.publicKeys))
    }

    private fun assertInstanceOfVerified(check: SignatureCheck) {
        assertTrue(check is SignatureCheck.Verified, "expected the signature to verify, got $check")
    }
}
