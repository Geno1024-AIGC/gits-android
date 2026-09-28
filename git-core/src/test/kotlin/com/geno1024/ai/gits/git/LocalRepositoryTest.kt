package com.geno1024.ai.gits.git

import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * The everyday path through the app: open a repository, edit, commit, publish, and
 * come back later.
 *
 * Local paths stand in for real remotes. A file remote exercises the same JGit
 * transport code that a network remote uses, minus authentication, and it does so
 * without a server, so a failure here points at the app rather than at a fixture.
 */
@ExtendWith(IsolatedGitEnvironment::class)
class LocalRepositoryTest {

    private val identity = Identity("Gits Test", "test@gits.invalid")

    @Test
    fun `a new repository starts on its initial branch with nothing to commit`(
        @TempDir dir: Path,
    ) {
        Gits.init(dir.toFile(), initialBranch = "trunk").use { gits ->
            assertEquals("trunk", gits.currentBranch())
            assertTrue(gits.status().isClean, "a fresh repository has nothing staged")
            assertTrue(gits.log().isEmpty(), "a fresh repository has no history")
            assertNull(gits.identity(), "identity is not set until the user sets it")
        }
    }

    @Test
    fun `an edit is untracked until staged, and staged until committed`(@TempDir dir: Path) {
        Gits.init(dir.toFile()).use { gits ->
            gits.setIdentity(identity)
            val file = File(dir.toFile(), "notes.txt")
            file.writeText("first\n")

            val untracked = gits.status()
            assertEquals(
                listOf(WorkingChange("notes.txt", ChangeKind.UNTRACKED)),
                untracked.changes,
            )

            gits.addAll()
            assertEquals(
                listOf(WorkingChange("notes.txt", ChangeKind.STAGED)),
                gits.status().staged,
            )

            gits.commit("add notes")
            assertTrue(gits.status().isClean, "committing should leave nothing behind")
            assertEquals("add notes", gits.log().single().subject)
        }
    }

    @Test
    fun `status separates a staged edit from an unstaged one`(@TempDir dir: Path) {
        Gits.init(dir.toFile()).use { gits ->
            gits.setIdentity(identity)
            val file = File(dir.toFile(), "notes.txt")
            file.writeText("first\n")
            gits.addAll()
            gits.commit("add notes")

            file.writeText("first\nsecond\n")
            assertEquals(
                listOf(WorkingChange("notes.txt", ChangeKind.MODIFIED)),
                gits.status().changes,
                "an edit in the working tree is not staged",
            )

            gits.addAll()
            file.writeText("first\nsecond\nthird\n")
            val byPath = gits.status().changes.associate { it.path to it.kind }
            assertEquals(ChangeKind.MODIFIED, byPath["notes.txt"], "the index copy is older than the file")
        }
    }

    @Test
    fun `log reads newest first and can be narrowed to a path`(@TempDir dir: Path) {
        Gits.init(dir.toFile()).use { gits ->
            gits.setIdentity(identity)
            File(dir.toFile(), "a.txt").writeText("a\n")
            gits.addAll()
            val first = gits.commit("touch a")
            File(dir.toFile(), "b.txt").writeText("b\n")
            gits.addAll()
            val second = gits.commit("touch b")

            val all = gits.log()
            assertEquals(listOf(second.id, first.id), all.map { it.id }, "newest commit comes first")
            assertEquals("test@gits.invalid", all.first().authorEmail)

            assertEquals(
                listOf("touch b"),
                gits.log(path = "b.txt").map { it.subject },
                "a path filter should exclude commits that never touched it",
            )
        }
    }

    @Test
    fun `branches track the checked out one and where it points`(@TempDir dir: Path) {
        Gits.init(dir.toFile(), initialBranch = "main").use { gits ->
            gits.setIdentity(identity)
            File(dir.toFile(), "a.txt").writeText("a\n")
            gits.addAll()
            gits.commit("first")

            gits.createBranch("feature")
            val names = gits.branches().map { it.name }
            assertTrue(names.containsAll(listOf("main", "feature")), "both branches should be listed")

            assertEquals("feature", gits.checkout("feature"))
            assertEquals("feature", gits.currentBranch())
            assertEquals(
                "feature",
                gits.branches().first { it.isCurrent }.name,
                "exactly one branch is checked out",
            )
        }
    }

