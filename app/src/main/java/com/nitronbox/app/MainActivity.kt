package com.nitronbox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nitronbox.app.ui.AppViewModelFactory
import com.nitronbox.app.ui.chat.ChatScreen
import com.nitronbox.app.ui.chat.ChatSessionViewModel
import com.nitronbox.app.ui.settings.SettingsScreen
import com.nitronbox.app.ui.settings.SettingsViewModel
import com.nitronbox.app.ui.theme.NitronBoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val factory = AppViewModelFactory((application as NitronBoxApplication).container)
        setContent {
            NitronBoxTheme {
                val navController = rememberNavController()
                NavHost(navController, startDestination = Routes.CHAT, modifier = Modifier) {
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

private object Routes {
    const val CHAT = "chat"
    const val SETTINGS = "settings"
}
