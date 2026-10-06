package com.geno1024.ai.gits.data

import android.content.Context
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.sshd.SshdSessionFactory
import java.io.File

/**
 * Where the files of an SSH client are kept, in place of a home directory.
 *
 * Android has no `~` to speak of: it lands somewhere the app may not write, and a
 * known_hosts file has to be created somewhere it can. So this app's storage stands
 * in for the whole of the home directory — config, known_hosts and private keys all
 * live under [directory], which is where the transport is pointed.
 */
object Ssh {

    /** The directory standing in for `~/.ssh`, inside this app's own storage. */
    fun directory(context: Context): File = File(context.filesDir, ".ssh")

    /**
     * Points JGit's SSH transport at this app's storage, before anything connects.
     *
     * The session factory is JVM-wide, so this runs once at start-up. What is left at
     * its defaults is deliberate: an unknown host key is asked about rather than taken
     * or refused, and the keys already in [directory] are the ones tried.
     */
    fun install(context: Context) {
        SshSessionFactory.setInstance(
            SshdSessionFactory().apply {
                setHomeDirectory(context.filesDir)
                setSshDirectory(directory(context))
            },
        )
    }
}
