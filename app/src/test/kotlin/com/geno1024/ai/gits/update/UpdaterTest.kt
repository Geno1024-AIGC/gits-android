package com.geno1024.ai.gits.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdaterTest {

    @Test
    fun `reads the build stamp out of a release name`() {
        val version = Updater.parseVersion("Gits 0.1.7.19.c9c6536b")
        assertEquals(Updater.Version(7, 19, "c9c6536b"), version)
    }

    @Test
    fun `keeps a sha of any case in one shape`() {
        assertEquals("c9c6536b", Updater.parseVersion("0.1.1.2.C9C6536B")?.sha)
    }

    @Test
    fun `a name that is not a build stamp is not a version`() {
        assertNull(Updater.parseVersion("Release 42"))
        assertNull(Updater.parseVersion("0.1.7.19"))
        assertNull(Updater.parseVersion("0.2.7.19.c9c6536b"))
    }

    @Test
    fun `orders builds by run, then by pack, and not by commit`() {
        val older = Updater.Version(7, 19, "00000000")
        val newer = Updater.Version(7, 20, "00000000")
        val muchNewer = Updater.Version(8, 1, "00000000")
        assertTrue(older < newer)
        assertTrue(newer < muchNewer)
    }

    @Test
    fun `the build being offered is the newest one, whatever order the feed used`() {
        val found = Updater.newer(
            current = Updater.Version(7, 19, "c9c6536b"),
            available = listOf(
                release("Gits 0.1.7.18.aaaaaaa"),
                release("Gits 0.1.9.2.cccccccc"),
                release("Gits 0.1.7.19.aaaaaaa"),
            ),
        )
        assertEquals("Gits 0.1.9.2.cccccccc", found?.name)
    }

    @Test
    fun `the same build is not an update`() {
        val found = Updater.newer(
            current = Updater.Version(7, 19, "c9c6536b"),
            available = listOf(release("Gits 0.1.7.19.aaaaaaa")),
        )
        assertNull(found)
    }

    @Test
    fun `older builds are not offered back`() {
        val found = Updater.newer(
            current = Updater.Version(8, 1, "cccccccc"),
            available = listOf(release("Gits 0.1.7.19.aaaaaaa"), release("Gits 0.1.7.20.bbbbbbbb")),
        )
        assertNull(found)
    }

    @Test
    fun `a feed with no build stamp offers nothing rather than guessing`() {
        assertNull(Updater.newer(Updater.Version(1, 1, "aaaaaaaa"), listOf(release("Nightly"))))
    }

    @Test
    fun `a first install is offered the newest build`() {
        val found = Updater.newer(current = null, available = listOf(release("Gits 0.1.2.3.dddddddd")))
        assertEquals("Gits 0.1.2.3.dddddddd", found?.name)
    }

    @Test
    fun `a mirror only changes the file, never the release it names`() {
        val release = release("Gits 0.1.2.3.dddddddd")
        val fromGitHub = Updater.downloadUrl(Updater.SOURCES.first(), release)
        val fromMirror = Updater.downloadUrl(Updater.SOURCES.last(), release)
        assertTrue(fromGitHub.startsWith("https://github.com/"))
        assertTrue(fromMirror.contains("https://github.com/"))
        assertTrue(fromGitHub.substringAfter("download/") == fromMirror.substringAfter("download/"))
    }

    @Test
    fun `a source that is gone falls back to GitHub`() {
        assertEquals("github", Updater.sourceFrom("a-mirror-that-went-away").id)
        assertEquals("ghfast", Updater.sourceFrom("ghfast").id)
    }

    private fun release(name: String) = Updater.Release(
        tag = "pre-release",
        name = name,
        prerelease = true,
        version = Updater.parseVersion(name),
        apkName = "gits.apk",
        apkSize = 1,
    )
}
