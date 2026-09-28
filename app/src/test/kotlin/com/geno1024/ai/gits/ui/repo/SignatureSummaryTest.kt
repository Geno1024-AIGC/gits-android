package com.geno1024.ai.gits.ui.repo

import com.geno1024.ai.gits.git.LogEntry
import com.geno1024.ai.gits.openpgp.SignatureCheck
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SignatureSummaryTest {

    private fun entry(signed: Boolean, keyId: String? = null, check: SignatureCheck? = null) = LogEntry(
        id = "0".repeat(40),
        shortId = "0000000",
        authorName = "Ada",
        authorEmail = "ada@gits.invalid",
        committedAtEpochMillis = 0L,
        subject = "a commit",
        body = "",
        signaturePresent = signed,
        signedByKeyId = keyId,
        signatureCheck = check,
    )

    @Test
    fun `an unsigned commit says nothing about signatures`() {
        assertNull(entry(signed = false).signatureSummary())
    }

    @Test
    fun `a verified signature is good and names the key`() {
        val summary = entry(
            signed = true,
            keyId = "81970E67EA9B4250",
            check = SignatureCheck.Verified("81970E67EA9B4250", "FINGERPRINT"),
        ).signatureSummary()

        assertEquals(Trust.GOOD, summary?.trust)
        assertEquals("signed by 81970E67EA9B4250, verified", summary?.text)
    }

    @Test
    fun `a key we lack is neither good nor bad`() {
        // The distinction that matters: nobody here can check this, which is not the
        // same as it being forged, and reading as either would mislead.
        val summary = entry(
            signed = true,
            keyId = "81970E67EA9B4250",
            check = SignatureCheck.NoKey("81970E67EA9B4250"),
        ).signatureSummary()

        assertEquals(Trust.UNKNOWN, summary?.trust)
    }

    @Test
    fun `a signature that does not verify is bad`() {
        val summary = entry(
            signed = true,
            keyId = "81970E67EA9B4250",
            check = SignatureCheck.Invalid("81970E67EA9B4250"),
        ).signatureSummary()

        assertEquals(Trust.BAD, summary?.trust)
        assertEquals("signed by 81970E67EA9B4250, does not verify", summary?.text)
    }

    @Test
    fun `a key that was not valid when it signed is not called good`() {
        // Signing while a key was expired is a real problem, but it is not evidence of
        // tampering, so it must not be dressed up as a forgery.
        val summary = entry(
            signed = true,
            keyId = "81970E67EA9B4250",
            check = SignatureCheck.KeyNotValidThen("81970E67EA9B4250"),
        ).signatureSummary()

        assertEquals(Trust.UNKNOWN, summary?.trust)
        assertEquals("signed by 81970E67EA9B4250, key not valid when signed", summary?.text)
    }

    @Test
    fun `an unreadable signature is bad and does not blame a key`() {
        val summary = entry(
            signed = true,
            keyId = "81970E67EA9B4250",
            check = SignatureCheck.Malformed("truncated"),
        ).signatureSummary()

        assertEquals(Trust.BAD, summary?.trust)
        assertEquals("signature unreadable", summary?.text)
    }

    @Test
    fun `a signed commit with no keys available is not called good`() {
        val summary = entry(signed = true, keyId = "81970E67EA9B4250", check = null).signatureSummary()

        assertEquals(Trust.UNKNOWN, summary?.trust)
        assertEquals("signed by 81970E67EA9B4250, not checked", summary?.text)
    }

    @Test
    fun `a signature with no readable key id still says something`() {
        val summary = entry(signed = true, keyId = null, check = SignatureCheck.Malformed("no issuer")).signatureSummary()

        assertEquals(Trust.BAD, summary?.trust)
    }
}
