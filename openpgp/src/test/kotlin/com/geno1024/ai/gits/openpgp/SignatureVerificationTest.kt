package com.geno1024.ai.gits.openpgp

import org.bouncycastle.openpgp.PGPPublicKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit

/**
 * Checks that verification accepts a good signature and refuses everything else.
 *
 * Both directions matter, and the second is the one that is easy to fake. Our own
 * signing agreeing with our own verification proves nothing: the same mistake in both —
 * a wrong hash, a signature over the wrong bytes — makes a round trip succeed and every
 * signature anyone else made fail. So `gpg` is asked directly whether the payload and
 * signature this module produced are a pair it accepts, and our answer has to match.
 */
class SignatureVerificationTest {

    private val userId = "Verifier <verifier@gits.invalid>"
    private val passphrase = "verifier passphrase".toCharArray()
    private val payload = "tree deadbeef\nauthor me\n\nthe bytes git signs\n".toByteArray()

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `a signature this module made verifies`(algorithm: KeyAlgorithm) {
        val ring = ring(algorithm)
        assertInstanceOf(SignatureCheck.Verified::class.java, check(ring, payload))
    }

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `gpg accepts the same signature over the same payload`(algorithm: KeyAlgorithm, @TempDir dir: Path) {
        assumeTrue(gpgAvailable(), "gpg is not installed")
        val ring = ring(algorithm)

        // The pair we hand gpg, judged by an implementation that did not make it. This
        // is what says the bytes we verify against are the bytes that were signed.
        val publicKey = Files.write(dir.resolve("public.asc"), ring.armoredPublicKey())
        assertEquals(0, gpg(dir, "--import", publicKey.toString()).first, "gpg would not take our public key")

        val data = Files.write(dir.resolve("payload"), payload)
        val signature = Files.write(dir.resolve("payload.asc"), sign(ring, payload))
        val (exitCode, output) = gpg(dir, "--verify", signature.toString(), data.toString())

        assertEquals(0, exitCode, "gpg rejected a signature we then call valid:\n$output")
    }

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `gpg refuses a changed byte, and so do we`(algorithm: KeyAlgorithm, @TempDir dir: Path) {
        assumeTrue(gpgAvailable(), "gpg is not installed")
        val ring = ring(algorithm)
        val changed = payload.copyOf().also { it[it.size - 2] = 'X'.code.toByte() }

        val publicKey = Files.write(dir.resolve("public.asc"), ring.armoredPublicKey())
        gpg(dir, "--import", publicKey.toString())
        val signature = Files.write(dir.resolve("payload.asc"), sign(ring, payload))
        val data = Files.write(dir.resolve("payload"), changed)
        val (exitCode, _) = gpg(dir, "--verify", signature.toString(), data.toString())

        assertNotEquals(0, exitCode, "gpg accepted a signature over a changed payload")
        assertInstanceOf(SignatureCheck.Invalid::class.java, check(ring, changed))
    }

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `changing one byte fails here too`(algorithm: KeyAlgorithm) {
        val ring = ring(algorithm)
        val changed = payload.copyOf().also { it[0] = 'X'.code.toByte() }
        assertInstanceOf(SignatureCheck.Invalid::class.java, check(ring, changed))
    }

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `a signature made over something else is invalid`(algorithm: KeyAlgorithm) {
        val ring = ring(algorithm)
        val other = "a different commit entirely\n".toByteArray()
        assertInstanceOf(SignatureCheck.Invalid::class.java, check(ring, other))
    }

    @Test
    fun `a signature from a key we do not hold is missing, not invalid`() {
        val mine = ring(KeyAlgorithm.ED25519)
        val theirs = ring(KeyAlgorithm.ED25519)
        assertNotEquals(
            mine.signingKeys.single().keyIdHex,
            theirs.signingKeys.single().keyIdHex,
        )

        // The ids do not line up, so nothing was checked. Calling this invalid would
        // report a forgery to someone who has simply not got the right key.
        val check = DetachedSignatures.verify(sign(theirs, payload), payload, mine.publicKeys)
        assertInstanceOf(SignatureCheck.NoKey::class.java, check)
    }

    @Test
    fun `a signature with no keys offered at all is missing`() {
        val ring = ring(KeyAlgorithm.ED25519)
        val check = DetachedSignatures.verify(sign(ring, payload), payload, emptyList<PGPPublicKey>())
        assertInstanceOf(SignatureCheck.NoKey::class.java, check)
    }

    @Test
    fun `a signature under a key we hold is checked rather than assumed`() {
        val ring = ring(KeyAlgorithm.ED25519)

        // The real key verifies, which is what makes the invalid cases above meaningful:
        // the same code path returns Invalid when the bytes do not match.
        assertInstanceOf(SignatureCheck.Verified::class.java, check(ring, payload))

        val forged = payload.copyOf().also { it[it.size - 1] = 'Z'.code.toByte() }
        assertInstanceOf(SignatureCheck.Invalid::class.java, check(ring, forged))
    }

    @Test
    fun `bytes that are not a signature are malformed`() {
        val ring = ring(KeyAlgorithm.ED25519)
        val check = DetachedSignatures.verify("this is not a signature".toByteArray(), payload, ring.publicKeys)
        assertInstanceOf(SignatureCheck.Malformed::class.java, check)
    }

    @Test
    fun `the issuer is reported without being checked`() {
        val ring = ring(KeyAlgorithm.ED25519)
        val issuer = DetachedSignatures.issuerOf(sign(ring, payload))
        assertEquals(ring.signingKeys.single().keyIdHex, issuer?.keyIdHex)
    }

    // ------------------------------------------------------------------- helpers

    private fun ring(algorithm: KeyAlgorithm) =
        SecretKeyRing(KeyGeneration.generate(userId, algorithm, passphrase))

    private fun sign(ring: SecretKeyRing, bytes: ByteArray) = ring.unlock(null, passphrase).sign(bytes)

    private fun check(ring: SecretKeyRing, bytes: ByteArray) =
        DetachedSignatures.verify(sign(ring, payload), bytes, ring.publicKeys)

    private fun gpgAvailable(): Boolean = runCatching {
        ProcessBuilder("gpg", "--version").start().waitFor() == 0
    }.getOrDefault(false)

    /** Runs gpg against an isolated GNUPGHOME so the developer's real keyring is untouched. */
    private fun gpg(dir: Path, vararg args: String): Pair<Int, String> {
        val home = Files.createDirectories(dir.resolve("gnupg"))
        Files.setPosixFilePermissions(home, PosixFilePermissions.fromString("rwx------"))
        val command = buildList {
            add("gpg")
            add("--homedir"); add(home.toFile().absolutePath)
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
