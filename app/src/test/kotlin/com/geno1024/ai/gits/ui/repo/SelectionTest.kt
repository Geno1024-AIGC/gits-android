package com.geno1024.ai.gits.ui.repo

import com.geno1024.ai.gits.data.WorkingEntry
import com.geno1024.ai.gits.git.ChangeKind
import com.geno1024.ai.gits.git.WorkingChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SelectionTest {

    private fun change(path: String, kind: ChangeKind = ChangeKind.STAGED) = WorkingChange(path, kind)

    private fun entry(path: String) = WorkingEntry(
        path = path,
        name = path.substringAfterLast('/'),
        directory = false,
    )

    @Test
    fun `select all in the flat list takes the changes`() {
        val changes = listOf(change("a.txt"), change("b.txt"))

        assertEquals(listOf("a.txt", "b.txt"), selectable(true, changes, emptyList()))
    }

    @Test
    fun `select all in the folder list takes what the folder shows`() {
        // A file git has not noticed yet is still a file in this folder, and a stage
        // offered on a selection has to mean what the list is showing.
        val files = listOf(entry("a.txt"), entry("b.txt"), entry("quiet.txt"))

        assertEquals(
            listOf("a.txt", "b.txt", "quiet.txt"),
            selectable(false, listOf(change("a.txt")), files),
        )
    }

    @Test
    fun `inverting turns what is on show over`() {
        assertEquals(setOf("b.txt"), inverted(setOf("a.txt"), listOf("a.txt", "b.txt")))
    }

    @Test
    fun `inverting leaves a path chosen elsewhere where it was`() {
        // Never quietly drops a selection the list cannot see, so inverting twice puts
        // everything back exactly as it was.
        val visible = listOf("a.txt", "b.txt")
        val selected = setOf("elsewhere.md", "a.txt")

        assertEquals(setOf("elsewhere.md", "b.txt"), inverted(selected, visible))
        assertEquals(selected, inverted(inverted(selected, visible), visible))
    }
}
