package com.geno1024.ai.gits.data

import java.nio.file.Path
import java.security.KeyPairGenerator
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test
    fun `the shape ssh-keygen writes carries ssh-keygen's own answer`(@TempDir dir: Path) {
        val file = dir.resolve("id_ed25519").toFile()
        file.writeText(ED25519_OPENSSH)

        assertEquals("SHA256:bzg2yT0M6WhMVEYO9lie9y2VbYRz3iHOp+pK9svFKX0", Ssh.fingerprintOf(file))
    }

    @Test
    fun `an old-style PEM RSA key carries ssh-keygen's own answer`(@TempDir dir: Path) {
        val file = dir.resolve("id_rsa").toFile()
        file.writeText(RSA_PKCS1_PEM)

        assertEquals("SHA256:a1hv22D95/HPMZkGyLoyCQxiEK5sT0MXcQgRWPNSfec", Ssh.fingerprintOf(file))
    }

    /** PKCS#8, wrapped the way a key file is wrapped. */
    private fun pem(der: ByteArray): ByteArray =
        ("-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(der) + "\n-----END PRIVATE KEY-----\n")
            .toByteArray()

    /** A throwaway ed25519 key, exactly as ssh-keygen writes one, with no passphrase. */
    private val ED25519_OPENSSH = """-----BEGIN OPENSSH PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
QyNTUxOQAAACCKcS7efHL7sP5H7NuoykMKucZSwGqNnTJx4wUscTtl7AAAAIgpj6pyKY+q
cgAAAAtzc2gtZWQyNTUxOQAAACCKcS7efHL7sP5H7NuoykMKucZSwGqNnTJx4wUscTtl7A
AAAEA/oh0Y4qs5pJ3v6f54tMbuGlUt7CO4uiqs5Xbm+6RHZYpxLt58cvuw/kfs26jKQwq5
xlLAao2dMnHjBSxxO2XsAAAAAAECAwQF
-----END OPENSSH PRIVATE KEY-----"""

    /** A throwaway RSA key in the PEM shape OpenSSH still reads, with no passphrase. */
    private val RSA_PKCS1_PEM = """-----BEGIN RSA PRIVATE KEY-----
MIIEpAIBAAKCAQEA07inePgpoMHMkSgd/XDFrqp/xrKiTwePjB+kFLzwVMizphtt
2sXGv559/Ce8mT7uC/6QrKmqhBe2fSMGxaeQfVMi7ONzw6nIpDTFD3QIgv4saeAN
R6/rviF3KXcnGds2ZUeZNoZz2HGRvZ7+3/22z50ce60YNokiO/+syNfVfjqudAdr
PyKMxLiiSP4YPboG8qpiXdnYkcOyeeSR0JZWsztZ4K1fwVortXIzkDvJi9smMhMH
585x1dAUqsArY3NuDGGDOVaJK2kUMpakcytl6+dp/uV6hsG664Omm8PD0L0bUtRK
wgNl/q1sWgq0dERSTt4a6CqLYN/ogT4hLO2IEQIDAQABAoIBABhGdotH9uvr9JLb
JO8jcEMJ4Qss/a+uh/6yvQs0q6Co/3Im+HZKUXK0jzDrdHQks0IUTs3BSWAKYP3h
l3vvrcE02E8NeD9BtxPeyg5uyTANyRVdn2AuE0tSRnmuZAESsZ60siQ+j8dMJD/3
3DTeAAk3dCXZD6rnF9IFy2+e0ad+iOFdfDjTMHC868YLfPbJjnwzNnnbueT/GhGy
mkCV28zh1yv2whtoLrRmVPTp0m4O3cJLfazP8kc1NM+2ch3AEWvoamfihnqs6Eas
rlcUkCcUuP7p104HvE+if82tboAV/NGpEk0OcYcPHSBkmgVLOiSLuHxRg2L/mKyV
nTeretECgYEA/S2jGWpLDn7ih4M7seCxiOTkaVClDUF1/vNx/Ga8fHYce+cX2Ecu
ReAZKIy/j1Jk2IScG1XHhOeHMjKMA7bgYPmo65mr6RhIRePivcFA/Y6W9Shdf+2B
v6qqZErU324Q3Y/h/wqZdsKgV9FwxLG9kv58i/2nOcTKvCrz5zlmAPkCgYEA1hS7
om+IhY60jhzoDcrt5H90GZoJ+EkBI2v8ywxP1gGL/Ks8uhsnHakXai0H7C1ope1E
OyXYubQSDPKErpRAbclEJMSoqZcN3Yq1IIsHlof0lEu1CSRMr5mR3KQR0J3VVvnK
yTiItEDTC+n8toCr/IjUMrMVWMw7EZHs/MNOndkCgYEAjIp063rRx24wGcGDta5h
XviQwtV9ieo4sho5wD7Xis+V6EHUPr7ktO09igD3pXu7d2XFsnbflqtfpUHh733o
+GwelQptH6vXEtT53RQWG6q3qceKf6U8TUVT6PCRUqYqvpNMhONBZWeM0rL0wntY
HO0f/iYEWlEfqWy+kDCQqyECgYEAlHf+hTHKwa1tpN2BRgeFoqGN2C+PWITw6Cr9
P6iDOc0K06nCTOOF5jkdxwIB65a2a9S4LDkcK/YpSpdq01R3tmwN+V32Bt9+uzV2
VmJ1Wb1iLvKuU++7y1C66wVSYZcEnPRR4el2TWRjuXCGVd+450PRvnOai2HgWVgV
vlqJDEkCgYB4PnZoj15D7TM7wIYClft9z0TvS7eNXIhDt8Zh6w2USkqYhV28h9Nb
xnoa5f96ZIRsleVf1ojMvT3jZBq3wkocHJ9PUFPBPXeSIUmAYqePaaCzYCkZTY0C
pK9CxnQr4jVUuBgNuDHKorFjcMEkzOIr/1PSAPY4izdCAuEW/nJmBg==
-----END RSA PRIVATE KEY-----"""
}
