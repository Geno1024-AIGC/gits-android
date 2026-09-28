package com.geno1024.ai.gits.git

import com.geno1024.ai.gits.openpgp.DetachedSignatures
import com.geno1024.ai.gits.openpgp.KeyAlgorithm
import com.geno1024.ai.gits.openpgp.KeyGeneration
import com.geno1024.ai.gits.openpgp.KeyRings
import com.geno1024.ai.gits.openpgp.SecretKeyRing
import com.geno1024.ai.gits.openpgp.SignatureCheck
import com.geno1024.ai.gits.openpgp.armoredPublicKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
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
 * Checks commit signatures against the real `git`.
 *
 * The hard part is not the arithmetic, it is working out which bytes were signed: git
 * signs the commit *without* the `gpgsig` header, then stores the signature inside it.
 * Reconstructing that wrongly is invisible to a test that only uses this app's own keys
 * against this app's own signing — it signs and verifies the same wrong bytes happily
 * and reports success. So `git verify-commit` gives the verdict and the two have to
 * agree, and one case deliberately rewrites a commit's content while keeping its
 * signature, which is the case that actually matters.
 */
@ExtendWith(IsolatedGitEnvironment::class)
class CommitSignatureVerificationTest {

    private val userId = "Gits Verify <verify@gits.invalid>"
    private val passphrase = "correct horse battery staple".toCharArray()
    private val identity = Identity("Verify Test", "verify@gits.invalid")

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `git agrees a commit this app signed is good`(algorithm: KeyAlgorithm, @TempDir dir: Path) {
        assumeTrue(toolsAvailable(), "git and gpg are required")
        val (repo, keyring) = signedCommit(algorithm, dir)

        // The oracle. If this passes, the bytes handed to the verifier are the bytes git
        // signed, so the stripping of the signature header is right.
        val (code, output) = git(repo, gnupgHome(dir, keyring), "verify-commit", "HEAD")
        assertEquals(0, code, "git rejected a commit we call verified:\n$output")
    }

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `we agree with git that a commit this app signed is good`(
        algorithm: KeyAlgorithm,
        @TempDir dir: Path,
    ) {
        val (repo, keyring) = signedCommit(algorithm, dir)

        Gits.open(repo).use { gits ->
            gits.useVerificationKeys(keyring.publicKeys)
            val verified = assertInstanceOf(
                SignatureCheck.Verified::class.java,
                gits.log().single().signatureCheck,
            )
            assertEquals(keyring.signingKeys.single().keyIdHex, verified.keyIdHex)
        }
    }

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `rewriting the content under an unchanged signature is refused`(algorithm: KeyAlgorithm, @TempDir dir: Path) {
        assumeTrue(toolsAvailable(), "git and gpg are required")
        val (repo, keyring) = signedCommit(algorithm, dir)

        // The signature header is kept byte for byte while the tree it commits to
        // changes. This is the case the feature exists for, and it is not the same as
        // an unsigned commit: a signature is present and claims to be good.
        rewriteTreeKeepingSignature(repo)

        val (code, output) = git(repo, gnupgHome(dir, keyring), "verify-commit", "HEAD")
        assertNotEquals(0, code, "git accepted a rewritten commit:\n$output")

        Gits.open(repo).use { gits ->
            gits.useVerificationKeys(keyring.publicKeys)
            val entry = gits.log().single()
            assertTrue(entry.signaturePresent, "the rewritten commit still carries a signature")
            assertInstanceOf(SignatureCheck.Invalid::class.java, entry.signatureCheck)
        }
    }

    @ParameterizedTest
    @EnumSource(KeyAlgorithm::class)
    fun `a signature from a key we do not hold is not called good`(
        algorithm: KeyAlgorithm,
        @TempDir dir: Path,
    ) {
        val (repo, _) = signedCommit(algorithm, dir)
        val other = SecretKeyRing(
            KeyGeneration.generate("Someone Else <else@gits.invalid>", algorithm, passphrase),
        )

        Gits.open(repo).use { gits ->
            gits.useVerificationKeys(other.publicKeys)
            // "No key" rather than "invalid": the two call for different reactions, and
            // reporting a forgery to someone who simply lacks the key is a lie.
            assertInstanceOf(SignatureCheck.NoKey::class.java, gits.log().single().signatureCheck)
        }
    }

    @Test
    fun `a public key that was exported is enough to verify a commit`(@TempDir dir: Path) {
        val (repo, keyring) = signedCommit(KeyAlgorithm.ED25519, dir)

        // The realistic case: checking a commit using a public key handed over by
        // someone else, with no secret material anywhere in the process.
        val imported = KeyRings.publicKeys(keyring.armoredPublicKey())
        assertTrue(imported.isNotEmpty(), "the exported public key should be readable")

        Gits.open(repo).use { gits ->
            gits.useVerificationKeys(imported)
            assertInstanceOf(SignatureCheck.Verified::class.java, gits.log().single().signatureCheck)
        }
    }

