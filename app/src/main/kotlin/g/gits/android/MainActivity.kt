package g.gits.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import g.gits.android.ui.theme.GitsTheme

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
