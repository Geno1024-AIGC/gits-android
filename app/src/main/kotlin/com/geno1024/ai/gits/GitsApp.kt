package com.geno1024.ai.gits

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.geno1024.ai.gits.ui.home.HomeScreen
import com.geno1024.ai.gits.ui.keys.KeysScreen
import com.geno1024.ai.gits.ui.repo.RepoScreen
import com.geno1024.ai.gits.ui.ssh.SshScreen
import com.geno1024.ai.gits.ui.settings.AboutScreen
import com.geno1024.ai.gits.ui.settings.SettingsScreen
import java.io.File

private object Routes {
    const val HOME = "home"
    const val REPO = "repo"
    const val KEYS = "keys"
    const val SSH = "ssh"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    /** Paths go in the back stack as an argument, so they are encoded, not interpolated. */
    fun repo(path: String) = "$REPO/${java.net.URLEncoder.encode(path, "UTF-8")}"
}

/** Decides which screen the user is on; every screen keeps its own state below this. */
@Composable
fun GitsApp() {
    val controller = rememberNavController()

    NavHost(navController = controller, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpen = { directory -> controller.navigate(Routes.repo(directory.path)) },
                onManageKeys = { controller.navigate(Routes.KEYS) { launchSingleTop = true } },
                onOpenSettings = { controller.navigate(Routes.SETTINGS) { launchSingleTop = true } },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { controller.popBackStack() },
                onOpenKeys = { controller.navigate(Routes.KEYS) { launchSingleTop = true } },
                onOpenSsh = { controller.navigate(Routes.SSH) { launchSingleTop = true } },
                onOpenAbout = { controller.navigate(Routes.ABOUT) { launchSingleTop = true } },
            )
        }
        // One destination each, shared by every entry point. Registering the same screen
        // twice under different routes would give it two back stack entries, and which
        // one you land on would depend on which icon you pressed.
        composable(Routes.KEYS) {
            KeysScreen(onBack = { controller.popBackStack() })
        }
        composable(Routes.SSH) {
            SshScreen(onBack = { controller.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { controller.popBackStack() })
        }
        composable(
            route = "${Routes.REPO}/{path}",
            arguments = listOf(navArgument("path") { type = NavType.StringType }),
        ) { entry ->
            val path = entry.arguments?.getString("path").orEmpty()
            RepoScreen(
                path = java.net.URLDecoder.decode(path, "UTF-8"),
                onBack = { controller.popBackStack() },
                onOpenSettings = { controller.navigate(Routes.SETTINGS) { launchSingleTop = true } },
            )
        }
    }
}
