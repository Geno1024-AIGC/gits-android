package com.geno1024.ai.gits.ui.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The two questions a new-repository dialog asks before its button may be pressed.
 *
 * Both are pure so that the answer can be pinned down here rather than on a device,
 * because the answer decides where files are written: getting it wrong does not show
 * up as a wrong string on screen but as a repository somewhere the user did not look.
 */
class NewRepositoryTest {

    private val picked = File("/storage/emulated/0/Documents/work")

    @Test
    fun `a picked folder with no name under it is the place itself`() {
        assertEquals(picked, repositoryTarget(picked, ""))
        assertEquals(picked, repositoryTarget(picked, "   "))
    }

    @Test
    fun `a name under a picked folder goes under that folder`() {
        assertEquals(File(picked, "mine"), repositoryTarget(picked, "mine"))
        assertEquals(File(picked, "mine"), repositoryTarget(picked, "  mine  "))
    }

    @Test
    fun `a picked folder and a path of its own are not both needed`() {
        // The pick is the answer. Demanding a name on top of it would turn the one
        // gesture that already said where into a half-answer.
        assertEquals(picked, repositoryTarget(picked, ""))
    }

    @Test
    fun `nothing picked and nothing typed names no place`() {
        assertNull(repositoryTarget(null, ""))
        assertNull(repositoryTarget(null, "   "))
    }

    @Test
    fun `a name with nothing picked under it names no place`() {
        // The app's working directory is `/`, so a bare name would land where nothing
        // may be written. Better to say no than to write somewhere nobody looked.
        assertNull(repositoryTarget(null, "mine"))
    }

    @Test
    fun `a path typed with nothing picked is taken at face value`() {
        assertEquals(File("/data/local/tmp/mine"), repositoryTarget(null, "/data/local/tmp/mine"))
    }

    @Test
    fun `a path of its own beats the folder picked`() {
        // Someone pasting an absolute path has already answered the question the pick
        // would have been for; nesting it under the pick would be answering for them.
        assertEquals(
            File("/data/local/tmp/mine"),
            repositoryTarget(picked, "/data/local/tmp/mine"),
        )
    }

    @Test
    fun `a clone address in path form ends in the repository`() {
        assertEquals("repo", repositoryNameOf("https://github.com/owner/repo.git"))
        assertEquals("repo", repositoryNameOf("https://github.com/owner/repo"))
    }

    @Test
    fun `a clone address in scp form ends in the repository`() {
        assertEquals("repo", repositoryNameOf("git@github.com:owner/repo.git"))
        assertEquals("repo", repositoryNameOf("git@github.com:repo.git"))
    }

    @Test
    fun `a trailing separator still leaves the repository named`() {
        assertEquals("repo", repositoryNameOf("https://github.com/owner/repo/"))
    }

    @Test
    fun `an address with no repository in it names none`() {
        assertNull(repositoryNameOf(""))
        assertNull(repositoryNameOf("   "))
        assertNull(repositoryNameOf("https://github.com/"))
    }

    @Test
    fun `an address that would name the folder above or below is refused`() {
        assertNull(repositoryNameOf("https://github.com/owner/."))
        assertNull(repositoryNameOf("https://github.com/owner/.."))
    }
}
