package com.geno1024.ai.gits.data

import java.nio.file.Path
import java.security.KeyPairGenerator
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * What a held key says about itself once it is looked at.
 *
 * The fingerprint is the answer OpenSSH would give to `ssh-keygen -lf`, taken from the
 * bytes of the key itself, so it says the same thing here as it would in a terminal —
 * there is nothing to store alongside it and nothing for it to drift from. A file that
 * is not a key says nothing and is given nothing.
 */
class SshFingerprintTest {

    @Test
    fun `a held key carries the fingerprint OpenSSH would print`(@TempDir dir: Path) {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val file = dir.resolve("id_rsa").toFile()
        file.writeBytes(pem(keyPair.private.encoded))

        val fingerprint = Ssh.fingerprintOf(file)

        assertTrue(fingerprint!!.startsWith("SHA256:"), "was: $fingerprint")
        assertTrue(fingerprint.length > "SHA256:".length + 40, "too short: $fingerprint")
    }

    @Test
    fun `a file that is not a key yields no fingerprint`(@TempDir dir: Path) {
        val file = dir.resolve("id_rsa").toFile()
        file.writeText("just some notes")

        assertNull(Ssh.fingerprintOf(file))
        assertNull(Ssh.fingerprintOf(dir.resolve("never written").toFile()))
    }

    /** PKCS#8, wrapped the way a key file is wrapped. */
    private fun pem(der: ByteArray): ByteArray =
        ("-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(der) + "\n-----END PRIVATE KEY-----\n")
            .toByteArray()
}
