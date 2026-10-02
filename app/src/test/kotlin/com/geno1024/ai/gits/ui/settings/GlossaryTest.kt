package com.geno1024.ai.gits.ui.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class GlossaryTest {

    @Test
    fun `every term is written once and has both translations`() {
        GLOSSARY.forEach { entry ->
            assertTrue(entry.english.isNotBlank(), "a term has no English")
            assertTrue(entry.git.isNotBlank(), "${entry.english} has no Git translation")
            assertTrue(entry.proGit2.isNotBlank(), "${entry.english} has no Pro Git 2 translation")
        }

        assertEquals(
            GLOSSARY.size,
            GLOSSARY.map { it.english }.toSet().size,
            "a term is listed twice",
        )
    }

    /**
     * The rule the app follows: where the two references disagree, Git wins. The
     * translations are alternatives rather than sentences, so a Pro Git 2 wording is
     * allowed as long as Git's own translation already covers it — `已暂存的` merely
     * adds a 的 to Git's `已暂存`, while `贮藏` is a different word from Git's `储藏`.
     */
    @Test
    fun `the app never writes a wording only Pro Git 2 uses`() {
        val zh = zhStrings()
        val alternatives = Regex("[、，；]")

        GLOSSARY.forEach { entry ->
            if (entry.git == entry.proGit2) return@forEach
            val ours = entry.git.split(alternatives).filter { it.isNotBlank() }
            entry.proGit2.split(alternatives).filter { it.isNotBlank() }.forEach { theirs ->
                val shared = ours.any { it.contains(theirs) || theirs.contains(it) }
                if (shared) return@forEach
                assertFalse(
                    zh.contains(theirs),
                    "${entry.english}: Git does not say '$theirs', but values-zh-rCN does",
                )
            }
        }
    }

    private fun zhStrings(): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (path in listOf(
                "app/src/main/res/values-zh-rCN/strings.xml",
                "src/main/res/values-zh-rCN/strings.xml",
            )) {
                val file = File(dir, path)
                if (file.isFile) return file.readText()
            }
            dir = dir.parentFile
        }
        throw AssertionError("values-zh-rCN/strings.xml not found from ${File(".").absolutePath}")
    }
}
