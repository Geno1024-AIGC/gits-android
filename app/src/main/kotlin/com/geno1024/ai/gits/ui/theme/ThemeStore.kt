package com.geno1024.ai.gits.ui.theme

import android.app.Application
import android.content.Context
import com.geno1024.ai.gits.data.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the theme is drawing: the mode it answers to, the scheme, and the custom colours. */
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val preset: ThemePreset = ThemePreset.DEFAULT,
    val colors: Map<String, Long> = emptyMap(),
)

/**
 * The theme as it stands, read and written from wherever a choice is made.
 *
 * A store rather than a plain read of the preference because the activity draws every
 * screen through the theme while the settings screen is the one changing it: the two
 * have to see the same answer at the same moment, and a flow held by one process
 * object is what gives them that without the activity having to know about a screen.
 */
class ThemeStore(private val appSettings: AppSettings) {

    private val _settings = MutableStateFlow(read())

    val settings: StateFlow<ThemeSettings> = _settings.asStateFlow()

    /** Puts the app in the light, in the dark, or on the system's answer. */
    fun setMode(mode: ThemeMode) {
        appSettings.themeMode = mode.id
        _settings.value = read()
    }

    /** Swaps the scheme the colours are taken from. */
    fun setPreset(preset: ThemePreset) {
        appSettings.themePreset = preset.id
        _settings.value = read()
    }

    /**
     * Fills one slot of the custom scheme, or empties it when [color] is null so the
     * slot falls back to what the default scheme paints there.
     */
    fun setColor(slot: String, color: Long?) {
        val colors = appSettings.themeColors.toMutableMap()
        if (color == null) colors.remove(slot) else colors[slot] = color
        appSettings.themeColors = colors
        _settings.value = read()
    }

    /** Everything the theme needs, read back from the preference in one go. */
    private fun read() = ThemeSettings(
        mode = ThemeMode.from(appSettings.themeMode),
        preset = ThemePreset.from(appSettings.themePreset),
        colors = appSettings.themeColors,
    )

    companion object {

        @Volatile
        private var instance: ThemeStore? = null

        fun getInstance(context: Context): ThemeStore =
            instance ?: synchronized(this) {
                instance ?: ThemeStore(
                    AppSettings.of(context.applicationContext as Application),
                ).also { instance = it }
            }
    }
}