    @Test
    fun `nothing is claimed when no keys are offered`(@TempDir dir: Path) {
        val (repo, _) = signedCommit(KeyAlgorithm.ED25519, dir)

        Gits.open(repo).use { gits ->
            val entry = gits.log().single()
            assertTrue(entry.signaturePresent, "the commit is still signed")
            // Null rather than a failure: there was nothing to check with, which is not
            // the same as having found the signature to be bad.
            assertNull(entry.signatureCheck)
        }
    }

    @Test
    fun `an unsigned commit reports no signature at all`(@TempDir dir: Path) {
        val repo = dir.resolve("plain").toFile()
        Gits.init(repo).use { gits ->
            gits.setIdentity(identity)
            File(repo, "a.txt").writeText("a\n")
            gits.addAll()
            val commit = gits.commit("plain")

            assertEquals(false, commit.signaturePresent)
            assertNull(commit.signatureCheck)
        }
    }

    @Test
    fun `a signature over some other bytes is invalid`(@TempDir dir: Path) {
        val ring = SecretKeyRing(
            KeyGeneration.generate("Signer <s@gits.invalid>", KeyAlgorithm.ED25519, passphrase),
        )
        val signature = ring.unlock(null, passphrase).sign("the real payload".toByteArray())
        val check = DetachedSignatures.verify(signature, "a different payload".toByteArray(), ring.publicKeys)
        assertInstanceOf(SignatureCheck.Invalid::class.java, check)
    }

    // ------------------------------------------------------------------- helpers

    private fun signedCommit(algorithm: KeyAlgorithm, dir: Path): Pair<File, SecretKeyRing> {
        val repo = dir.resolve("repo-$algorithm").toFile()
        val keyring = SecretKeyRing(KeyGeneration.generate(userId, algorithm, passphrase))
        Gits.init(repo, signer = GitsSigner(listOf(keyring), { passphrase.copyOf() })).use { gits ->
            gits.setIdentity(identity)
            File(repo, "README.md").writeText("hello\n")
            gits.addAll()
            assertTrue(gits.commit("first", sign = true).signaturePresent, "the commit should be signed")
        }
        return repo to keyring
    }

    /**
     * Points HEAD at a commit whose tree has been changed and whose signature has not.
     *
     * Written through `hash-object` and `update-ref` so the result is a real commit
     * object carrying the original `gpgsig` header verbatim. Making a new commit instead
     * would produce an unsigned one, which is a different and much easier thing to spot.
     */
    private fun rewriteTreeKeepingSignature(repo: File) {
        File(repo, "sneaky.txt").writeText("added after signing\n")
        assertEquals(0, git(repo, mapOf(), "add", "sneaky.txt").first, "could not stage")

        val tree = git(repo, mapOf(), "write-tree").second.trim()
        val original = git(repo, mapOf(), "cat-file", "commit", "HEAD").second
        val rewritten = original.replaceFirst(Regex("^tree [0-9a-f]+", RegexOption.MULTILINE), "tree $tree")

        val written = git(repo, mapOf(), "hash-object", "-w", "-t", "commit", "--stdin", input = rewritten)
        assertEquals(0, written.first, "could not write the rewritten commit:\n${written.second}")
        val newId = written.second.trim()

        assertEquals(0, git(repo, mapOf(), "update-ref", "HEAD", newId).first, "could not move HEAD")
    }

    private fun toolsAvailable(): Boolean = available("git") && available("gpg")

    private fun available(tool: String): Boolean = runCatching {
        ProcessBuilder(tool, "--version").start().waitFor() == 0
    }.getOrDefault(false)

    /** A GnuPG home holding only this key's public key, as a verifying party would have. */
    private fun gnupgHome(dir: Path, keyring: SecretKeyRing): Map<String, String> {
        val home = Files.createDirectories(dir.resolve("gnupg-verify"))
        Files.setPosixFilePermissions(home, PosixFilePermissions.fromString("rwx------"))
        val exported = Files.write(dir.resolve("public-verify.asc"), keyring.armoredPublicKey())
        val (code, output) = gpg(home.toFile().absolutePath, "--import", exported.toString())
        assertEquals(0, code, "could not stage the public key for git:\n$output")
        return mapOf("GNUPGHOME" to home.toFile().absolutePath)
    }

    private fun git(dir: File, env: Map<String, String>, vararg args: String, input: String? = null): Pair<Int, String> {
        val process = ProcessBuilder(listOf("git", "-C", dir.absolutePath) + args)
            .redirectErrorStream(true)
            .also { it.environment().putAll(env) }
            .start()
        if (input != null) {
            process.outputStream.bufferedWriter().use { it.write(input) }
        } else {
            process.outputStream.close()
        }
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(2, TimeUnit.MINUTES)) { "git ${args.joinToString(" ")} hung" }
        return process.exitValue() to output
    }

    private fun gpg(home: String, vararg args: String): Pair<Int, String> {
        val process = ProcessBuilder(
            listOf("gpg", "--homedir", home, "--batch", "--no-tty", "--yes") + args,
        ).redirectErrorStream(true).start()
        process.outputStream.close()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(2, TimeUnit.MINUTES)) { "gpg hung" }
        return process.exitValue() to output
    }
}
