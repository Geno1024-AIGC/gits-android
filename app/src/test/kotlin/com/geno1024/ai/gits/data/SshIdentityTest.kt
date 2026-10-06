package com.geno1024.ai.gits.data

import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * What a file has to say for this app to know which name to file it under.
 *
 * The transport only ever looks for four names, so a key that arrives from anywhere —
 * a file picker, a terminal's folder — has to be recognised or nothing will try it.
 * The bytes are all that is looked at: where the file came from decides nothing.
 */
class SshIdentityTest {

    @Test
    fun `a PEM key is named after its own banner`() {
        assertEquals("id_rsa", Ssh.defaultNameFor(pem("RSA PRIVATE KEY", "kEjHxQ==")))
        assertEquals("id_dsa", Ssh.defaultNameFor(pem("DSA PRIVATE KEY", "kEjHxQ==")))
        assertEquals("id_ecdsa", Ssh.defaultNameFor(pem("EC PRIVATE KEY", "kEjHxQ==")))
    }

    @Test
    fun `an OpenSSH key is named after the key type inside it`() {
        assertEquals("id_ed25519", Ssh.defaultNameFor(openssh("ssh-ed25519")))
        assertEquals("id_rsa", Ssh.defaultNameFor(openssh("ssh-rsa")))
        assertEquals("id_ecdsa", Ssh.defaultNameFor(openssh("ecdsa-sha2-nistp256")))
        assertEquals("id_dsa", Ssh.defaultNameFor(openssh("ssh-dss")))
    }

    @Test
    fun `a PKCS 8 key is named after the algorithm it wraps`() {
        assertEquals("id_ed25519", Ssh.defaultNameFor(pkcs8(0x06, 0x03, 0x2B, 0x65, 0x70)))
        assertEquals(
            "id_rsa",
            Ssh.defaultNameFor(
                pkcs8(0x06, 0x09, 0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x01),
            ),
        )
        assertEquals(
            "id_ecdsa",
            Ssh.defaultNameFor(pkcs8(0x06, 0x07, 0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x02, 0x01)),
        )
    }

    @Test
    fun `a file that says nothing about itself is not guessed at`() {
        assertNull(Ssh.defaultNameFor("just some notes".toByteArray()))
        assertNull(Ssh.defaultNameFor("ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAA comment".toByteArray()))
        assertNull(Ssh.defaultNameFor(pkcs8(0x06, 0x03, 0x01, 0x02, 0x03)))
        // An encrypted key's body is unreadable, so its kind cannot come from the bytes.
        assertNull(Ssh.defaultNameFor(pem("ENCRYPTED PRIVATE KEY", "kEjHxQ==")))
    }

    @Test
    fun `the name the file went by is used only when the bytes say nothing`() {
        assertEquals("id_ed25519", Ssh.identityNameFor("nothing here".toByteArray(), "id_ed25519"))
        assertNull(Ssh.identityNameFor("nothing here".toByteArray(), "server-key"))
        assertNull(Ssh.identityNameFor("nothing here".toByteArray(), null))
        assertEquals("id_rsa", Ssh.identityNameFor(pem("RSA PRIVATE KEY", "kEjHxQ=="), "id_ed25519"))
    }

    private fun pem(kind: String, body: String): ByteArray =
        ("-----BEGIN $kind-----\n$body\n-----END $kind-----\n").toByteArray()

    private fun openssh(type: String): ByteArray {
        val body = Base64.getEncoder().encodeToString(type.toByteArray() + ByteArray(24))
        return pem("OPENSSH PRIVATE KEY", body)
    }

    private fun pkcs8(vararg oid: Int): ByteArray {
        val der = byteArrayOf(0x30, 0x08, 0x02, 0x01, 0x00) +
            oid.map { it.toByte() }.toByteArray() +
            byteArrayOf(0x04, 0x00)
        return pem("PRIVATE KEY", Base64.getEncoder().encodeToString(der))
    }
}
