import 'dart:async';
import 'dart:convert';
import 'package:dio/dio.dart';
import 'models.dart';

class AiApiClient {
  AiApiClient() : _dio = Dio(BaseOptions(connectTimeout: const Duration(seconds: 25), receiveTimeout: const Duration(minutes: 3)));
  final Dio _dio;
  CancelToken? _cancelToken;

  void cancel() => _cancelToken?.cancel('Остановлено');

  Map<String, String> _headers(AiProvider provider, String key) => switch (provider.protocol) {
    ApiProtocol.anthropic => {'x-api-key': key, 'anthropic-version': '2023-06-01', 'content-type': 'application/json'},
    ApiProtocol.gemini => {'content-type': 'application/json'},
    ApiProtocol.openAi => {'authorization': 'Bearer $key', 'content-type': 'application/json', if (provider.id == 'openrouter') 'HTTP-Referer': 'https://nitronbox.app', if (provider.id == 'openrouter') 'X-Title': 'NitronBox'},
  };

  String _custom(String base, String endpoint) {
    final clean = base.trim().replaceFirst(RegExp(r'/+$'), '');
    final uri = Uri.tryParse(clean);
    if (uri == null || !uri.hasScheme || !{'http', 'https'}.contains(uri.scheme)) throw Exception('Некорректный Base URL');
    if (clean.endsWith('/chat/completions')) return endpoint == 'models' ? clean.replaceFirst(RegExp(r'/chat/completions$'), '/models') : clean;
    return '$clean${clean.endsWith('/v1') ? '' : '/v1'}/$endpoint';
  }

  Future<List<AiModel>> loadModels(AiProvider provider, String key, String baseUrl) async {
    var url = provider.custom ? _custom(baseUrl, 'models') : provider.modelsUrl;
    if (provider.protocol == ApiProtocol.gemini) url = '$url?key=${Uri.encodeQueryComponent(key)}&pageSize=1000';
    try {
      final response = await _dio.get<Object?>(url, options: Options(headers: _headers(provider, key)));
      final body = response.data is Map ? Map<String, dynamic>.from(response.data! as Map) : jsonDecode(response.data.toString()) as Map<String, dynamic>;
      final source = (provider.protocol == ApiProtocol.gemini ? body['models'] : body['data'] ?? body['models']) as List? ?? const [];
      final result = source.whereType<Map>().where((raw) {
        if (provider.protocol != ApiProtocol.gemini) return true;
        return (raw['supportedGenerationMethods'] as List? ?? const []).contains('generateContent');
      }).map((raw) {
        final item = Map<String, dynamic>.from(raw);
        var id = (item['id'] ?? item['name'] ?? '').toString();
        if (provider.protocol == ApiProtocol.gemini) id = id.replaceFirst('models/', '');
        return AiModel(id, (item['displayName'] ?? item['name'] ?? id).toString(), (item['description'] ?? item['owned_by'] ?? '').toString());
      }).where((model) => model.id.isNotEmpty).toList()..sort((a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()));
      return result;
    } on DioException catch (error) { throw Exception(_dioError(error)); }
  }

  Stream<String> streamChat({required AiProvider provider, required String key, required String baseUrl, required String model, required String system, required double temperature, required List<ChatMessage> messages}) async* {
    _cancelToken = CancelToken();
    var url = provider.custom ? _custom(baseUrl, 'chat/completions') : provider.chatUrl;
    if (provider.protocol == ApiProtocol.gemini) url = '$url/models/${Uri.encodeComponent(model)}:streamGenerateContent?alt=sse&key=${Uri.encodeQueryComponent(key)}';
    final data = switch (provider.protocol) {
      ApiProtocol.openAi => {'model': model, 'stream': true, 'temperature': temperature, 'messages': [if (system.trim().isNotEmpty) {'role': 'system', 'content': system.trim()}, ...messages.map((m) => {'role': m.role, 'content': m.content})]},
      ApiProtocol.anthropic => {'model': model, 'stream': true, 'max_tokens': 4096, 'temperature': temperature, if (system.trim().isNotEmpty) 'system': system.trim(), 'messages': messages.map((m) => {'role': m.role, 'content': m.content}).toList()},
      ApiProtocol.gemini => {if (system.trim().isNotEmpty) 'systemInstruction': {'parts': [{'text': system.trim()}]}, 'contents': messages.map((m) => {'role': m.role == 'assistant' ? 'model' : 'user', 'parts': [{'text': m.content}]}).toList(), 'generationConfig': {'temperature': temperature}},
    };
    try {
      final response = await _dio.post<ResponseBody>(url, data: data, cancelToken: _cancelToken, options: Options(headers: _headers(provider, key), responseType: ResponseType.stream));
      final lines = response.data!.stream
          .cast<List<int>>()
          .transform(utf8.decoder)
          .transform(const LineSplitter());
      await for (final line in lines) {
        if (!line.startsWith('data:')) continue;
        final payload = line.substring(5).trim();
        if (payload.isEmpty || payload == '[DONE]') continue;
        final event = jsonDecode(payload) as Map<String, dynamic>;
        if (event['error'] case final Map error) throw Exception(error['message'] ?? 'Ошибка потока');
        final delta = switch (provider.protocol) {
          ApiProtocol.openAi => (((event['choices'] as List?)?.firstOrNull as Map?)?['delta'] as Map?)?['content']?.toString() ?? '',
          ApiProtocol.anthropic => event['type'] == 'content_block_delta' ? ((event['delta'] as Map?)?['text']?.toString() ?? '') : '',
          ApiProtocol.gemini => _geminiText(event),
        };
        if (delta.isNotEmpty) yield delta;
      }
    } on DioException catch (error) {
      if (!CancelToken.isCancel(error)) throw Exception(_dioError(error));
    } finally { _cancelToken = null; }
  }

  String _geminiText(Map<String, dynamic> event) {
    final candidates = event['candidates'] as List?;
    if (candidates == null || candidates.isEmpty) return '';
    final content = (candidates.first as Map?)?['content'] as Map?;
    final parts = content?['parts'] as List? ?? const [];
    return parts.whereType<Map>().map((part) => part['text']?.toString() ?? '').join();
  }

  String _dioError(DioException error) {
    final data = error.response?.data;
    if (data is Map) return ((data['error'] as Map?)?['message'] ?? data['message'] ?? 'Ошибка ${error.response?.statusCode}').toString();
    return error.message ?? 'Не удалось подключиться к провайдеру';
  }
}

extension<T> on List<T> { T? get firstOrNull => isEmpty ? null : first; }
