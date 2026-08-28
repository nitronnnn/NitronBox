package com.nitronbox.app.data.remote

import com.nitronbox.app.data.security.CredentialStore
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/** Builds provider bridges from persisted profiles; secrets are resolved through the vault. */
class ProviderRegistryFactory(
    private val credentialStore: CredentialStore,
    private val baseClient: OkHttpClient,
    private val json: Json,
) {
    fun create(profiles: List<ProviderProfile>): ProviderRegistry =
        ProviderRegistry(profiles.map { profile ->
            HttpAiProviderBridge(profile, credentialStore, baseClient, json)
        })
}
