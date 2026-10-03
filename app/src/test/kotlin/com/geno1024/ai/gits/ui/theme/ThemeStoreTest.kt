package com.geno1024.ai.gits.ui.theme

import com.geno1024.ai.gits.data.AppSettings
import com.geno1024.ai.gits.data.Settings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The activity draws through the theme while the settings screen is the one changing
 * it, so what matters is that a choice reaches the flow both are looking at, that it
 * survives being stored, and that there is an answer even when what is stored names
 * nothing this build knows.
 *
 * Stood over a map for the same reason every other store here is: the value is a
 * string either way, and the store it will really use is Android's.
 */
class ThemeStoreTest {

    @Test
    fun `a store nobody has touched asks the system and takes the plain scheme`() {
        val store = ThemeStore(AppSettings(MapSettings()))
        assertEquals(ThemeSettings(), store.settings.value)
    }

    @Test
    fun `a mode set on the store is the mode its flow carries`() {
        val store = ThemeStore(AppSettings(MapSettings()))
        store.setMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, store.settings.value.mode)
    }

    @Test
    fun `a preset set survives being read by a store of its own`() {
        val backing = MapSettings()
        ThemeStore(AppSettings(backing)).setPreset(ThemePreset.DRACULA)
        assertEquals(
            ThemePreset.DRACULA,
            ThemeStore(AppSettings(backing)).settings.value.preset,
        )
    }

    @Test
    fun `a colour set is held and a colour taken back is gone`() {
        val store = ThemeStore(AppSettings(MapSettings()))
        store.setColor("primary", 0xFFEC407AL)
        assertEquals(mapOf("primary" to 0xFFEC407AL), store.settings.value.colors)
        store.setColor("primary", null)
        assertEquals(emptyMap<String, Long>(), store.settings.value.colors)
    }

    @Test
    fun `an id that names no mode or scheme still has an answer`() {
        val backing = MapSettings()
        backing.write(
            strings = mapOf(
                AppSettings.KEY_THEME_MODE to "violet",
                AppSettings.KEY_THEME_PRESET to "plaid",
            ),
        )
        val theme = ThemeStore(AppSettings(backing)).settings.value
        assertEquals(ThemeMode.SYSTEM, theme.mode)
        assertEquals(ThemePreset.DEFAULT, theme.preset)
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
