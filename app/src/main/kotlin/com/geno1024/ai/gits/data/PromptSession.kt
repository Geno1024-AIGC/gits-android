package com.geno1024.ai.gits.data

import com.geno1024.ai.gits.git.Answer
import com.geno1024.ai.gits.git.Questioner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

/**
 * A question waiting on the person using the app, and the way to answer it.
 *
 * The exchange that asked is blocked until [answer] is called, so this is the whole
 * of what a screen needs: what was said beside the question, what was asked, and how
 * to say something back.
 */
class Prompting internal constructor(
    val kind: Answer,
    val messages: List<String>,
    val prompt: String?,
    private val future: CompletableFuture<String?>,
) {

    /** Hands back what was said, or null when the question is being dismissed. */
    fun answer(value: String?) {
        future.complete(value)
    }

    /** Waits for that answer. Only ever called from the thread the exchange runs on. */
    internal fun await(): String? = try {
        future.get()
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        null
    } catch (e: ExecutionException) {
        null
    }
}

/**
 * Puts the questions of a running exchange to the screen, one at a time.
 *
 * A transport asks, waits here, and goes on once somebody has answered — which is the
 * only way a host key's fingerprints can be shown before they are trusted, since they
 * do not exist until the server has sent them. One question at a time is not a
 * limitation so much as the shape of it: an exchange asks, is answered, and then asks
 * again if it must.
 *
 * Kept per screen so the question goes to whoever started the work, and is let go of
 * with them: a question nothing will ever answer would hold the exchange forever.
 */
class PromptSession {

    private val asking = MutableStateFlow<Prompting?>(null)

    /** The question on screen now, or null when nothing is asking. */
    val questions: StateFlow<Prompting?> = asking.asStateFlow()

    /** What an exchange is handed so it may ask. Runs on the exchange's own thread. */
    val questioner = Questioner { question ->
        val prompt = Prompting(
            kind = question.kind,
            messages = question.messages,
            prompt = question.prompt,
            future = CompletableFuture(),
        )
        if (!asking.compareAndSet(null, prompt)) return@Questioner null
        try {
            prompt.await()
        } finally {
            asking.compareAndSet(prompt, null)
        }
    }

    /** Lets go of a question nobody is going to answer now. */
    fun abandon() {
        val prompt = asking.value
        if (prompt != null && asking.compareAndSet(prompt, null)) {
            prompt.answer(null)
        }
    }
}
