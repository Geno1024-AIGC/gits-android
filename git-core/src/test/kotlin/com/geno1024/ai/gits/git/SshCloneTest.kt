package com.geno1024.ai.gits.git

import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.sshd.SshdSessionFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Cloning over SSH against a server on this machine.
 *
 * The interesting moment is the first one: an unknown host key arrives with its
 * fingerprints, and somebody has to be asked whether to trust it before a byte of the
 * repository is exchanged. A server of our own is the only place that moment can be
 * produced on purpose — twice, in fact, since a host already in known_hosts must not
 * be asked about again, and a question answered with nothing at all must stop the
 * clone rather than wave it through.
 *
 * The transport is pointed at the test's own home directory so the trust that comes
 * out of these tests is written there and nowhere near anyone's real known_hosts.
 */
@ExtendWith(IsolatedGitEnvironment::class)
@Timeout(60)
class SshCloneTest {

    @TempDir
    lateinit var work: File

    private val asked = mutableListOf<Question>()

    @BeforeEach
    fun keepTheTransportInItsOwnHome() {
        val home = work.resolve("home")
        File(home, ".ssh").mkdirs()
        SshSessionFactory.setInstance(
            SshdSessionFactory().apply {
                setHomeDirectory(home)
                setSshDirectory(File(home, ".ssh"))
            },
        )
    }

    @AfterEach
    fun handTheTransportBack() {
        SshSessionFactory.setInstance(null)
        asked.clear()
    }

    @Test
    fun `an unknown host key is asked about once, and then remembered`() {
        serving { uri ->
            Gits.clone(uri, work.resolve("first"), credentials = offering("yes")).close()

            assertEquals(1, asked.size, "the first meeting with a host is the one to ask about")
            val question = asked.single()
            assertEquals(Answer.YES_NO, question.kind)
            assertTrue(
                question.messages.any { it.contains("127.0.0.1") },
                "the host should be named beside the fingerprints: ${question.messages}",
            )
            assertTrue(File(work.resolve("first"), ".git").exists(), "the clone should have gone through")

            Gits.clone(uri, work.resolve("second"), credentials = offering("yes")).close()

            assertEquals(
                1,
                asked.size,
                "a host already in known_hosts is trusted without asking a second time",
            )
        }
    }

    @Test
    fun `a host key nobody will trust stops the clone`() {
        serving { uri ->
            val target = work.resolve("refused")

            assertThrows(Exception::class.java) {
                Gits.clone(uri, target, credentials = offering(null)).close()
            }

            assertEquals(1, asked.size, "the question should still have been asked")
            assertFalse(File(target, ".git").exists(), "an untrusted host must not be cloned from")
        }
    }

    /** Offers a stored secret for the server, and one answer for every question. */
    private fun offering(answer: String?) = CredentialsSource.of(
        Credentials.UsernamePassword(
            "git",
            "hunter2".toCharArray(),
            Questioner { question ->
                asked += question
                answer
            },
        ),
    )

    /**
     * Serves one repository over SSH, and hands the address it can be cloned from to
     * [block]. The command factory runs whatever the client asks for through a shell,
     * which is the whole of what an SSH server owes `git-upload-pack`.
     */
    private fun <T> serving(block: (String) -> T): T {
        val source = work.resolve("work")
        Gits.init(source).use { gits ->
            gits.setIdentity(Identity("Tester", "tester@example.invalid"))
            File(source, "a.txt").writeText("hello\n")
            gits.addAll()
            gits.commit("first")
        }
        val repository = work.resolve("source.git")
        Git.cloneRepository()
            .setURI(source.absolutePath)
            .setBare(true)
            .setDirectory(repository)
            .call()
            .close()

        val keys = File(work, "server-keys/hostkey")
        keys.parentFile.mkdirs()
        val sshd = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(keys.toPath())
            passwordAuthenticator = PasswordAuthenticator { user, pass, _ ->
                user == "git" && pass == "hunter2"
            }
            commandFactory = CommandFactory { _, command -> ShellOut(command) }
        }
        sshd.start()
        try {
            return block("ssh://git@127.0.0.1:${sshd.port}${repository.absolutePath}")
        } finally {
            sshd.stop(true)
        }
    }
}

/** One command, run the way a shell would run it, with its three streams joined up. */
private class ShellOut(private val command: String) : Command {

    private var input: InputStream = InputStream.nullInputStream()
    private var output: OutputStream? = null
    private var error: OutputStream? = null
    private var exited: ExitCallback? = null
    private var process: Process? = null

    override fun setInputStream(`in`: InputStream) {
        input = `in`
    }

    override fun setOutputStream(out: OutputStream) {
        output = out
    }

    override fun setErrorStream(err: OutputStream) {
        error = err
    }

    override fun setExitCallback(callback: ExitCallback) {
        exited = callback
    }

    override fun start(channel: ChannelSession, env: Environment) {
        val started = ProcessBuilder("sh", "-c", command).start()
        process = started
        daemon { pump(input, started.outputStream); started.outputStream.close() }
        val outgoing = daemon { pump(started.inputStream, output!!.perWriteFlush()) }
        val errors = daemon { pump(started.errorStream, error!!.perWriteFlush()) }
        daemon {
            val status = started.waitFor()
            outgoing.join()
            errors.join()
            exited?.onExit(status)
        }
    }

    override fun destroy(channel: ChannelSession) {
        process?.destroyForcibly()
    }

    /**
     * Two layers buffer small writes: the JVM's [java.lang.Process] stdin is a
     * [java.io.BufferedOutputStream], and sshd's channel stream holds anything smaller than a
     * packet until flush or close. `git-upload-pack` reads nothing until its request is complete
     * and closes nothing until the clone is over, so every chunk must be pushed out the moment it
     * arrives or both sides stall until EOF.
     */
    private fun pump(from: InputStream, to: OutputStream) {
        val buffer = ByteArray(8192)
        while (true) {
            val n = from.read(buffer)
            if (n < 0) break
            to.write(buffer, 0, n)
            to.flush()
        }
    }

    private fun OutputStream.perWriteFlush() = object : OutputStream() {
        override fun write(b: Int) {
            this@perWriteFlush.write(b)
            this@perWriteFlush.flush()
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            this@perWriteFlush.write(b, off, len)
            this@perWriteFlush.flush()
        }
    }

    private fun daemon(body: () -> Unit) =
        Thread {
            runCatching(body)
        }.apply {
            isDaemon = true
            start()
        }
}
