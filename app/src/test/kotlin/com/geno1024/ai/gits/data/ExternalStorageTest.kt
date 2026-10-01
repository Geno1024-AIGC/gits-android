package com.geno1024.ai.gits.data

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The question asked before a repository is made somewhere.
 *
 * It is the one place where a wrong answer is worse than no answer at all: a refusal
 * arrives as an exception, and letting it out of here takes down the app rather than
 * the operation it was meant to spare.
 */
class ExternalStorageTest {

    @TempDir
    lateinit var temp: File

    @Test
    fun `a folder that takes a file is writable`() {
        assertTrue(ExternalStorage.isWritable(temp))
    }

    @Test
    fun `a folder not made yet is tested through the folder above it`() {
        assertTrue(
            ExternalStorage.isWritable(File(temp, "not-made-yet")),
            "the parent is where the folder would be made",
        )
    }

    @Test
    fun `the repository's own git folder is the one asked about, not the folder above it`() {
        val repository = File(temp, "repository")
        val git = File(repository, ".git")
        assertTrue(git.mkdirs())
        assertTrue(repository.setWritable(false, false))

        val writable = ExternalStorage.isWritable(repository)

        assertTrue(repository.setWritable(true, false))
        assertTrue(writable, "write permission on .git is what Git needs, and it is open")
    }

    @Test
    fun `a folder that refuses a file is refused rather than thrown out of`() {
        val locked = File(temp, "locked")
        assertTrue(locked.mkdirs())
        assertTrue(locked.setWritable(false, false))
        val enforced = runCatching {
            val probe = File(locked, "probe")
            if (probe.createNewFile()) probe.delete()
        }.isFailure

        val writable = ExternalStorage.isWritable(locked)

        assertTrue(locked.setWritable(true, false))
        if (enforced) {
            assertFalse(writable, "a folder that will not take a file is not writable")
        }
    }

    @Test
    fun `no probe file is left behind`() {
        ExternalStorage.isWritable(temp)

        assertTrue(
            temp.listFiles()!!.none { it.name.startsWith(".gits-write-probe-") },
            "a folder about to become a repository should not start with litter in it",
        )
    }

    @Test
    fun `a folder whose parent is not there names nowhere to write`() {
        assertFalse(ExternalStorage.isWritable(File(temp, "a/b/c")))
    }
}
