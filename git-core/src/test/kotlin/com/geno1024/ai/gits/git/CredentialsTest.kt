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
 * A token is worth stealing, so what these credentials may be handed to matters more
 * than whether they are correct.
 */
class CredentialsTest {

    private val secret = "ghp_example".toCharArray()
    private val credentials = Credentials.UsernamePassword("someone", secret)

    @Test
    fun `no credentials means no provider at all`() {
        assertEquals(null, Credentials.None.toProvider(emptySet()))
    }

    @Test
    fun `a configured host gets the username and the secret`() {
        val provider = credentials.toProvider(setOf("github.com"))!!
        val user = CredentialItem.Username()
        val password = CredentialItem.Password()

        assertTrue(provider.get(URIish("https://github.com/o/r.git"), user, password))
        assertEquals("someone", user.value)
        assertArrayEquals(secret, password.value)
    }

    @Test
    fun `the host is matched without regard to case`() {
        val provider = credentials.toProvider(setOf("github.com"))!!
        val user = CredentialItem.Username()

        assertTrue(provider.get(URIish("https://GitHub.COM/o/r.git"), user))
        assertEquals("someone", user.value)
    }

    @Test
    fun `an unexpected host is refused rather than answered`() {
        val provider = credentials.toProvider(setOf("github.com"))!!
        val user = CredentialItem.Username()
        val password = CredentialItem.Password()

        val failure = assertThrows(TransportException::class.java) {
            provider.get(URIish("https://evil.example/o/r.git"), user, password)
        }
        assertTrue(
            failure.message!!.contains("evil.example"),
            "the message should name the host that was refused: ${failure.message}",
        )
        assertEquals(null, user.value, "a refused request must leave the item unfilled")
    }

    @Test
    fun `a request with no host at all is refused`() {
        val provider = credentials.toProvider(setOf("github.com"))!!
        val user = CredentialItem.Username()

        assertThrows(TransportException::class.java) {
            provider.get(URIish("file:///tmp/whatever"), user)
        }
        assertEquals(null, user.value)
    }

    @Test
    fun `one host does not unlock another`() {
        val provider = credentials.toProvider(setOf("github.com", "gitlab.com"))!!
        val user = CredentialItem.Username()

        assertTrue(provider.get(URIish("https://gitlab.com/o/r.git"), user))
        assertThrows(TransportException::class.java) {
            provider.get(URIish("https://bitbucket.org/o/r.git"), CredentialItem.Username())
        }
    }

    @Test
    fun `the transport clearing its buffer does not destroy the caller's secret`() {
        val provider = credentials.toProvider(setOf("github.com"))!!

        val first = CredentialItem.Password()
        provider.get(URIish("https://github.com/o/r.git"), first)
        first.value = null // what the transport does when the exchange ends
        first.clear()

        val second = CredentialItem.Password()
        provider.get(URIish("https://github.com/o/r.git"), second)
        assertArrayEquals(
            secret,
            second.value,
            "a second push should still be able to authenticate",
        )
    }

    @Test
    fun `wipe clears the caller's own copy`() {
        val own = Credentials.UsernamePassword("someone", "ghp_example".toCharArray())
        own.wipe()
        val provider = own.toProvider(setOf("github.com"))!!
        val password = CredentialItem.Password()
        provider.get(URIish("https://github.com/o/r.git"), password)
        assertTrue(
            password.value!!.none { it == 'h' },
            "a wiped secret should hand out nothing resembling the original: " +
                String(password.value!!),
        )
    }

    @Test
    fun `host extraction ignores a uri with no authority`() {
        assertEquals(null, URIish("file:///tmp/x").credentialHost())
        assertEquals(null, URIish("https:///x").credentialHost())
        assertEquals("github.com", URIish("https://github.com/o/r.git").credentialHost())
    }

    @Test
    fun `a secret is filed under the host the transport asks for`() {
        assertEquals("github.com", "https://github.com/o/r.git".toCredentialHost())
        assertEquals("github.com", "git@github.com:owner/repository.git".toCredentialHost())
        assertEquals("localhost", "http://localhost:8080/o/r".toCredentialHost())
    }

    @Test
    fun `an address written as just a host is still a host`() {
        assertEquals("github.com", "github.com".toCredentialHost())
        assertEquals("github.com", "github.com/owner/repository".toCredentialHost())
        assertEquals("github.com", "github.com:8080".toCredentialHost())
        assertEquals("github.com", "GitHub.com/owner/repository".toCredentialHost())
        assertEquals("github.com", "  github.com/owner/repository  ".toCredentialHost())
    }

    @Test
    fun `a local path has no host to file anything under`() {
        assertEquals("", "/srv/git/repository.git".toCredentialHost())
        assertEquals("", "file:///srv/git/repository.git".toCredentialHost())
        assertEquals("", "".toCredentialHost())
    }

    @Test
    fun `an ssh address is told apart from every other`() {
        assertTrue("git@example.com:owner/repository.git".isSshAddress())
        assertTrue("ssh://git@example.com/owner/repository.git".isSshAddress())
        assertTrue("  git@example.com:owner/repository.git  ".isSshAddress())
        assertTrue("git+ssh://example.com/owner/repository.git".isSshAddress())
        assertFalse("https://github.com/owner/repository.git".isSshAddress())
        // A name in the user slot does not make a page over https into SSH.
        assertFalse("https://git@example.com/owner/repository.git".isSshAddress())
        assertFalse("/srv/git/repository.git".isSshAddress())
        assertFalse("file:///srv/git/repository.git".isSshAddress())
        assertFalse("".isSshAddress())
    }
}
