package com.geno1024.ai.gits.git

import com.geno1024.ai.gits.openpgp.KeyAlgorithm
import com.geno1024.ai.gits.openpgp.KeyGeneration
import com.geno1024.ai.gits.openpgp.KeyRings
import com.geno1024.ai.gits.openpgp.SecretKeyRing
import com.geno1024.ai.gits.openpgp.armoredPublicKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.io.File

@ExtendWith(IsolatedGitEnvironment::class)
/**
 * Which key signs, when more than one is held.
 *
 * The failure this guards against is quiet: two keys, one chosen by the order they
 * happened to be read in, and commits signed as the wrong person with nothing to say
 * so. A repository that names a key of its own must still win, because that is what
 * whoever made the repository asked for.
 *
 * The environment is isolated because a `user.signingkey` left in the machine's global
 * config reaches JGit as a real request, and then nothing here is choosing anything.
 */
class SigningKeyChoiceTest {

    private val passphrase = "correct horse battery staple".toCharArray()
    private val identity = Identity("Choice Test", "choice@gits.invalid")

    @Test
    fun `the chosen key signs when the repository names none`(@TempDir dir: File) {
        val first = ring("first <first@example.com>")
        val chosen = ring("chosen <chosen@example.com>")
        val repo = File(dir, "repo")

        val commit = commit(repo, signer(first, chosen, fallback = chosen.signingKeys.single().fingerprintHex))

        assertEquals(chosen.keys.first().keyIdHex, commit.signedByKeyId)
        assertTrue(commit.signaturePresent)
    }

    @Test
    fun `the chosen key is not simply the first one held`(@TempDir dir: File) {
        // Names are chosen so the first key's fingerprint does not sort first, making
        // this a real test of the choice rather than of the order keys happen to read in.
        val chosen = ring("chosen <chosen@example.com>")
        val later = ring("later <later@example.com>")
        val repo = File(dir, "repo")

        val commit = commit(repo, signer(later, chosen, fallback = chosen.signingKeys.single().fingerprintHex))

        assertEquals(chosen.keys.first().keyIdHex, commit.signedByKeyId)
    }

    @Test
    fun `a key the repository names beats the chosen key`(@TempDir dir: File) {
        val chosen = ring("chosen <chosen@example.com>")
        val asked = ring("asked <asked@example.com>")
        val repo = File(dir, "repo")

        Gits.init(repo, signer = signer(chosen, asked, fallback = chosen.signingKeys.single().fingerprintHex)).use { gits ->
            gits.setIdentity(identity)
            gits.setSigningKey(asked.signingKeys.single().fingerprintHex)
            File(repo, "a.txt").writeText("a\n")
            gits.addAll()

            assertEquals(asked.keys.first().keyIdHex, gits.commit("asked", sign = true).signedByKeyId)
        }
    }

    @Test
    fun `a chosen key that is gone does not stop a commit being signed`(@TempDir dir: File) {
        val ring = ring("kept <kept@example.com>")
        val repo = File(dir, "repo")

        val commit = commit(repo, signer(ring, fallback = "FFFFFFFFFFFFFFFF"))

        assertEquals(ring.signingKeys.single().keyIdHex, commit.signedByKeyId)
    }

    @Test
    fun `with one key and nothing chosen, that key signs`(@TempDir dir: File) {
        val ring = ring("only <only@example.com>")
        val repo = File(dir, "repo")

        val commit = commit(repo, signer(ring))

        assertEquals(ring.signingKeys.single().keyIdHex, commit.signedByKeyId)
    }

    @Test
    fun `an exported public key names the key that signed`(@TempDir dir: File) {
        val ring = ring("exporter <exporter@example.com>")
        val repo = File(dir, "repo")
        val commit = commit(repo, signer(ring))
        val exported = ring.armoredPublicKey()

        assertTrue(String(exported).contains("BEGIN PGP PUBLIC KEY BLOCK"))

        // The point of the export. A verifying party holding only this file can find the
        // key, so the signature above is attributable rather than merely present.
        val found = KeyRings.readPublicKeys(exported)
            .flatMap { ring -> ring.toList() }
            .map { keyIdOf(it.keyID) }
            .toSet()
        assertTrue(
            ring.keys.first().keyIdHex in found,
            "the exported keyring should still name the key, found $found",
        )
        assertTrue(
            commit.signedByKeyId in found,
            "the exported keyring should hold the key that signed, found $found",
        )
    }

    @Test
    fun `an exported public key carries no secret`(@TempDir dir: File) {
        val ring = ring("exporter <exporter@example.com>")
        assertTrue(!String(ring.armoredPublicKey()).contains("PRIVATE KEY BLOCK"))
    }

    @Test
    fun `a key generated with a passphrase reports as protected`(@TempDir dir: File) {
        val ring = ring("protected <protected@example.com>")
        assertTrue(
            ring.keys.all { it.isPassphraseProtected },
            "a key made here is under the passphrase that was asked for",
        )
    }

    // ------------------------------------------------------------------- helpers

    private fun ring(userId: String) =
        SecretKeyRing(KeyGeneration.generate(userId, KeyAlgorithm.ED25519, passphrase))

    private fun signer(vararg rings: SecretKeyRing, fallback: String? = null) = GitsSigner(
        keyrings = rings.toList(),
        passphrases = { passphrase.copyOf() },
        fallbackSpec = fallback,
    )

    private fun keyIdOf(id: Long) = String.format("%016X", id)

    private fun commit(repo: File, signer: GitsSigner): CommitResult =
        Gits.init(repo, signer = signer).use { gits ->
            gits.setIdentity(identity)
            File(repo, "a.txt").writeText("a\n")
            gits.addAll()
            gits.commit("first", sign = true)
        }
}
