package com.geno1024.ai.gits.ui.repo

import com.geno1024.ai.gits.git.ChangeKind
import com.geno1024.ai.gits.git.Identity
import com.geno1024.ai.gits.git.WorkingChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CommitBlockTest {

    private fun ready() = RepoUiState(
        identity = Identity("Ada", "ada@gits.invalid"),
        changes = listOf(WorkingChange("a.txt", ChangeKind.STAGED)),
    )

    @Test
    fun `a commit ready to go says so by saying nothing`() {
        assertNull(ready().commitBlock("a subject"))
    }

    @Test
    fun `nothing staged is named first because it is the one with a shortcut`() {
        assertEquals(
            CommitBlock.NOTHING_STAGED,
            ready().copy(changes = emptyList()).commitBlock("a subject"),
        )
    }

    @Test
    fun `an empty message is said even when everything else is ready`() {
        assertEquals(CommitBlock.NO_MESSAGE, ready().commitBlock("   "))
    }

    @Test
    fun `busy outranks the rest because they can change in a moment`() {
        assertEquals(CommitBlock.BUSY, ready().copy(busy = true).commitBlock(""))
    }

    @Test
    fun `a missing identity is left to the field that asks for it`() {
        // The reason line is about the commit; the name and email have their own words
        // beside the box they belong to, and repeating them here would say it twice.
        assertNull(ready().copy(identity = null).commitBlock("a subject"))
    }
}
