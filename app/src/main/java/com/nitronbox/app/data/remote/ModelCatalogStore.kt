package com.nitronbox.app.data.remote

import com.nitronbox.app.data.local.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide cache of discovered model catalogs, shared between the chat model picker and the
 * settings screen so a freshly created provider's models are visible everywhere at once.
 */
class ModelCatalogStore(
    private val repository: ChatRepository,
    private val factory: ProviderRegistryFactory,
) {
    private val _models = MutableStateFlow<Map<String, List<DiscoveredModel>>>(emptyMap())
    val models: StateFlow<Map<String, List<DiscoveredModel>>> = _models.asStateFlow()

    fun cached(providerId: String): List<DiscoveredModel> = _models.value[providerId].orEmpty()

    fun hasCatalog(providerId: String): Boolean = _models.value.containsKey(providerId)

    /** Fetches the catalog; returns null on failure (error text is surfaced by the caller). */
    suspend fun refresh(providerId: String): List<DiscoveredModel>? {
        val result = runCatching {
            val profile = repository.providerProfile(providerId)
                ?: throw IllegalStateException("Provider is missing")
            factory.create(listOf(profile)).require(providerId).discoverModels(forceRefresh = true)
        }
        return result.fold(
            onSuccess = { models ->
                _models.value = _models.value + (providerId to models)
                models
            },
            onFailure = { null },
        )
    }

    fun forget(providerId: String) {
        _models.value = _models.value - providerId
    }
}
