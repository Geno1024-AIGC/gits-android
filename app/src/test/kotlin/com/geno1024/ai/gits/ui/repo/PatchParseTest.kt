package com.geno1024.ai.gits.ui.repo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Turning a patch back into the parts a screen can draw.
 *
 * The paperwork above the first `@@` is the part that goes wrong quietly: the `+++`
 * naming the same file reads as an added line to anything that splits on `+` alone,
 * and a file heading that never appears leaves the hunks below naming nothing.
 */
class PatchParseTest {

    private fun kindsOf(patch: String) = parsePatch(patch).map { it.kind }

    @Test
    fun `the file is a heading and its paperwork is not drawn`() {
        val patch = """
            diff --git a/notes.txt b/notes.txt
            index 1234567..89abcde 100644
            --- a/notes.txt
            +++ b/notes.txt
            @@ -1,2 +1,3 @@
             first
            +second
        """.trimIndent()

        val lines = parsePatch(patch)

        assertEquals(DiffLine(DiffLineKind.FILE, "notes.txt"), lines.first())
        assertEquals(
            listOf(DiffLineKind.FILE, DiffLineKind.HUNK, DiffLineKind.CONTEXT, DiffLineKind.ADDED),
            lines.map { it.kind },
            "the index and the ---/+++ pair are not lines of the patch: $lines",
        )
    }

    @Test
    fun `the marker stays on a line that came or went, and goes on one that stayed`() {
        val patch = """
            diff --git a/notes.txt b/notes.txt
            @@ -1,2 +1,2 @@
            -gone
            +here
             kept
        """.trimIndent()

        val lines = parsePatch(patch)

        assertEquals("-gone", lines[2].text, "the minus is what says which it was")
        assertEquals("+here", lines[3].text)
        assertEquals("kept", lines[4].text, "a space marking a line that stayed is not part of it")
    }

    @Test
    fun `what is said about the file survives as something to draw`() {
        val patch = """
            diff --git a/old.txt b/new.txt
            similarity index 100%
            rename from old.txt
            rename to new.txt
        """.trimIndent()

        val lines = parsePatch(patch)

        assertEquals(
            listOf(DiffLineKind.FILE, DiffLineKind.META, DiffLineKind.META),
            lines.map { it.kind },
            "a rename has no hunks, and the two lines saying so are the whole answer",
        )
        assertEquals("rename from old.txt", lines[1].text)
    }

    @Test
    fun `a second file in the same patch starts its own heading`() {
        val patch = """
            diff --git a/one.txt b/one.txt
            index 1111111..2222222 100644
            --- a/one.txt
            +++ b/one.txt
            @@ -1 +1 @@
            -a
            +b
            diff --git a/two.txt b/two.txt
            index 3333333..4444444 100644
            --- a/two.txt
            +++ b/two.txt
            @@ -1 +1 @@
            -c
            +d
        """.trimIndent()

        val headings = parsePatch(patch).filter { it.kind == DiffLineKind.FILE }

        assertEquals(listOf("one.txt", "two.txt"), headings.map { it.text })
    }

    @Test
    fun `a path holding a space is read as the name and not as two words`() {
        val patch = "diff --git a/my notes.txt b/my notes.txt\n--- a/my notes.txt\n+++ b/my notes.txt\n"

        assertEquals(DiffLine(DiffLineKind.FILE, "my notes.txt"), parsePatch(patch).first())
    }

    @Test
    fun `nothing is drawn for nothing`() {
        assertEquals(emptyList<DiffLine>(), parsePatch(""))
        assertEquals(emptyList<DiffLine>(), parsePatch("   "))
    }

    @Test
    fun `the newline ending the patch is not an empty line of it`() {
        val patch = "diff --git a/one.txt b/one.txt\n@@ -1 +1 @@\n-a\n+b\n"

        val lines = parsePatch(patch)

        assertEquals(4, lines.size, "the trailing newline adds nothing: $lines")
        assertEquals(DiffLineKind.ADDED, lines.last().kind)
        assertTrue(lines.none { it.kind == DiffLineKind.CONTEXT && it.text.isEmpty() })
    }

    @Test
    fun `something said after the hunks is still something to draw`() {
        val patch = """
            diff --git a/pic.png b/pic.png
            index 1234567..89abcde 100644
            Binary files a/pic.png and b/pic.png differ
        """.trimIndent()

        assertEquals(
            listOf(DiffLineKind.FILE, DiffLineKind.META),
            kindsOf(patch),
            "a binary has no hunks, and saying so is the answer",
        )
    }
}
