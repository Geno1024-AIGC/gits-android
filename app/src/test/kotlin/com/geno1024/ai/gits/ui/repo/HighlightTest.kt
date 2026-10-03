package com.geno1024.ai.gits.ui.repo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HighlightTest {

    private fun spans(source: String, language: CodeLanguage) = findTokens(source, language)

    private fun runs(source: String, language: CodeLanguage) =
        spans(source, language).map { source.substring(it.start, it.end) }

    @Test
    fun `a file goes to the family its extension names`() {
        assertEquals(CodeLanguage.C_LIKE, CodeLanguage.of("app/src/main/Main.kt"))
        assertEquals(CodeLanguage.PYTHON, CodeLanguage.of("tools/run.py"))
        assertEquals(CodeLanguage.SHELL, CodeLanguage.of("scripts/build.sh"))
        assertEquals(CodeLanguage.JSON, CodeLanguage.of("package.json"))
        assertEquals(CodeLanguage.XML, CodeLanguage.of("app/src/main/AndroidManifest.xml"))
    }

    @Test
    fun `a file nobody names is still the common elements`() {
        assertEquals(CodeLanguage.GENERIC, CodeLanguage.of("notes.txt"))
        assertEquals(CodeLanguage.GENERIC, CodeLanguage.of("README"))
    }

    @Test
    fun `a line comment runs to the end of its line`() {
        val source = "val a = 1 // note\nval b = 2"
        val comment = spans(source, CodeLanguage.C_LIKE).single { it.kind == TokenKind.COMMENT }
        assertEquals("// note", source.substring(comment.start, comment.end))
    }

    @Test
    fun `a comment marker inside a string is text`() {
        val source = "\"// not a comment\""
        assertEquals(listOf(TokenKind.STRING), spans(source, CodeLanguage.C_LIKE).map { it.kind })
        assertEquals(listOf(source), runs(source, CodeLanguage.C_LIKE))
    }

    @Test
    fun `a word the language has spent is a run and its neighbour is not`() {
        val source = "fun fill()"
        val first = spans(source, CodeLanguage.C_LIKE).first()
        assertEquals(TokenKind.KEYWORD, first.kind)
        assertEquals("fun", source.substring(first.start, first.end))
        assertEquals(listOf("fun"), runs(source, CodeLanguage.C_LIKE))
    }

    @Test
    fun `an unclosed comment runs to the end of the file`() {
        val source = "/* forever"
        assertEquals(listOf(source), runs(source, CodeLanguage.C_LIKE))
    }

    @Test
    fun `an annotation is a run of its own`() {
        val source = "@Override fun run()"
        val first = spans(source, CodeLanguage.C_LIKE).first()
        assertEquals(TokenKind.ANNOTATION, first.kind)
        assertEquals("@Override", source.substring(first.start, first.end))
    }

    @Test
    fun `a number is a run of its own`() {
        val source = "val n = 42"
        val number = spans(source, CodeLanguage.C_LIKE).single { it.kind == TokenKind.NUMBER }
        assertEquals("42", source.substring(number.start, number.end))
    }

    @Test
    fun `a file of no family still gets the common comments strings numbers and words`() {
        val source = "if (set == \"on\") return 42 # maybe"
        val runs = runs(source, CodeLanguage.GENERIC)
        assertTrue("if" in runs)
        assertTrue("return" in runs)
        assertTrue("\"on\"" in runs)
        assertTrue("42" in runs)
        assertTrue("# maybe" in runs)
    }

    @Test
    fun `a file with nothing worth colouring comes back with nothing`() {
        assertEquals(emptyList<TokenSpan>(), spans("nothing to see here", CodeLanguage.GENERIC))
    }

    @Test
    fun `a file past the limit is not worth a pass over`() {
        assertTrue(worthHighlighting("x".repeat(MAX_HIGHLIGHT)))
        assertFalse(worthHighlighting("x".repeat(MAX_HIGHLIGHT + 1)))
    }
}
