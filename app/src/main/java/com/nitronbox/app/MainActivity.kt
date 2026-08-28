package com.nitronbox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nitronbox.app.data.model.ThemeMode
import com.nitronbox.app.data.model.WorkspaceTheme
import com.nitronbox.app.data.settings.ThemeModeSetting
import com.nitronbox.app.ui.AppViewModelFactory
import com.nitronbox.app.ui.chat.ChatScreen
import com.nitronbox.app.ui.chat.ChatSessionViewModel
import com.nitronbox.app.ui.i18n.LocalStrings
import com.nitronbox.app.ui.i18n.stringsFor
import com.nitronbox.app.ui.settings.SettingsScreen
import com.nitronbox.app.ui.settings.SettingsViewModel
import com.nitronbox.app.ui.theme.NitronBoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as NitronBoxApplication).container
        val factory = AppViewModelFactory(container)
        setContent {
            val themeMode by container.appSettings.themeMode.collectAsState(ThemeModeSetting.SYSTEM)
            val language by container.appSettings.language.collectAsState(com.nitronbox.app.data.settings.LanguageSetting.SYSTEM)
            val blurEnabled by container.appSettings.blurEnabled.collectAsState(true)
            val blurStrength by container.appSettings.blurStrength.collectAsState(18f)
            val blurredPanels by container.appSettings.blurredPanels.collectAsState(true)
            val panelBlurStrength by container.appSettings.panelBlurStrength.collectAsState(24f)
            val strings = remember(language) { stringsFor(language) }

            androidx.compose.runtime.CompositionLocalProvider(LocalStrings provides strings) {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.nitronbox.app.ui.theme.LocalUiFx provides com.nitronbox.app.ui.theme.UiFxConfig(
                        blurEnabled = blurEnabled,
                        blurRadius = blurStrength,
                        blurredPanels = blurredPanels,
                        panelBlurRadius = panelBlurStrength,
                    ),
                ) {
                NitronBoxTheme(
                    workspaceTheme = WorkspaceTheme(mode = themeMode.toDomain()),
                ) {
                    val navController = rememberNavController()
                    NavHost(
                        navController,
                        startDestination = Routes.CHAT,
                        modifier = Modifier,
                        // Quick cross-fade instead of the default jump-cut: no white flash.
                        enterTransition = { fadeIn(androidx.compose.animation.core.tween(220)) },
                        exitTransition = { fadeOut(androidx.compose.animation.core.tween(180)) },
                        popEnterTransition = { fadeIn(androidx.compose.animation.core.tween(220)) },
                        popExitTransition = { fadeOut(androidx.compose.animation.core.tween(180)) },
                    ) {
                        composable(Routes.CHAT) {
                            val chatViewModel: ChatSessionViewModel = viewModel(factory = factory)
                            ChatScreen(
                                viewModel = chatViewModel,
                                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                            )
                        }
                        composable(Routes.SETTINGS) {
                            val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
                }
            }
        }
    }
}
private fun ThemeModeSetting.toDomain(): ThemeMode = when (this) {
    ThemeModeSetting.SYSTEM -> ThemeMode.SYSTEM
    ThemeModeSetting.LIGHT -> ThemeMode.LIGHT
    ThemeModeSetting.DARK -> ThemeMode.DARK
}

private object Routes {
    const val CHAT = "chat"
    const val SETTINGS = "settings"
}
