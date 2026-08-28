package com.nitronbox.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nitronbox.app.AppContainer
import com.nitronbox.app.ui.chat.ChatSessionViewModel
import com.nitronbox.app.ui.settings.SettingsViewModel

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        ChatSessionViewModel::class.java -> ChatSessionViewModel(container) as T
        SettingsViewModel::class.java -> SettingsViewModel(container) as T
        else -> throw IllegalArgumentException("Unknown view model: $modelClass")
    }
}
