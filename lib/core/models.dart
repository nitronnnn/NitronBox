enum ApiProtocol { openAi, anthropic, gemini }

class AiProvider {
  const AiProvider({required this.id, required this.name, required this.short, required this.color, required this.protocol, required this.chatUrl, required this.modelsUrl, required this.keyHint, this.custom = false});
  final String id;
  final String name;
  final String short;
  final int color;
  final ApiProtocol protocol;
  final String chatUrl;
  final String modelsUrl;
  final String keyHint;
  final bool custom;
}

const providers = <AiProvider>[
  AiProvider(id: 'openai', name: 'OpenAI', short: 'OA', color: 0xFF10A37F, protocol: ApiProtocol.openAi, chatUrl: 'https://api.openai.com/v1/chat/completions', modelsUrl: 'https://api.openai.com/v1/models', keyHint: 'sk-...'),
  AiProvider(id: 'anthropic', name: 'Anthropic', short: 'AN', color: 0xFFD97757, protocol: ApiProtocol.anthropic, chatUrl: 'https://api.anthropic.com/v1/messages', modelsUrl: 'https://api.anthropic.com/v1/models?limit=1000', keyHint: 'sk-ant-...'),
  AiProvider(id: 'gemini', name: 'Google Gemini', short: 'G', color: 0xFF4285F4, protocol: ApiProtocol.gemini, chatUrl: 'https://generativelanguage.googleapis.com/v1beta', modelsUrl: 'https://generativelanguage.googleapis.com/v1beta/models', keyHint: 'AIza...'),
  AiProvider(id: 'openrouter', name: 'OpenRouter', short: 'OR', color: 0xFF7067E8, protocol: ApiProtocol.openAi, chatUrl: 'https://openrouter.ai/api/v1/chat/completions', modelsUrl: 'https://openrouter.ai/api/v1/models', keyHint: 'sk-or-...'),
  AiProvider(id: 'groq', name: 'Groq', short: 'GQ', color: 0xFFF55036, protocol: ApiProtocol.openAi, chatUrl: 'https://api.groq.com/openai/v1/chat/completions', modelsUrl: 'https://api.groq.com/openai/v1/models', keyHint: 'gsk_...'),
  AiProvider(id: 'mistral', name: 'Mistral AI', short: 'MI', color: 0xFFFF7000, protocol: ApiProtocol.openAi, chatUrl: 'https://api.mistral.ai/v1/chat/completions', modelsUrl: 'https://api.mistral.ai/v1/models', keyHint: 'API key'),
  AiProvider(id: 'xai', name: 'xAI', short: 'x', color: 0xFF303030, protocol: ApiProtocol.openAi, chatUrl: 'https://api.x.ai/v1/chat/completions', modelsUrl: 'https://api.x.ai/v1/models', keyHint: 'xai-...'),
  AiProvider(id: 'custom', name: 'Свой провайдер', short: '+', color: 0xFF168D87, protocol: ApiProtocol.openAi, chatUrl: '', modelsUrl: '', keyHint: 'API key', custom: true),
];

class AiModel {
  const AiModel(this.id, this.name, [this.description = '']);
  final String id;
  final String name;
  final String description;
}

class ChatMessage {
  const ChatMessage({required this.id, required this.role, required this.content});
  final int id;
  final String role;
  final String content;
  ChatMessage copyWith({String? content}) => ChatMessage(id: id, role: role, content: content ?? this.content);
}

class Conversation {
  const Conversation({required this.id, required this.title, required this.messages});
  final int id;
  final String title;
  final List<ChatMessage> messages;
  Conversation copyWith({List<ChatMessage>? messages}) => Conversation(id: id, title: title, messages: messages ?? this.messages);
}
