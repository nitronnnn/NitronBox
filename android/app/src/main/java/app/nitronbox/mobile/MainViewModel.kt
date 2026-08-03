package app.nitronbox.mobile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

data class UiState(
    val provider: Provider = Providers.first(),
    val apiKeys: Map<String, String> = emptyMap(),
    val model: String = "",
    val baseUrl: String = "",
    val system: String = "",
    val temperature: Float = .7f,
    val models: List<AiModel> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
    val activeId: Long? = null,
    val loadingModels: Boolean = false,
    val generating: Boolean = false,
    val error: String = "",
)

class MainViewModel : ViewModel() {
    var state by mutableStateOf(UiState()); private set
    private val api = ApiClient()
    private val ids = AtomicLong(System.currentTimeMillis())
    private var job: Job? = null

    fun key() = state.apiKeys[state.provider.id].orEmpty()
    fun setKey(value: String) {
        state = state.copy(
            apiKeys = state.apiKeys + (state.provider.id to value),
            models = emptyList(),
            model = "",
            error = "",
        )
    }
    fun chooseProvider(value: Provider) { state = state.copy(provider = value, model = "", models = emptyList(), baseUrl = if (value.custom) state.baseUrl else "", error = "") }
    fun chooseModel(value: String) { state = state.copy(model = value) }
    fun setBaseUrl(value: String) { state = state.copy(baseUrl = value, models = emptyList(), model = "") }
    fun setSystem(value: String) { state = state.copy(system = value) }
    fun setTemperature(value: Float) { state = state.copy(temperature = value) }
    fun clearError() { state = state.copy(error = "") }
    fun newChat() { state = state.copy(activeId = null, error = "") }
    fun openChat(id: Long) { state = state.copy(activeId = id, error = "") }
    fun deleteChat(id: Long) { state = state.copy(conversations = state.conversations.filterNot { it.id == id }, activeId = if (state.activeId == id) null else state.activeId) }

    fun loadModels(onSuccess: () -> Unit = {}) {
        if (key().isBlank()) { state = state.copy(error = "Добавьте API-ключ"); return }
        if (state.provider.custom && state.baseUrl.isBlank()) { state = state.copy(error = "Укажите Base URL своего провайдера"); return }
        viewModelScope.launch {
            state = state.copy(loadingModels = true, error = "")
            try {
                val result = api.models(state.provider, key(), state.baseUrl)
                state = state.copy(models = result, model = state.model.takeIf { selected -> result.any { it.id == selected } } ?: result.firstOrNull()?.id.orEmpty())
                if (result.isEmpty()) state = state.copy(error = "Провайдер вернул пустой каталог") else onSuccess()
            } catch (error: Exception) { state = state.copy(error = error.message ?: "Не удалось получить модели") }
            finally { state = state.copy(loadingModels = false) }
        }
    }

    fun send(text: String, onAccepted: () -> Unit = {}) {
        val content = text.trim()
        if (content.isBlank() || state.generating) return
        if (key().isBlank()) { state = state.copy(error = "Добавьте API-ключ"); return }
        if (state.model.isBlank()) { state = state.copy(error = "Выберите модель из каталога"); return }
        val conversationId = state.activeId ?: ids.incrementAndGet()
        val user = ChatMessage(ids.incrementAndGet(), "user", content)
        val assistant = ChatMessage(ids.incrementAndGet(), "assistant", "")
        val current = state.conversations.find { it.id == conversationId }
        val history = current?.messages.orEmpty()
        val updated = Conversation(conversationId, current?.title ?: content.take(42), history + user + assistant)
        state = state.copy(conversations = listOf(updated) + state.conversations.filterNot { it.id == conversationId }, activeId = conversationId, generating = true, error = "")
        onAccepted()
        job = viewModelScope.launch {
            try {
                api.chat(state.provider, key(), state.baseUrl, state.model, state.system, state.temperature, history + user) { delta ->
                    withContext(Dispatchers.Main) { appendDelta(conversationId, assistant.id, delta) }
                }
            } catch (_: CancellationException) {
            } catch (error: Exception) {
                state = state.copy(error = error.message ?: "Не удалось получить ответ")
                removeEmpty(conversationId, assistant.id)
            } finally { state = state.copy(generating = false) }
        }
    }

    fun stop() { api.cancel(); job?.cancel(); state = state.copy(generating = false) }

    private fun appendDelta(chatId: Long, messageId: Long, delta: String) {
        state = state.copy(conversations = state.conversations.map { chat -> if (chat.id != chatId) chat else chat.copy(messages = chat.messages.map { if (it.id == messageId) it.copy(content = it.content + delta) else it }) })
    }
    private fun removeEmpty(chatId: Long, messageId: Long) {
        state = state.copy(conversations = state.conversations.map { chat -> if (chat.id != chatId) chat else chat.copy(messages = chat.messages.filterNot { it.id == messageId && it.content.isEmpty() }) })
    }
}
