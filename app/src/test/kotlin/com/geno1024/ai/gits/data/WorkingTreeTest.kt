package com.geno1024.ai.gits.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The screen's view of a repository's files, and the one thing it is not allowed to do.
 *
 * Every path handed here comes from the person tapping, so what matters as much as the
 * happy path is that a name cannot be used to write somewhere the repository is not.
 */
class WorkingTreeTest {

    @TempDir
    lateinit var root: File

    private fun file(relative: String): File =
        File(root, relative).also { it.parentFile.mkdirs(); it.writeText(relative) }

    @Test
    fun `folders are offered before files, and both by name`() {
        file("beta.txt")
        file("alpha.txt")
        File(root, "Zeta").mkdirs()
        File(root, "alpha").mkdirs()

        val entries = WorkingTree.entries(root, "")

        assertEquals(listOf("alpha", "Zeta", "alpha.txt", "beta.txt"), entries.map { it.name })
        assertEquals(listOf(true, true, false, false), entries.map { it.directory })
    }

    @Test
    fun `the repository's own git folder is not offered`() {
        File(root, ".git").mkdirs()
        File(root, ".git/objects").mkdirs()
        file("readme.md")

        val entries = WorkingTree.entries(root, "")

        assertEquals(listOf("readme.md"), entries.map { it.name })
    }

    @Test
    fun `a path is named from the repository root, wherever it is listed from`() {
        file("src/app/Main.kt")

        val entries = WorkingTree.entries(root, "src")

        assertEquals(listOf("src/app"), entries.map { it.path })
        assertEquals("app", entries.single().name)
    }

    @Test
    fun `a folder that is not there says so instead of listing nothing`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkingTree.entries(root, "nowhere/at/all")
        }
    }

    @Test
    fun `an empty file is created and stays empty`() {
        WorkingTree.create(root, "notes/todo.md", directory = false)

        val created = File(root, "notes/todo.md")
        assertTrue(created.isFile)
        assertEquals(0L, created.length())
        assertTrue(File(root, "notes").isDirectory, "the folder above it comes too")
    }

    @Test
    fun `a folder is created`() {
        WorkingTree.create(root, "src/util", directory = true)

        assertTrue(File(root, "src/util").isDirectory)
        assertEquals(0, File(root, "src/util").listFiles()!!.size)
    }

    @Test
    fun `a name that is already there is refused rather than emptied`() {
        file("keepme.txt")

        assertThrows(IllegalArgumentException::class.java) {
            WorkingTree.create(root, "keepme.txt", directory = false)
        }

        assertEquals("keepme.txt", File(root, "keepme.txt").readText(), "creating over it would lose it")
    }

    @Test
    fun `a path reaching outside the repository is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkingTree.create(root, "../escaped.txt", directory = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkingTree.entries(root, "..")
        }

        assertFalse(File(root.parentFile, "escaped.txt").exists())
    }

    @Test
    fun `nothing can be created in the repository's own git folder`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkingTree.create(root, ".git/hooks/pre-commit", directory = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkingTree.entries(root, ".git")
        }
    }

    @Test
    fun `an unnamed request is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkingTree.create(root, "   ", directory = false)
        }
    }
}
