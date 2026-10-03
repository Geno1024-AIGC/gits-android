package com.geno1024.ai.gits.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * The patch behind a line of history.
 *
 * The two commits worth reading carefully are the first one, which has no parent to be
 * read against, and a later one, where reading it against nothing would show the whole
 * file again instead of what that commit did.
 */
@ExtendWith(IsolatedGitEnvironment::class)
class CommitDiffTest {

    private val identity = Identity("Gits Test", "test@gits.invalid")

    @Test
    fun `the first commit is read against nothing and brings the whole file`(@TempDir dir: Path) {
        Gits.init(dir.toFile()).use { gits ->
            gits.setIdentity(identity)
            File(dir.toFile(), "notes.txt").writeText("first\n")
            gits.addAll()
            gits.commit("add notes")

            val patch = gits.commitDiff(gits.log().single().id)

            assertTrue(patch.contains("notes.txt"), "the patch names the file: $patch")
            assertTrue(patch.contains("+first"), "the file arrived whole: $patch")
        }
    }

    @Test
    fun `a later commit is read against its parent and shows only its own line`(@TempDir dir: Path) {
        Gits.init(dir.toFile()).use { gits ->
            gits.setIdentity(identity)
            val file = File(dir.toFile(), "notes.txt")
            file.writeText("first\n")
            gits.addAll()
            gits.commit("add notes")
            file.writeText("first\nsecond\n")
            gits.addAll()
            gits.commit("add a second line")

            val patch = gits.commitDiff(gits.log().first().id)

            assertTrue(patch.contains("+second"), "what the commit did is there: $patch")
            assertFalse(patch.contains("+first"), "what it did not do is not: $patch")
        }
    }

    @Test
    fun `a rename is followed to the name it ended up with`(@TempDir dir: Path) {
        Gits.init(dir.toFile()).use { gits ->
            gits.setIdentity(identity)
            val before = File(dir.toFile(), "old.txt")
            before.writeText("moved\n")
            gits.addAll()
            gits.commit("add old")
            assertTrue(before.renameTo(File(dir.toFile(), "new.txt")))
            gits.addAll()
            gits.commit("rename it")

            val patch = gits.commitDiff(gits.log().first().id)

            assertTrue(patch.contains("new.txt"), "the new name is in the patch: $patch")
        }
    }

    @Test
    fun `a name that does not name a commit is refused rather than guessed at`(@TempDir dir: Path) {
        Gits.init(dir.toFile()).use { gits ->
            assertThrows(IllegalArgumentException::class.java) { gits.commitDiff("nope") }
        }
    }

    @Test
    fun `the same patch comes back for a short id and for the whole one`(@TempDir dir: Path) {
        Gits.init(dir.toFile()).use { gits ->
            gits.setIdentity(identity)
            File(dir.toFile(), "notes.txt").writeText("first\n")
            gits.addAll()
            gits.commit("add notes")
            val id = gits.log().single().id

            assertEquals(gits.commitDiff(id), gits.commitDiff(id.take(7)), "a short id works")
        }
    }
}
