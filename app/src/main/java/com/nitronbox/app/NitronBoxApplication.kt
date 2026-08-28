package com.nitronbox.app

import android.app.Application
import android.util.Log
import com.nitronbox.app.data.attachments.AttachmentPipeline
import com.nitronbox.app.data.attachments.PdfTextExtractor
import com.nitronbox.app.data.local.ChatRepository
import com.nitronbox.app.data.local.NitronBoxDatabase
import com.nitronbox.app.data.model.ModelTarget
import com.nitronbox.app.data.remote.ProviderRegistry
import com.nitronbox.app.data.remote.ProviderRegistryFactory
import com.nitronbox.app.data.security.SecureCredentialStore
import com.nitronbox.app.data.settings.AppSettings
import com.nitronbox.app.domain.chat.ChatService
import com.nitronbox.app.domain.chat.LlmConversationSummarizer
import com.nitronbox.app.domain.context.CharacterTokenEstimator
import com.nitronbox.app.domain.context.ContextWindowEngine
import com.google.android.gms.security.ProviderInstaller
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class NitronBoxApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Persist stack traces so crash reports survive the process death.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                java.io.File(filesDir, "last_crash.txt")
                    .writeText(android.util.Log.getStackTraceString(error))
            }
            previous?.uncaughtException(thread, error)
        }
        // Installs current TLS providers on API 26 devices when Play Services is available.
        ProviderInstaller.installIfNeededAsync(
            this,
            object : ProviderInstaller.ProviderInstallListener {
                override fun onProviderInstalled() = Unit
                override fun onProviderInstallFailed(errorCode: Int, recoveryIntent: android.content.Intent?) {
                    Log.w("NitronBox", "TLS provider update unavailable (code=$errorCode)")
                }
            },
        )
    }
}

/** Small composition root; feature modules can replace this with Hilt without changing domains. */
class AppContainer(application: Application) {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        classDiscriminator = "type"
    }
    val database = NitronBoxDatabase.create(application)
    val dao = database.dao()
    val chatRepository = ChatRepository(dao, json)
    val credentialStore = SecureCredentialStore(application)
    val attachmentPipeline = AttachmentPipeline(application.contentResolver)
    val pdfTextExtractor = PdfTextExtractor(application, application.contentResolver)
    val appSettings = AppSettings(application)
    val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    val providerRegistryFactory = ProviderRegistryFactory(credentialStore, httpClient, json)
    val modelCatalogStore = com.nitronbox.app.data.remote.ModelCatalogStore(chatRepository, providerRegistryFactory)
    val contentResolver = application.contentResolver
    val filesDir: java.io.File = application.filesDir
    val creatorFileTools = com.nitronbox.app.data.filesystem.CreatorFileTools(application)

    val contextWindowEngine: ContextWindowEngine by lazy {
        val summarizer = LlmConversationSummarizer(
            resolveBridge = { activeBridge() },
            resolveTarget = { activeTarget() },
        )
        ContextWindowEngine(CharacterTokenEstimator(), summarizer)
    }

    val chatService: ChatService by lazy {
        ChatService(chatRepository, contextWindowEngine, attachmentPipeline, pdfTextExtractor)
    }

    /** Builds a registry over the currently persisted provider profiles. */
    suspend fun loadRegistry(): ProviderRegistry =
        providerRegistryFactory.create(chatRepository.providerProfiles())

    private suspend fun activeBridge() =
        loadRegistry().require(activeTarget().providerId)

    private suspend fun activeTarget(): ModelTarget {
        val active = appSettings.loadActiveModel()
            ?: throw com.nitronbox.app.data.remote.ProviderException(
                "No model is selected. Configure a provider and pick a model in Settings.",
                "workspace",
            )
        return ModelTarget(active.providerId, active.modelId, active.displayName)
    }
}
