package com.geno1024.ai.gits.openpgp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit

/**
 * Checks the signatures this module produces against the reference implementation.
 *
 * A self round trip proves only that BouncyCastle agrees with itself, and this project
 * exists because Git tooling outside Android will be reading these signatures. So the
 * armor, the packet layout and the algorithm ids are all verified by handing the output
 * to `gpg` and requiring it to agree.
 */
class GpgInteropTest {

    private val userId = "Gits Interop <interop@gits.invalid>"
    private val passphrase = "interop-passphrase".toCharArray()

    /**
     * Stands in for a commit object: headers, a blank line, then the message. Git signs
     * exactly these bytes, so a signature that verifies here verifies on a commit.
     */
    private val commitLike = """
        tree 4b825dc642cb6eb9a060e54bf8d69288fbee4904
        parent 7c9b1f2e6a5d4c3b2a1908f7e6d5c4b3a291807f
        author Gits Interop <interop@gits.invalid> 1700000000 +0000
        committer Gits Interop <interop@gits.invalid> 1700000000 +0000

        a commit message
    """.trimIndent().toByteArray()

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `gpg accepts the detached signature`(algorithm: KeyAlgorithm, @TempDir dir: Path) {
        assumeTrue(gpgAvailable(), "gpg is not installed")

        val ring = KeyGeneration.generate(userId, algorithm, passphrase)
        val publicKey = Files.write(dir.resolve("public.asc"), ring.toCertificate().armored())
        val (importExit, importOutput) = gpg(dir, "--import", publicKey.toString())
        assertEquals(0, importExit, "could not import our public key into gpg:\n$importOutput")

        val session = SecretKeyRing(ring).unlock(null, passphrase)
        val payloadFile = Files.write(dir.resolve("payload"), commitLike)
        val signatureFile = Files.write(dir.resolve("payload.asc"), session.sign(commitLike))

        val (exitCode, output) = gpg(
            dir,
            "--pinentry-mode", "loopback",
            "--passphrase", passphrase.concatToString(),
            "--verify", signatureFile.toString(), payloadFile.toString(),
        )

        assertEquals(0, exitCode, "gpg rejected a signature from $algorithm:\n$output")
        assertTrue(
            output.contains("Good signature") || output.contains("完好的签名"),
            "expected gpg to report a good signature, got:\n$output",
        )
    }

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `gpg imports a generated secret keyring`(algorithm: KeyAlgorithm, @TempDir dir: Path) {
        assumeTrue(gpgAvailable(), "gpg is not installed")

        val ring = KeyGeneration.generate(userId, algorithm, passphrase)
        val secretKey = Files.write(dir.resolve("secret.asc"), ring.armored())

        val (exitCode, output) = gpg(
            dir,
            "--pinentry-mode", "loopback",
            "--passphrase", passphrase.concatToString(),
            "--import", secretKey.toString(),
        )
        assertEquals(0, exitCode, "gpg refused our generated keyring:\n$output")

        val listed = gpg(dir, "--list-secret-keys", userId).second
        // gpg prints its own algorithm labels, e.g. "rsa2048" and "ed25519".
        assertTrue(
            listed.contains(algorithm.name.lowercase()),
            "gpg did not list a ${algorithm.name} key:\n$listed",
        )
        // A keyring with no user id is skipped by gpg, which would silently lose the key.
        assertTrue(
            listed.contains("Gits Interop"),
            "gpg did not retain the user id:\n$listed",
        )
    }

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `gpg reports a tampered payload as bad`(algorithm: KeyAlgorithm, @TempDir dir: Path) {
        assumeTrue(gpgAvailable(), "gpg is not installed")

        val ring = KeyGeneration.generate(userId, algorithm, passphrase)
        val publicKey = Files.write(dir.resolve("public.asc"), ring.toCertificate().armored())
        gpg(dir, "--import", publicKey.toString())

        val session = SecretKeyRing(ring).unlock(null, passphrase)
        val signatureFile = Files.write(dir.resolve("payload.asc"), session.sign(commitLike))
        val tampered = Files.write(dir.resolve("payload"), "not the signed bytes".toByteArray())

        val (exitCode, _) = gpg(
            dir,
            "--pinentry-mode", "loopback",
            "--passphrase", passphrase.concatToString(),
            "--verify", signatureFile.toString(), tampered.toString(),
        )

        assertNotEquals(0, exitCode, "gpg accepted a signature over different bytes")
    }

    private fun gpgAvailable(): Boolean = runCatching {
        ProcessBuilder("gpg", "--version").start().waitFor() == 0
    }.getOrDefault(false)

    /** Runs gpg against an isolated GNUPGHOME so the developer's real keyring is untouched. */
    private fun gpg(dir: Path, vararg args: String): Pair<Int, String> {
        val home = Files.createDirectories(dir.resolve("gnupg"))
        Files.setPosixFilePermissions(
            home,
            PosixFilePermissions.fromString("rwx------"),
        )
        val homePath = home.toFile().absolutePath

        val command = buildList {
            add("gpg")
            add("--homedir"); add(homePath)
            add("--batch")
            add("--no-tty")
            addAll(args)
        }
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(60, TimeUnit.SECONDS)) process.destroy()
        return process.exitValue() to output
    }
}
