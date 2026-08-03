import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'api_client.dart';
import 'models.dart';

class ChatState {
  const ChatState({this.provider = const AiProvider(id: 'openai', name: 'OpenAI', short: 'OA', color: 0xFF10A37F, protocol: ApiProtocol.openAi, chatUrl: 'https://api.openai.com/v1/chat/completions', modelsUrl: 'https://api.openai.com/v1/models', keyHint: 'sk-...'), this.apiKeys = const {}, this.model = '', this.baseUrl = '', this.system = '', this.temperature = .7, this.models = const [], this.chats = const [], this.activeId, this.loadingModels = false, this.streaming = false, this.error = ''});
  final AiProvider provider;
  final Map<String, String> apiKeys;
  final String model;
  final String baseUrl;
  final String system;
  final double temperature;
  final List<AiModel> models;
  final List<Conversation> chats;
  final int? activeId;
  final bool loadingModels;
  final bool streaming;
  final String error;
  String get key => apiKeys[provider.id] ?? '';
  Conversation? get active => chats.where((chat) => chat.id == activeId).firstOrNull;
  ChatState copyWith({AiProvider? provider, Map<String, String>? apiKeys, String? model, String? baseUrl, String? system, double? temperature, List<AiModel>? models, List<Conversation>? chats, int? activeId, bool clearActive = false, bool? loadingModels, bool? streaming, String? error}) => ChatState(provider: provider ?? this.provider, apiKeys: apiKeys ?? this.apiKeys, model: model ?? this.model, baseUrl: baseUrl ?? this.baseUrl, system: system ?? this.system, temperature: temperature ?? this.temperature, models: models ?? this.models, chats: chats ?? this.chats, activeId: clearActive ? null : activeId ?? this.activeId, loadingModels: loadingModels ?? this.loadingModels, streaming: streaming ?? this.streaming, error: error ?? this.error);
}

final chatProvider = StateNotifierProvider<ChatController, ChatState>((ref) => ChatController());

class ChatController extends StateNotifier<ChatState> {
  ChatController() : super(const ChatState());
  final _api = AiApiClient();
  int _id = DateTime.now().millisecondsSinceEpoch;

  void provider(AiProvider value) => state = state.copyWith(provider: value, model: '', models: const [], baseUrl: value.custom ? state.baseUrl : '', error: '');
  void apiKey(String value) => state = state.copyWith(apiKeys: {...state.apiKeys, state.provider.id: value}, model: '', models: const [], error: '');
  void baseUrl(String value) => state = state.copyWith(baseUrl: value, model: '', models: const []);
  void model(String value) => state = state.copyWith(model: value);
  void system(String value) => state = state.copyWith(system: value);
  void temperature(double value) => state = state.copyWith(temperature: value);
  void clearError() => state = state.copyWith(error: '');
  void newChat() => state = state.copyWith(clearActive: true, error: '');
  void openChat(int id) => state = state.copyWith(activeId: id, error: '');
  void deleteChat(int id) => state = state.copyWith(chats: state.chats.where((chat) => chat.id != id).toList(), clearActive: state.activeId == id);
  void stop() { _api.cancel(); state = state.copyWith(streaming: false); }

  Future<bool> loadModels() async {
    if (state.key.trim().isEmpty) { state = state.copyWith(error: 'Добавьте API-ключ'); return false; }
    if (state.provider.custom && state.baseUrl.trim().isEmpty) { state = state.copyWith(error: 'Укажите Base URL'); return false; }
    state = state.copyWith(loadingModels: true, error: '');
    try {
      final result = await _api.loadModels(state.provider, state.key, state.baseUrl);
      state = state.copyWith(models: result, model: result.any((m) => m.id == state.model) ? state.model : (result.firstOrNull?.id ?? ''), loadingModels: false, error: result.isEmpty ? 'Каталог моделей пуст' : '');
      return result.isNotEmpty;
    } catch (error) { state = state.copyWith(loadingModels: false, error: error.toString().replaceFirst('Exception: ', '')); return false; }
  }

  Future<void> send(String raw) async {
    final text = raw.trim();
    if (text.isEmpty || state.streaming) return;
    if (state.key.isEmpty) { state = state.copyWith(error: 'Добавьте API-ключ'); return; }
    if (state.model.isEmpty) { state = state.copyWith(error: 'Выберите модель'); return; }
    final chatId = state.activeId ?? ++_id;
    final current = state.chats.where((chat) => chat.id == chatId).firstOrNull;
    final history = current?.messages ?? const <ChatMessage>[];
    final user = ChatMessage(id: ++_id, role: 'user', content: text);
    final assistant = ChatMessage(id: ++_id, role: 'assistant', content: '');
    final chat = Conversation(id: chatId, title: current?.title ?? text.substring(0, text.length.clamp(0, 42)), messages: [...history, user, assistant]);
    state = state.copyWith(chats: [chat, ...state.chats.where((item) => item.id != chatId)], activeId: chatId, streaming: true, error: '');
    try {
      await for (final delta in _api.streamChat(provider: state.provider, key: state.key, baseUrl: state.baseUrl, model: state.model, system: state.system, temperature: state.temperature, messages: [...history, user])) {
        _append(chatId, assistant.id, delta);
      }
    } catch (error) { state = state.copyWith(error: error.toString().replaceFirst('Exception: ', '')); _removeEmpty(chatId, assistant.id); }
    state = state.copyWith(streaming: false);
  }

  void _append(int chatId, int messageId, String delta) => state = state.copyWith(chats: state.chats.map((chat) => chat.id != chatId ? chat : chat.copyWith(messages: chat.messages.map((message) => message.id == messageId ? message.copyWith(content: message.content + delta) : message).toList())).toList());
  void _removeEmpty(int chatId, int messageId) => state = state.copyWith(chats: state.chats.map((chat) => chat.id != chatId ? chat : chat.copyWith(messages: chat.messages.where((message) => message.id != messageId || message.content.isNotEmpty).toList())).toList());
}

extension<T> on Iterable<T> { T? get firstOrNull => isEmpty ? null : first; }
