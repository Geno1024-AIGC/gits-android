package com.geno1024.ai.gits.data

import com.geno1024.ai.gits.git.Answer
import com.geno1024.ai.gits.git.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The bridge between an exchange that blocks and a screen that answers.
 *
 * The whole of the mechanism is here: a question asked from another thread appears,
 * its answer goes back to whoever asked, and a question nobody will answer anymore is
 * let go of rather than left hanging. What the screen does with it is a screen's own
 * business and is not tested from here.
 */
class PromptSessionTest {

    @Test
    fun `a question appears, and its answer goes back to the one asking`() = runBlocking {
        val session = PromptSession()

        val asked = async(Dispatchers.IO) {
            session.questioner.ask(Question(Answer.YES_NO, listOf("fingerprint"), "Trust it?"))
        }
        val prompt = withTimeout(5_000) { session.questions.filterNotNull().first() }

        assertEquals(Answer.YES_NO, prompt.kind)
        assertEquals(listOf("fingerprint"), prompt.messages)
        assertEquals("Trust it?", prompt.prompt)

        prompt.answer("yes")

        assertEquals("yes", asked.await())
        assertNull(session.questions.value, "the question should be gone once answered")
    }

    @Test
    fun `letting go lets the asking go too`() = runBlocking {
        val session = PromptSession()

        val asked = async(Dispatchers.IO) {
            session.questioner.ask(Question(Answer.SECRET, emptyList(), "Passphrase"))
        }
        withTimeout(5_000) { session.questions.filterNotNull().first() }

        session.abandon()

        assertNull(asked.await(), "a question nobody will answer now should say nothing")
        assertNull(session.questions.value)
    }

    @Test
    fun `a second question while one is up gets nothing`() = runBlocking {
        val session = PromptSession()

        val first = async(Dispatchers.IO) {
            session.questioner.ask(Question(Answer.YES_NO, emptyList(), "First?"))
        }
        withTimeout(5_000) { session.questions.filterNotNull().first() }

        val second = async(Dispatchers.IO) {
            session.questioner.ask(Question(Answer.YES_NO, emptyList(), "Second?"))
        }
        assertNull(withTimeout(5_000) { second.await() })

        session.abandon()
        assertNull(withTimeout(5_000) { first.await() })
    }
}
