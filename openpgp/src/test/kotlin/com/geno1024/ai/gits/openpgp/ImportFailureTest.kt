package com.geno1024.ai.gits.openpgp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What a person holding a key file is told when the file is not one.
 *
 * Every format below is called a key by the tools that produce it, and none of them is
 * the format this app signs with, so naming the format is the whole of the answer.
 */
class ImportFailureTest {

    private val passphrase = "correct horse battery staple".toCharArray()

    private fun keyring() = KeyGeneration.generate(
        "Gits Test <test@gits.invalid>",
        KeyAlgorithm.ED25519,
        passphrase,
    )

    private fun refusal(bytes: ByteArray): String =
        runCatching { SecretKeyRing.read(bytes) }
            .exceptionOrNull()
            ?.message
            ?: error("that file was read without complaint")

    @Test
    fun `an openssh key is named as one`() {
        val ssh = """
            -----BEGIN OPENSSH PRIVATE KEY-----
            b3BlbnNzaC1rZXktaXQtaWQtdjIAAAAACnNoYTI1NgAAAAg
            ZmFrZWRhdGEyNTYAAAAAAAAACnNoYTI1NgAAAAg
            -----END OPENSSH PRIVATE KEY-----
        """.trimIndent().toByteArray()

        assertEquals(
            "That file is an SSH key, not an OpenPGP key. Commits here are signed with OpenPGP keys.",
            refusal(ssh),
        )
    }

    @Test
    fun `an openssl key is named as one`() {
        val pem = """
            -----BEGIN RSA PRIVATE KEY-----
            MIIEpAIBAAKCAQEA0Z3VS5JJcds3xfn/ygWyF6PZF1pG+nF+vJ1EpUeuRaST
            -----END RSA PRIVATE KEY-----
        """.trimIndent().toByteArray()

        assertEquals(
            "That file is an OpenSSL key, not an OpenPGP key. Commits here are signed with OpenPGP keys.",
            refusal(pem),
        )
    }

    @Test
    fun `a certificate is named as one`() {
        val certificate = """
            -----BEGIN CERTIFICATE-----
            MIIDdzCCAl+gAwIBAgIEb0T0lTANBgkqkiG9w0BAQsFADB6MQswCQYDVQQGEwJV
            -----END CERTIFICATE-----
        """.trimIndent().toByteArray()

        assertTrue(
            refusal(certificate).startsWith("That file is a certificate, not an OpenPGP key."),
            "a certificate is not a key this app can use",
        )
    }

    @Test
    fun `a file that is not armour at all says so without jargon`() {
        val message = refusal(byteArrayOf(0x80.toByte(), 0x01, 0x02, 0x03))
        assertEquals("That file does not look like an OpenPGP key.", message)
    }

    @Test
    fun `an openpgp file that is broken is called damaged rather than foreign`() {
        val broken = """
            -----BEGIN PGP PRIVATE KEY BLOCK-----
            not base64 at all!!
            -----END PGP PRIVATE KEY BLOCK-----
        """.trimIndent().toByteArray()

        val message = refusal(broken)
        assertTrue(
            message.startsWith("That OpenPGP key file could not be read:"),
            "a key that really is OpenPGP should not be reported as some other format: $message",
        )
    }

    @Test
    fun `text that holds no key is read as empty rather than refused`() {
        assertTrue(
            SecretKeyRing.read("just some notes".toByteArray()).isEmpty(),
            "a keystore to sort out is not a reason to throw",
        )
    }

    @Test
    fun `an armoured key reads back`() {
        assertEquals(1, SecretKeyRing.read(keyring().armored()).size)
    }

    @Test
    fun `a binary key reads back`() {
        assertEquals(1, SecretKeyRing.read(keyring().encoded).size)
    }

    @Test
    fun `a byte order mark in front of an armoured key does not hide it`() {
        val marked = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + keyring().armored()

        assertEquals(1, SecretKeyRing.read(marked).size, "an invisible mark a save can add")
    }

    @Test
    fun `windows line endings do not break the armour`() {
        val crlf = keyring().armored().toString(Charsets.UTF_8).replace("\n", "\r\n").toByteArray()

        assertEquals(1, SecretKeyRing.read(crlf).size)
    }

    @Test
    fun `the key under a byte order mark keeps its identity`() {
        val ring = keyring()
        val marked = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + ring.armored()

        val expected = SecretKeyRing(ring).keys.first().fingerprintHex
        val read = SecretKeyRing.read(marked).first()

        assertEquals(expected, read.keys.first().fingerprintHex)
    }
}
