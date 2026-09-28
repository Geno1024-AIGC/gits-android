package com.geno1024.ai.gits.git

import org.eclipse.jgit.lib.Config
import org.eclipse.jgit.lib.RepositoryCache
import org.eclipse.jgit.storage.file.FileBasedConfig
import org.eclipse.jgit.util.FS
import org.eclipse.jgit.util.SystemReader
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.io.File
import java.nio.file.Files

/**
 * Detaches a test run from the developer's own git setup.
 *
 * JGit falls back to the global config for anything a repository does not state itself,
 * so an ambient `commit.gpgsign=true` or `user.signingkey` changes what a test exercises
 * without changing a line of the test. The same suite would then pass on a clean machine
 * and fail on this one, which is the worst possible way for a signing test to behave.
 */
class IsolatedGitEnvironment : BeforeAllCallback, AfterAllCallback {

    private var previous: SystemReader? = null
    private val home: File = Files.createTempDirectory("gits-test-home").toFile()

    override fun beforeAll(context: ExtensionContext) {
        previous = SystemReader.getInstance()
        SystemReader.setInstance(
            object : SystemReader.Delegate(previous) {
                override fun openUserConfig(base: Config?, fs: FS): FileBasedConfig =
                    FileBasedConfig(File(home, ".gitconfig"), fs)

                override fun openSystemConfig(base: Config?, fs: FS): FileBasedConfig =
                    FileBasedConfig(File(home, "gitconfig-system"), fs)

                override fun openJGitConfig(base: Config?, fs: FS): FileBasedConfig =
                    FileBasedConfig(File(home, "jgit-config"), fs)
            },
        )
    }

    override fun afterAll(context: ExtensionContext) {
        SystemReader.setInstance(previous)
        // Cached repositories hold handles and config snapshots from the temporary home.
        RepositoryCache.clear()
        home.deleteRecursively()
    }
}
