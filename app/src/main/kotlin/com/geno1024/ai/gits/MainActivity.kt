package com.geno1024.ai.gits

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.geno1024.ai.gits.ui.theme.GitsTheme

/** Single activity host; every screen composes from here. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GitsTheme {
                GitsApp()
            }
        }
    }
}
