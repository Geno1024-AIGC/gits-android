package com.geno1024.ai.gits.git

import com.geno1024.ai.gits.openpgp.KeyAlgorithm
import com.geno1024.ai.gits.openpgp.KeyGeneration
import com.geno1024.ai.gits.openpgp.SecretKeyRing
import com.geno1024.ai.gits.openpgp.armored
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit

/**
 * Checks signing against the real `git` binary.
 *
 * Our own reader agreeing with our own writer proves nothing: a signature can be
 * malformed in a way both ends tolerate, or correct in a way GnuPG rejects. `git` is
 * the consumer this app exists to serve, so it gets the deciding vote.
 */
@ExtendWith(IsolatedGitEnvironment::class)
class CommitSigningTest {

    private val userId = "Gits Signing Test <signing@gits.invalid>"
    private val passphrase = "correct horse battery staple".toCharArray()
    private val identity = Identity("Gits Test", "test@gits.invalid")

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `git verify-commit accepts a commit signed by this app`(
        algorithm: KeyAlgorithm,
        @TempDir dir: Path,
    ) {
        assumeTrue(gitAvailable() && gpgAvailable(), "git and gpg are required")

        val signed = signedCommit(algorithm, dir)
        val home = gnupgHomeWith(dir, signed.keyring)

        val (code, output) = git(signed.repo, mapOf("GNUPGHOME" to home), "verify-commit", signed.commit.id)
        assertEquals(0, code, "git rejected our signature:\n$output")
    }

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `git refuses the commit when it cannot see the public key`(
        algorithm: KeyAlgorithm,
        @TempDir dir: Path,
    ) {
        assumeTrue(gitAvailable() && gpgAvailable(), "git and gpg are required")

        val signed = signedCommit(algorithm, dir)

        // An empty keyring is what makes the passing test above meaningful: git has to
        // find the key in GnuPG, so a success there means the signature was actually
        // checked against the key rather than merely present on the commit.
        val (code, output) = git(
            signed.repo,
            mapOf("GNUPGHOME" to gnupgHome(dir.resolve("gnupg-empty"))),
            "verify-commit",
            signed.commit.id,
        )
        assertTrue(code != 0, "git must not verify a commit whose key it cannot see:\n$output")
    }

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `an explicitly configured signing key is honoured`(
        algorithm: KeyAlgorithm,
        @TempDir dir: Path,
    ) {
        assumeTrue(gitAvailable() && gpgAvailable(), "git and gpg are required")

        val signed = signedCommit(algorithm, dir)
        val key = signed.keyring.signingKeys.single()
        Gits.open(signed.repo).use { it.setSigningKey(key.fingerprintHex) }
        val home = gnupgHomeWith(dir, signed.keyring)

        val (code, output) = git(signed.repo, mapOf("GNUPGHOME" to home), "verify-commit", signed.commit.id)
        assertEquals(0, code, "git rejected our signature:\n$output")
    }

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `log names the signing key of a signed commit`(algorithm: KeyAlgorithm, @TempDir dir: Path) {
        val signed = signedCommit(algorithm, dir)

        Gits.open(signed.repo).use { gits ->
            val entry = gits.log().single()
            assertEquals(signed.commit.id, entry.id)
            assertTrue(entry.signaturePresent, "log should report a signature")
            assertEquals(signed.commit.signedByKeyId, entry.signedByKeyId, "log should name the key")
        }
    }

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `an unsigned commit reports neither a signature nor a key`(
        algorithm: KeyAlgorithm,
        @TempDir dir: Path,
    ) {
        Gits.init(dir.toFile()).use { gits ->
            gits.setIdentity(identity)
            File(dir.toFile(), "a.txt").writeText("a\n")
            gits.addAll()
            val commit = gits.commit("plain")

            assertEquals(false, commit.signaturePresent, "no signature was asked for")
            assertEquals(null, commit.signedByKeyId, "an unsigned commit has no key")
        }
    }

    // ------------------------------------------------------------------- helpers

    private class Signed(val repo: File, val commit: CommitResult, val keyring: SecretKeyRing)

    /** Signs with no `user.signingkey` set, which is how a freshly created repo looks. */
    private fun signedCommit(algorithm: KeyAlgorithm, dir: Path): Signed {
        val repo = dir.toFile().resolve("repo")
        val keyring = SecretKeyRing(KeyGeneration.generate(userId, algorithm, passphrase))
        val signer = GitsSigner(
            keyrings = listOf(keyring),
            passphrases = { passphrase.copyOf() },
        )

        val commit = Gits.init(repo, signer = signer).use { gits ->
            gits.setIdentity(identity)
            File(repo, "README.md").writeText("hello\n")
            gits.addAll()
            gits.commit("first", sign = true).also {
                assertTrue(it.signaturePresent, "the commit should carry a signature")
                assertNotNull(it.signedByKeyId, "the signature should name a key")
            }
        }
        return Signed(repo, commit, keyring)
    }

    /** A GnuPG home holding only [keyring]'s public key, as a verifying party would have. */
    private fun gnupgHomeWith(dir: Path, keyring: SecretKeyRing): String {
        val home = gnupgHome(dir.resolve("gnupg-signer"))
        val exported = Files.createTempFile(dir, "public", ".asc")
        Files.write(exported, keyring.ring.toCertificate().armored())
        val (code, output) = gpg(home, "--import", exported.toString())
        assertEquals(0, code, "could not stage the public key for git:\n$output")
        return home
    }

    private fun gnupgHome(at: Path): String {
        Files.createDirectories(at)
        Files.setPosixFilePermissions(at, PosixFilePermissions.fromString("rwx------"))
        return at.toFile().absolutePath
    }

    private fun git(dir: File, env: Map<String, String>, vararg args: String): Pair<Int, String> {
        val command = ProcessBuilder(listOf("git", "-C", dir.absolutePath) + args)
            .redirectErrorStream(true)
            .also { it.environment().putAll(env) }
            .start()
        val output = command.inputStream.bufferedReader().readText()
        assertTrue(command.waitFor(2, TimeUnit.MINUTES), "git ${args.joinToString(" ")} hung")
        return command.exitValue() to output
    }

    private fun gpg(home: String, vararg args: String): Pair<Int, String> {
        val command = ProcessBuilder(
            listOf("gpg", "--homedir", home, "--batch", "--no-tty", "--yes") + args,
        ).redirectErrorStream(true).start()
        val output = command.inputStream.bufferedReader().readText()
        assertTrue(command.waitFor(2, TimeUnit.MINUTES), "gpg hung")
        return command.exitValue() to output
    }

    private fun gitAvailable(): Boolean = toolAvailable("git", "--version")

    private fun gpgAvailable(): Boolean = toolAvailable("gpg", "--version")

    private fun toolAvailable(vararg command: String): Boolean = try {
        ProcessBuilder(command.toList())
            .redirectErrorStream(true)
            .start()
            .apply { waitFor(60, TimeUnit.SECONDS) }
            .exitValue() == 0
    } catch (_: Exception) {
        false
    }
}
