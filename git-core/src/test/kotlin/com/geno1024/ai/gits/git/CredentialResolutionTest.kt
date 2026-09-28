package com.geno1024.ai.gits.git

import com.sun.net.httpserver.HttpServer
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Path

/**
 * Credentials are decided when a remote is used, not when a repository is opened.
 *
 * A remote can point anywhere, and can be edited between the moment the app opens a
 * folder and the moment the user presses push. The only honest place to decide which
 * secret a host should see is at the moment of the exchange, so these tests drive a
 * real transfer against a server that demands authentication and check what was asked
 * for, and against a local path and check that nothing was.
 */
@ExtendWith(IsolatedGitEnvironment::class)
class CredentialResolutionTest {

    @Test
    fun `a local remote never asks for credentials`(@TempDir dir: Path) {
        val remote = bareRemote(dir.toFile())
        val asked = mutableListOf<String?>()

        val pushed = Gits.init(dir.toFile()).use { gits ->
            gits.setIdentity(Identity("T", "t@x.invalid"))
            gits.addRemote("origin", remote.absolutePath)
            File(dir.toFile(), "a.txt").writeText("a\n")
            gits.addAll()
            gits.commit("first")

            gits.withCredentialsSource(askRecording(asked))
            gits.push("origin", listOf("refs/heads/main:refs/heads/main")).single().isSuccess
        }

        assertTrue(pushed, "a local push should succeed")
        assertEquals(emptyList<String?>(), asked, "a path has no host to authenticate to")
    }

    @Test
    fun `a remote that demands authentication is asked about its own host`(@TempDir dir: Path) {
        val asked = mutableListOf<String?>()
        ChallengingServer().use { server ->
            val uri = "http://localhost:${server.port}/owner/repo.git"
            Gits.init(dir.toFile()).use { gits ->
                gits.setIdentity(Identity("T", "t@x.invalid"))
                gits.addRemote("origin", uri)
                File(dir.toFile(), "a.txt").writeText("a\n")
                gits.addAll()
                gits.commit("first")

                gits.withCredentialsSource(
                    CredentialsSource { host ->
                        asked += host
                        Credentials.UsernamePassword("someone", "wrong".toCharArray())
                    },
                )
                // The server never accepts anything, so the push cannot succeed. What
                // matters is that the source was consulted, and about whom.
                runCatching { gits.push("origin", listOf("refs/heads/main:refs/heads/main")) }
            }
        }

        assertEquals(listOf("localhost"), asked.distinct(), "the remote's own host, and only it")
    }

    @Test
    fun `nothing is asked for while only reading`(@TempDir dir: Path) {
        val asked = mutableListOf<String?>()
        Gits.init(dir.toFile()).use { gits ->
            gits.addRemote("origin", "https://github.com/owner/repo.git")
            gits.withCredentialsSource(askRecording(asked))

            gits.remotes()
            gits.branches()
            gits.status()
            gits.log()

            assertEquals(emptyList<String?>(), asked, "reading a repository should never prompt")
        }
    }

    @Test
    fun `a source that refuses everything leaves a transfer unauthenticated`(@TempDir dir: Path) {
        ChallengingServer().use { server ->
            Gits.init(dir.toFile()).use { gits ->
                gits.setIdentity(Identity("T", "t@x.invalid"))
                File(dir.toFile(), "a.txt").writeText("a\n")
                gits.addAll()
                gits.commit("first")
                gits.addRemote("origin", "http://localhost:${server.port}/owner/repo.git")

                gits.withCredentialsSource(CredentialsSource { Credentials.None })
                val failure = assertThrows(Exception::class.java) {
                    gits.push("origin", listOf("refs/heads/main:refs/heads/main"))
                }
                assertTrue(
                    failure.message.orEmpty().isNotBlank(),
                    "a transfer that cannot authenticate should say so",
                )
            }
        }
    }

    @Test
    fun `the convenience sources behave as advertised`() {
        assertEquals(Credentials.None, CredentialsSource.None.forHost("github.com"))
        assertEquals(Credentials.None, CredentialsSource.None.forHost(null))
        val fixed = Credentials.UsernamePassword("someone", "secret".toCharArray())
        assertEquals(fixed, CredentialsSource.of(fixed).forHost("anywhere.example"))
    }

    @Test
    fun `a host comes from the remote, not from anything the caller says`() {
        assertEquals("github.com", URIish("https://github.com/o/r.git").credentialHost())
        assertEquals("github.com", URIish("ssh://git@github.com/o/r.git").credentialHost())
        assertEquals("localhost", URIish("http://localhost:8080/o/r.git").credentialHost())
        assertNull(URIish("/srv/git/repo.git").credentialHost())
    }

    private fun askRecording(asked: MutableList<String?>) = CredentialsSource { host ->
        asked += host
        Credentials.None
    }

    private fun bareRemote(root: File): File {
        val at = root.resolve("remote.git")
        Git.init().setBare(true).setDirectory(at).setInitialBranch("main").call().close()
        return at
    }

    /**
     * Answers every request with 401 and a Basic challenge.
     *
     * JGit only consults a credentials provider after a server asks for one, so a
     * server that says nothing cannot show what the app would have offered.
     */
    private class ChallengingServer : AutoCloseable {
        private val server: HttpServer = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
            createContext("/") { exchange ->
                exchange.responseHeaders.add("WWW-Authenticate", """Basic realm="gits-test"""")
                val body = "Unauthorized".toByteArray()
                exchange.sendResponseHeaders(401, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }

        val port: Int get() = server.address.port

        override fun close() = server.stop(0)
    }
}
