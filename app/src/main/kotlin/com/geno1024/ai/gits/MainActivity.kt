package com.geno1024.ai.gits

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geno1024.ai.gits.data.Ssh
import com.geno1024.ai.gits.ui.theme.GitsTheme
import com.geno1024.ai.gits.ui.theme.ThemeStore
import com.geno1024.ai.gits.ui.theme.isDark

/** Single activity host; every screen composes from here. */
class MainActivity : ComponentActivity() {

    private val themeStore by lazy { ThemeStore.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before anything can connect: the transport is JVM-wide and looks here for
        // the keys, the config and the host this app has already trusted.
        Ssh.install(this)
        setContent {
            val theme by themeStore.settings.collectAsStateWithLifecycle()
            GitsTheme(
                darkTheme = theme.mode.isDark(),
                preset = theme.preset,
                customColors = theme.colors,
            ) {
                GitsApp()
            }
        }
    }
}
