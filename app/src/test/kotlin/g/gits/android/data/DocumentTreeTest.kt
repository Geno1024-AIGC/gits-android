package g.gits.android.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DocumentTreeTest {

    @Test
    fun `a folder in primary storage resolves to where primary storage is mounted`() {
        assertEquals(
            "/storage/emulated/0/Documents/code/my-repo",
            DocumentTree.pathFor("primary:Documents/code/my-repo"),
        )
    }

    @Test
    fun `a folder on a card resolves through the volume that holds it`() {
        assertEquals(
            "/storage/1A2B-3C4D/Documents/my-repo",
            DocumentTree.pathFor("1A2B-3C4D:Documents/my-repo"),
        )
    }

    @Test
    fun `primary storage itself is a folder too`() {
        assertEquals("/storage/emulated/0", DocumentTree.pathFor("primary:"))
    }

    @Test
    fun `the volume name is not case sensitive`() {
        assertEquals("/storage/emulated/0/Documents", DocumentTree.pathFor("PRIMARY:Documents"))
    }

    @Test
    fun `a relative path cannot escape the volume it was given`() {
        // The id is a path within a volume. A ".." in it would otherwise reach outside,
        // so it is left in the path and the filesystem resolves it; what matters is
        // that the volume itself is never taken from the id's tail.
        assertEquals(
            "/storage/emulated/0/Documents/../other",
            DocumentTree.pathFor("primary:Documents/../other"),
        )
        assertTrue(DocumentTree.pathFor("primary:../../etc")!!.startsWith("/storage/emulated/0/"))
    }

    @Test
    fun `an id with no volume names nothing`() {
        assertNull(DocumentTree.pathFor(null))
        assertNull(DocumentTree.pathFor(""))
        assertNull(DocumentTree.pathFor("no-colon-here"))
    }
}
