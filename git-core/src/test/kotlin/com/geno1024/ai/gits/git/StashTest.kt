package com.geno1024.ai.gits.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Putting work aside without committing it.
 *
 * A stash is the one operation here that removes things from the working tree on
 * purpose, so what these care about is that the work comes back whole: shelved when
 * asked, restored on request, and never quietly dropped along the way.
 */
@ExtendWith(IsolatedGitEnvironment::class)
class StashTest {

    private val identity = Identity("Gits Test", "test@gits.invalid")

    @Test
    fun `a stash shelves the changes and the list holds it`(@TempDir dir: Path) {
        Gits.init(dir.toFile()).use { gits ->
            gits.setIdentity(identity)
            val notes = File(dir.toFile(), "notes.txt")
            notes.writeText("first\n")
            gits.addAll()
            gits.commit("first")

            notes.writeText("first\nsecond\n")
            val stashed = gits.stash()

            assertNotNull(stashed, "an edited file is something to shelve")
            assertEquals("first\n", notes.readText(), "the working tree goes back to how it was")
            assertTrue(gits.status().isClean, "a stash shelves everything it took")
            assertEquals(
                listOf(stashed!!.ref),
                gits.stashList().map { it.ref },
                "the new stash is the newest one in the list",
            )
            assertEquals(stashed.id, gits.stashList().single().id)
        }
    }

    @Test
    fun `an unchanged working tree has nothing to shelve`(@TempDir dir: Path) {
        Gits.init(dir.toFile()).use { gits ->
            gits.setIdentity(identity)
            File(dir.toFile(), "notes.txt").writeText("first\n")
            gits.addAll()
            gits.commit("first")

            assertNull(gits.stash(), "there is nothing here to put aside")
            assertTrue(gits.stashList().isEmpty(), "nothing was shelved, so nothing is listed")
        }
    }

    @Test
    fun `popping a stash puts the work back and forgets it`(@TempDir dir: Path) {
        Gits.init(dir.toFile()).use { gits ->
            gits.setIdentity(identity)
            val notes = File(dir.toFile(), "notes.txt")
            notes.writeText("first\n")
            gits.addAll()
            gits.commit("first")

            notes.writeText("first\nsecond\n")
            gits.stash()

            gits.stashPop()

            assertEquals("first\nsecond\n", notes.readText(), "the shelved edit is back")
            assertTrue(gits.stashList().isEmpty(), "a popped stash is no longer in the list")
            assertEquals(
                listOf(WorkingChange("notes.txt", ChangeKind.MODIFIED)),
                gits.status().changes,
                "the work comes back as an edit waiting to be committed, not as a commit",
            )
        }
    }

    @Test
    fun `a stash can be restored without being forgotten`(@TempDir dir: Path) {
        Gits.init(dir.toFile()).use { gits ->
            gits.setIdentity(identity)
            val notes = File(dir.toFile(), "notes.txt")
            notes.writeText("first\n")
            gits.addAll()
            gits.commit("first")

            notes.writeText("first\nsecond\n")
            gits.stash()

            gits.stashApply("stash@{0}")

            assertEquals("first\nsecond\n", notes.readText())
            assertEquals(1, gits.stashList().size, "applying is not the same as popping")
        }
    }

    @Test
    fun `dropping takes the stash that was named`(@TempDir dir: Path) {
        Gits.init(dir.toFile()).use { gits ->
            gits.setIdentity(identity)
            val notes = File(dir.toFile(), "notes.txt")
            notes.writeText("first\n")
            gits.addAll()
            gits.commit("first")

            notes.writeText("first\nsecond\n")
            val older = gits.stash()!!
            notes.writeText("first\nthird\n")
            val newer = gits.stash()!!

            // Names are positions, so they are read off the list rather than kept from
            // when each stash was made: a name taken earlier would have moved.
            val listed = gits.stashList()
            assertEquals(listOf(newer.id, older.id), listed.map { it.id }, "the newest comes first")

            gits.stashDrop(listed.first().ref)

            assertEquals(listOf(older.id), gits.stashList().map { it.id })
        }
    }

    @Test
    fun `untracked files are left alone unless asked for`(@TempDir dir: Path) {
        Gits.init(dir.toFile()).use { gits ->
            gits.setIdentity(identity)
            val tracked = File(dir.toFile(), "tracked.txt")
            tracked.writeText("first\n")
            gits.addAll()
            gits.commit("first")
            val loose = File(dir.toFile(), "loose.txt")
            loose.writeText("not committed\n")

            tracked.writeText("first\nsecond\n")
            gits.stash()

            assertEquals("first\n", tracked.readText(), "the tracked edit was shelved")
            assertTrue(loose.exists(), "an untracked file is not swept up unasked")

            val untracked = gits.stash(includeUntracked = true)
            assertNotNull(untracked, "the loose file is now something to shelve")
            assertTrue(!loose.exists(), "it went with the stash")

            gits.stashPop()
            assertEquals("not committed\n", loose.readText(), "and it came back with it")
        }
    }

    @Test
    fun `a name that is not a stash is refused rather than guessed`(@TempDir dir: Path) {
        Gits.init(dir.toFile()).use { gits ->
            gits.setIdentity(identity)
            File(dir.toFile(), "notes.txt").writeText("first\n")
            gits.addAll()
            gits.commit("first")

            val failure = runCatching { gits.stashDrop("refs/heads/main") }.exceptionOrNull()
            assertNotNull(failure, "dropping something that is not a stash should fail")
            assertTrue(
                failure is IllegalArgumentException,
                "and say so in a sentence rather than in a stack trace: $failure",
            )
        }
    }
}
