package com.geno1024.ai.gits.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The default branch is the one setting a person changes once and then lives with,
 * so what matters is that it comes back the same, that it survives being cleared,
 * and that there is always an answer to give the dialog that asks for it.
 *
 * Stood over a map: the value is a string either way, and the store it will really
 * use is Android's, which is the thing no test here can reach.
 */
class AppSettingsTest {

    @Test
    fun `a branch nobody has set is main`() {
        assertEquals("main", AppSettings(MapSettings()).initialBranch)
    }

    @Test
    fun `the branch written is the branch read back`() {
        val settings = AppSettings(MapSettings())
        settings.initialBranch = "trunk"
        assertEquals("trunk", settings.initialBranch)
    }

    @Test
    fun `a branch read back is the same on a settings object of its own`() {
        val store = MapSettings()
        AppSettings(store).initialBranch = "release/2.x"
        assertEquals("release/2.x", AppSettings(store).initialBranch)
    }

    @Test
    fun `space around a branch is not part of its name`() {
        val settings = AppSettings(MapSettings())
        settings.initialBranch = "  trunk  "
        assertEquals("trunk", settings.initialBranch)
    }

    @Test
    fun `a branch that says nothing leaves main in place`() {
        val settings = AppSettings(MapSettings())
        settings.initialBranch = "trunk"
        settings.initialBranch = "   "
        assertEquals("main", settings.initialBranch)
    }

    @Test
    fun `a theme nobody has set asks the system and takes the plain scheme`() {
        val settings = AppSettings(MapSettings())
        assertEquals("system", settings.themeMode)
        assertEquals("default", settings.themePreset)
    }

    @Test
    fun `the mode and the preset written are the ones read back`() {
        val settings = AppSettings(MapSettings())
        settings.themeMode = "dark"
        settings.themePreset = "dracula"
        assertEquals("dark", settings.themeMode)
        assertEquals("dracula", settings.themePreset)
    }

    @Test
    fun `the colours written are the colours read back`() {
        val settings = AppSettings(MapSettings())
        val colours = mapOf("primary" to 0xFF7AA2F7L, "background" to 0xFF0D1117L)
        settings.themeColors = colours
        assertEquals(colours, settings.themeColors)
    }

    @Test
    fun `colours taken back leave nothing behind to read`() {
        val settings = AppSettings(MapSettings())
        settings.themeColors = mapOf("primary" to 0xFF7AA2F7L)
        settings.themeColors = emptyMap()
        assertEquals(emptyMap<String, Long>(), settings.themeColors)
    }

    @Test
    fun `an entry that is not a colour is not read as one`() {
        val store = MapSettings()
        store.write(
            strings = mapOf(AppSettings.KEY_THEME_COLORS to "primary=notacolour;onSurface=FFFFFFFF"),
        )
        assertEquals(mapOf("onSurface" to 0xFFFFFFFFL), AppSettings(store).themeColors)
    }

    private class MapSettings : Settings {
        private val written = mutableMapOf<String, String>()

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