    @Test
    fun `blame attributes each line to the commit that wrote it`(@TempDir dir: Path) {
        Gits.init(dir.toFile()).use { gits ->
            gits.setIdentity(identity)
            val file = File(dir.toFile(), "story.txt")
            file.writeText("alpha\n")
            gits.addAll()
            val first = gits.commit("write alpha")
            file.writeText("alpha\nbeta\n")
            gits.addAll()
            val second = gits.commit("write beta")

            val blame = gits.blame("story.txt")
            assertEquals(listOf("alpha", "beta"), blame.map { it.text })
            assertEquals(first.id, blame[0].commitId, "alpha belongs to the first commit")
            assertEquals(second.id, blame[1].commitId, "beta belongs to the second")
        }
    }

    @Test
    fun `remotes are listed, changed and removed`(@TempDir dir: Path) {
        val remote = bareRemote(dir.toFile(), "remote.git")
        Gits.init(dir.toFile()).use { gits ->
            gits.setIdentity(identity)
            gits.addRemote("origin", remote.absolutePath)

            val origin = gits.remotes().single { it.name == "origin" }
            assertTrue(origin.uris.isNotEmpty(), "a remote reports where it fetches from")

            gits.removeRemote("origin")
            assertTrue(gits.remotes().isEmpty(), "a removed remote should be gone")
        }
    }

    @Test
    fun `a commit can be pushed, cloned and pulled back`(@TempDir dir: Path) {
        val remote = bareRemote(dir.toFile(), "remote.git")
        val work = dir.toFile().resolve("work")
        val copy = dir.toFile().resolve("copy")

        val firstId = Gits.init(work, initialBranch = "main").use { gits ->
            gits.setIdentity(identity)
            gits.addRemote("origin", remote.absolutePath)
            File(work, "a.txt").writeText("shared\n")
            gits.addAll()
            gits.commit("shared file").also { commit ->
                val branch = gits.currentBranch()
                gits.push("origin", listOf("refs/heads/$branch:refs/heads/main"))
                    .single()
                    .let { assertTrue(it.isSuccess, "push should succeed: ${it.message}") }
            }.id
        }

        Gits.clone(remote.absolutePath, copy).use { clone ->
            assertEquals(listOf("shared file"), clone.log().map { it.subject })
            assertEquals(
                setOf("origin"),
                clone.remotes().map { it.name }.toSet(),
                "a clone configures its origin",
            )

            // The second writer's work, to be fetched by the first. A clone inherits no
            // identity from its origin, so it has to be told who is committing.
            clone.setIdentity(identity)
            File(copy, "a.txt").writeText("shared\nextra\n")
            clone.addAll()
            clone.commit("extend file")
            clone.push("origin", listOf("refs/heads/main:refs/heads/main"))
        }

        val tip = Gits.open(work).use { gits ->
            gits.fetch("origin")
            gits.pull("origin", "main")
            assertEquals(
                listOf("extend file", "shared file"),
                gits.log().map { it.subject },
                "the pull should bring in the other clone's commit",
            )
            assertEquals("shared\nextra\n", File(work, "a.txt").readText())
            assertTrue(
                gits.log().map { it.id }.contains(firstId),
                "the pulled history should still contain the first commit",
            )
            gits.log().first().id
        }

        assertEquals(
            tip,
            Git.open(remote).use { it.repository.resolve("refs/heads/main").name },
            "the bare remote should hold the tip of what was pushed to it",
        )
    }

    @Test
    fun `clone of an empty remote produces an empty repository`(@TempDir dir: Path) {
        val remote = bareRemote(dir.toFile(), "empty.git")
        val copy = dir.toFile().resolve("copy")

        Gits.clone(remote.absolutePath, copy).use { gits ->
            assertTrue(gits.status().isClean)
            assertTrue(gits.log().isEmpty(), "an empty remote has no commits to show")
        }
    }

    @Test
    fun `opening a directory that is not a repository fails clearly`(@TempDir dir: Path) {
        val plain = dir.toFile().resolve("plain")
        plain.mkdirs()
        val failure = runCatching { Gits.open(plain) }.exceptionOrNull()
        assertNotNull(failure, "opening a non-repository should fail rather than invent one")
        assertFalse(
            failure is IllegalStateException && failure.message.orEmpty().contains("repository not found").not(),
            "expected a not-found failure but got $failure",
        )
    }

    /**
     * A bare repository standing in for a server. Its HEAD has to name the branch the
     * test pushes, or a clone has nothing to check out and fails for a reason that has
     * nothing to do with the app.
     */
    private fun bareRemote(root: File, name: String, initialBranch: String = "main"): File {
        val at = root.resolve(name)
        Git.init().setBare(true).setDirectory(at).setInitialBranch(initialBranch).call().close()
        return at
    }
}
