package g.gits.android.data

import g.gits.git.Credentials
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A store holding tokens is worth more tests than most, because the failure modes are
 * quiet: a secret that cannot be unsealed looks exactly like one that was never
 * entered, and both show up much later as an unexplained 401.
 *
 * The encryption itself is the Android keystore's job and cannot run here. What is
 * tested is everything this class decides, over a stand-in box.
 */
class CredentialStoreTest {

    @Test
    fun `a token given for a host comes back for that host`() {
        val store = store()
        store.remember("github.com", "octocat", "ghp_secret".toCharArray())

        val credentials = store.credentialsFor("github.com")
        assertTrue(credentials is Credentials.UsernamePassword)
        credentials as Credentials.UsernamePassword
        assertEquals("octocat", credentials.username)
        assertEquals("ghp_secret", credentials.secret.concatToString())
    }

    @Test
    fun `a token is not offered to a different host`() {
        val store = store()
        store.remember("github.com", "octocat", "ghp_secret".toCharArray())

        assertEquals(Credentials.None, store.credentialsFor("gitlab.com"))
        assertEquals(Credentials.None, store.credentialsFor(null))
        assertFalse(store.has("gitlab.com"))
    }

    @Test
    fun `a host with nothing given is asked for`() {
        val store = store()
        assertFalse(store.has("github.com"))
        assertTrue(store.has(null).not(), "there is no host to hold a token for")
    }

    @Test
    fun `the secret is not written down in the clear`() {
        val settings = MapSettings()
        val store = CredentialStore(settings, ReversingBox())
        store.remember("github.com", "octocat", "ghp_secret".toCharArray())

        val written = settings.written.values.joinToString()
        assertFalse(
            written.contains("ghp_secret"),
            "the token should be sealed before it is stored, but found: $written",
        )
        assertTrue(written.contains("octocat"), "a name is not a secret and may be stored plainly")
    }

    @Test
    fun `a secret that cannot be unsealed is forgotten rather than returned empty`() {
        val settings = MapSettings()
        val store = CredentialStore(settings, ReversingBox())
        store.remember("github.com", "octocat", "ghp_secret".toCharArray())

        // The keystore that sealed this is gone, which is what a restored backup or a
        // wiped key looks like. The entry has to go, or the app will keep offering a
        // secret that can never work.
        val rotated = CredentialStore(settings, UnopenableBox())
        assertEquals(Credentials.None, rotated.credentialsFor("github.com"))
        assertFalse(rotated.has("github.com"), "an unusable entry should not be offered again")
    }

    @Test
    fun `hosts are listed with the names given for them`() {
        val store = store()
        store.remember("github.com", "octocat", "one".toCharArray())
        store.remember("gitlab.com", "someone", "two".toCharArray())

        assertEquals(
            setOf("github.com", "gitlab.com"),
            store.accounts().map { it.host }.toSet(),
        )
        assertEquals(
            mapOf("github.com" to "octocat", "gitlab.com" to "someone"),
            store.accounts().associate { it.host to it.username },
        )
    }

    @Test
    fun `giving a token again replaces the old one`() {
        val store = store()
        store.remember("github.com", "octocat", "old".toCharArray())
        store.remember("github.com", "octocat", "new".toCharArray())

        val credentials = store.credentialsFor("github.com") as Credentials.UsernamePassword
        assertEquals("new", credentials.secret.concatToString())
        assertEquals(setOf("github.com"), store.hosts())
    }

    @Test
    fun `forgetting a host leaves the others alone`() {
        val store = store()
        store.remember("github.com", "octocat", "one".toCharArray())
        store.remember("gitlab.com", "someone", "two".toCharArray())

        store.forget("github.com")

        assertEquals(setOf("gitlab.com"), store.hosts())
        assertEquals(Credentials.None, store.credentialsFor("github.com"))
        assertTrue(store.has("gitlab.com"))
    }

    @Test
    fun `forgetting everything leaves nothing behind`() {
        val store = store()
        store.remember("github.com", "octocat", "one".toCharArray())
        store.remember("gitlab.com", "someone", "two".toCharArray())

        store.forgetAll()

        assertEquals(emptySet<String>(), store.hosts())
        assertEquals(Credentials.None, store.credentialsFor("github.com"))
        assertEquals(Credentials.None, store.credentialsFor("gitlab.com"))
    }

    @Test
    fun `a token with characters outside ascii survives the round trip`() {
        val store = store()
        // Not every token is a hex string from an API, and a store that mangles a
        // passphrase would fail at the far end with nothing to explain it.
        val awkward = "pässwörd with spaces & symbols ~!@#"
        store.remember("example.test", "someone", awkward.toCharArray())

        val credentials = store.credentialsFor("example.test") as Credentials.UsernamePassword
        assertEquals(awkward, credentials.secret.concatToString())
    }

    @Test
    fun `a source resolves per host`() {
        val store = store()
        store.remember("github.com", "octocat", "one".toCharArray())
        val source = store.asSource()

        assertTrue(source.forHost("github.com") is Credentials.UsernamePassword)
        assertEquals(Credentials.None, source.forHost("gitlab.com"))
        assertEquals(Credentials.None, source.forHost(null))
    }

    private fun store() = CredentialStore(MapSettings(), ReversingBox())

    /**
     * Stands in for the keystore by storing bytes back to front, which is enough to
     * catch a store that writes the secret in the clear and to let a test hand back
     * bytes it can no longer read.
     */
    private class ReversingBox : SecretBox {
        override fun seal(plaintext: ByteArray): ByteArray = plaintext.reversedArray()

        override fun open(sealed: ByteArray): ByteArray = sealed.reversedArray()
    }

    private class UnopenableBox : SecretBox {
        override fun seal(plaintext: ByteArray): ByteArray = plaintext

        override fun open(sealed: ByteArray): ByteArray =
            throw IllegalStateException("the key is gone")
    }

    private class MapSettings : Settings {
        val written = mutableMapOf<String, String>()

        override fun string(key: String): String? = written[key]

        override fun strings(key: String): Set<String>? = written[key]?.split(' ')?.toSet()

        override fun write(
            strings: Map<String, String>,
            stringsSet: Map<String, Set<String>>,
            removed: Set<String>,
        ) {
            strings.forEach { (key, value) -> written[key] = value }
            stringsSet.forEach { (key, value) -> written[key] = value.joinToString(" ") }
            removed.forEach(written::remove)
        }
    }
}
