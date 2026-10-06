package com.geno1024.ai.gits.git

import org.eclipse.jgit.errors.TransportException
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.URIish
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What a transport is told when it asks in the middle of an exchange.
 *
 * The questions that happen here are the ones that cannot be asked beforehand: an SSH
 * host key only exists once the server has sent it, and the passphrase of a private
 * key belongs to a key nobody has tried yet. What matters is that the answer goes back
 * to the transport, that a dismissal reads as a refusal, and that a secret already
 * held is never replaced by a question.
 */
class InteractiveCredentialsTest {

    private val uri = URIish("ssh://git@example.com/owner/repo.git")

    @Test
    fun `a question carries what was said beside it, and the answer comes back`() {
        val asked = mutableListOf<Question>()
        val provider = provider(questioner = { asked += it; "yes" })

        val fingerprints = CredentialItem.InformationalMessage(
            "The authenticity of host 'example.com' can't be established.",
        )
        val question = CredentialItem.YesNoType("Trust this host key?")

        assertTrue(provider.get(uri, fingerprints, question))
        assertTrue(question.value, "saying yes should trust the key")
        val heard = asked.single()
        assertEquals(Answer.YES_NO, heard.kind)
        assertEquals("Trust this host key?", heard.prompt)
        assertEquals(1, heard.messages.size)
        assertTrue(heard.messages.single().contains("example.com"))
    }

    @Test
    fun `a dismissed question reads as a no rather than hanging the exchange`() {
        val provider = provider(questioner = { null })
        val question = CredentialItem.YesNoType("Trust this host key?")

        assertFalse(provider.get(uri, CredentialItem.InformationalMessage("fingerprints"), question))
        assertFalse(question.value, "nothing said should trust nothing")
    }

    @Test
    fun `a passphrase is asked for only when the store holds none`() {
        val asked = mutableListOf<Question>()
        val provider = provider(questioner = { asked += it; "hunter2" })
        val passphrase = CredentialItem.Password("Passphrase for key 'id_ed25519'")

        assertTrue(provider.get(uri, passphrase))
        assertArrayEquals("hunter2".toCharArray(), passphrase.value)
        assertEquals(Answer.SECRET, asked.single().kind)
    }

    @Test
    fun `a secret already held is used without asking anybody`() {
        val asked = mutableListOf<Question>()
        val provider = provider(
            username = "someone",
            secret = "stored-secret",
            questioner = { asked += it; "never" },
        )
        val user = CredentialItem.Username()
        val password = CredentialItem.Password()

        assertTrue(provider.get(uri, user, password))
        assertEquals("someone", user.value)
        assertArrayEquals("stored-secret".toCharArray(), password.value)
        assertTrue(asked.isEmpty(), "a stored secret should not be asked about again")
    }

    @Test
    fun `a provider that cannot ask refuses what would need asking`() {
        val provider = provider(questioner = null)

        assertFalse(provider.supports(CredentialItem.YesNoType("Trust this host key?")))
        assertTrue(provider.supports(CredentialItem.Username(), CredentialItem.Password()))
        assertFalse(provider.isInteractive())
        assertFalse(
            provider.get(
                uri,
                CredentialItem.InformationalMessage("fingerprints"),
                CredentialItem.YesNoType("Trust this host key?"),
            ),
            "with nobody to ask, an unknown host key must not be trusted",
        )
    }

    @Test
    fun `the question goes to the host the exchange is with, and no other`() {
        val provider = provider(questioner = { "yes" })

        assertThrows(TransportException::class.java) {
            provider.get(
                URIish("ssh://evil.example/owner/repo.git"),
                CredentialItem.YesNoType("Trust this host key?"),
            )
        }
    }

    private fun provider(
        username: String = "",
        secret: String = "",
        questioner: ((Question) -> String?)?,
    ) = Credentials.UsernamePassword(
        username,
        secret.toCharArray(),
        questioner?.let { asked -> Questioner { question -> asked(question) } },
    ).toProvider(setOf("example.com"))!!
}
