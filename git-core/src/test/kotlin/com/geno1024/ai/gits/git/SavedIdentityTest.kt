package com.geno1024.ai.gits.git

import com.geno1024.ai.gits.git.Gits.Companion.init
import com.geno1024.ai.gits.git.Gits.Companion.open
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * How a saved identity reaches a repository.
 *
 * The rule that matters is the one about not overwriting. An identity is typed once and
 * then applied wherever it can be, and an app that quietly rewrites the author of a
 * repository someone already set up would misattribute every commit in it.
 */
@ExtendWith(IsolatedGitEnvironment::class)
class SavedIdentityTest {

    private val saved = Identity("Ada", "ada@example.invalid")

    @Test
    fun `a repository with no identity is given the saved one`(@TempDir dir: Path) {
        val repo = dir.resolve("fresh").toFile()
        init(repo).use {
            it.adoptSavedIdentity(saved)
            assertEquals(saved, it.identity())
        }
    }

    @Test
    fun `a repository with its own identity is left alone`(@TempDir dir: Path) {
        val repo = dir.resolve("cloned").toFile()
        val theirs = Identity("Grace", "grace@example.invalid")
        init(repo).use { it.setIdentity(theirs) }

        open(repo).use {
            it.adoptSavedIdentity(saved)
            assertEquals(theirs, it.identity(), "an identity the user chose must not be overwritten")
        }
    }

    @Test
    fun `a repository with only a name is not given a different name`(@TempDir dir: Path) {
        val repo = dir.resolve("partial").toFile()
        init(repo).use { gits ->
            gits.setIdentity(Identity("Grace", ""))
            gits.adoptSavedIdentity(saved)
            // The name stands, and the missing half is filled in, because half an
            // identity is not a usable one.
            assertEquals(Identity("Grace", "ada@example.invalid"), gits.identity())
        }
    }

    @Test
    fun `nothing is written when no identity has been saved`(@TempDir dir: Path) {
        val repo = dir.resolve("unset").toFile()
        init(repo).use {
            it.adoptSavedIdentity(null)
            assertNull(it.identity(), "a repository must not gain an identity nobody gave")
        }
    }

    @Test
    fun `adopting twice does not change the answer`(@TempDir dir: Path) {
        val repo = dir.resolve("twice").toFile()
        init(repo).use { gits ->
            gits.adoptSavedIdentity(saved)
            gits.adoptSavedIdentity(Identity("Someone Else", "other@example.invalid"))
            assertEquals(saved, gits.identity())
        }
    }

    @Test
    fun `the saved identity lands in the repository config where git can see it`(@TempDir dir: Path) {
        val repo = dir.resolve("config").toFile()
        init(repo).use { it.adoptSavedIdentity(saved) }

        // Read the file as text, so this fails if the value is only in the app's
        // memory and never reaches the file a commit would actually be written from.
        val config = File(repo, ".git/config").readText()
        assertTrue(config.contains("name = Ada"), "name missing from .git/config:\n$config")
        assertTrue(config.contains("email = ada@example.invalid"), "email missing from .git/config:\n$config")
    }
}
