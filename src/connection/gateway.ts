import type { ChatMessage, ConnectionGateway, ConnectRequest } from './types';

const timeout = (duration: number) =>
  new Promise<never>((_, reject) => {
    setTimeout(() => reject(new Error('Connection timed out')), duration);
  });

const cleanCustomBase = (request: ConnectRequest) => {
  const base = request.customBaseUrl?.trim().replace(/\/+$/, '');
  if (!base || !/^https?:\/\//i.test(base)) throw new Error('Enter a valid HTTPS endpoint');
  return `${base}${base.endsWith('/v1') ? '' : '/v1'}`;
};

const baseUrl = (request: ConnectRequest) =>
  request.point.id === 'custom' ? cleanCustomBase(request) : request.point.baseUrl;

const headers = (request: ConnectRequest): Record<string, string> => {
  if (request.point.id === 'anthropic') {
    return {
      'content-type': 'application/json',
      'x-api-key': request.apiKey.trim(),
      'anthropic-version': '2023-06-01',
    };
  }
  if (request.point.id === 'gemini') return { 'content-type': 'application/json' };
  return {
    'content-type': 'application/json',
    Authorization: `Bearer ${request.apiKey.trim()}`,
  };
};

const parseModels = (pointId: string, body: { data?: unknown[]; models?: unknown[] }) => {
  const source = body.data ?? body.models ?? [];
  return source
    .map((raw) => {
      if (!raw || typeof raw !== 'object') return '';
      const item = raw as { id?: unknown; name?: unknown; supportedGenerationMethods?: unknown };
      if (
        pointId === 'gemini' &&
        Array.isArray(item.supportedGenerationMethods) &&
        !item.supportedGenerationMethods.includes('generateContent')
      ) {
        return '';
      }
      return String(item.id ?? item.name ?? '').replace(/^models\//, '');
    })
    .filter(Boolean)
    .sort();
};

const fetchJson = async (url: string, init: RequestInit) => {
  const response = await Promise.race([fetch(url, init), timeout(30000)]);
  const body = (await response.json().catch(() => ({}))) as {
    data?: unknown[];
    models?: unknown[];
    choices?: Array<{ message?: { content?: string } }>;
    content?: Array<{ text?: string }>;
    candidates?: Array<{ content?: { parts?: Array<{ text?: string }> } }>;
    error?: { message?: string };
    message?: string;
  };
  if (!response.ok) {
    throw new Error(body.error?.message ?? body.message ?? `Request failed (${response.status})`);
  }
  return body;
};

const messageContent = (message: ChatMessage) => {
  if (message.attachments.length === 0) return message.content;
  const attached = message.attachments
    .map((file) => {
      const header = `\n\n[Attached file: ${file.name}]`;
      return file.textContent ? `${header}\n${file.textContent}` : header;
    })
    .join('');
  return `${message.content}${attached}`;
};

const modelMessages = (messages: ChatMessage[]) =>
  messages.map((message) => ({ role: message.role, content: messageContent(message) }));

export const httpConnectionGateway: ConnectionGateway = {
  async connect(request) {
    if (!request.apiKey.trim()) throw new Error('API key is required');
    const startedAt = Date.now();
    let url = `${baseUrl(request)}/models`;
    if (request.point.id === 'gemini') {
      url += `?key=${encodeURIComponent(request.apiKey.trim())}&pageSize=1000`;
    }
    const body = await fetchJson(url, { headers: headers(request) });
    const models = parseModels(request.point.id, body);
    if (models.length === 0) throw new Error('Provider returned no chat models');
    return { latencyMs: Date.now() - startedAt, modelCount: models.length, models };
  },

  async sendMessage(request) {
    if (request.point.id === 'anthropic') {
      const body = await fetchJson(`${baseUrl(request)}/messages`, {
        method: 'POST',
        headers: headers(request),
        body: JSON.stringify({
          model: request.model,
          max_tokens: 4096,
          system: request.systemPrompt?.trim() || undefined,
          messages: modelMessages(request.messages),
        }),
      });
      return body.content?.map((block) => block.text ?? '').join('') || 'Empty response';
    }

    if (request.point.id === 'gemini') {
      const url = `${baseUrl(request)}/models/${encodeURIComponent(request.model)}:generateContent?key=${encodeURIComponent(request.apiKey.trim())}`;
      const body = await fetchJson(url, {
        method: 'POST',
        headers: headers(request),
        body: JSON.stringify({
          systemInstruction: request.systemPrompt?.trim()
            ? { parts: [{ text: request.systemPrompt.trim() }] }
            : undefined,
          contents: request.messages.map((message) => ({
            role: message.role === 'assistant' ? 'model' : 'user',
            parts: [{ text: messageContent(message) }],
          })),
        }),
      });
      return (
        body.candidates?.[0]?.content?.parts?.map((part) => part.text ?? '').join('') ||
        'Empty response'
      );
    }

    const body = await fetchJson(`${baseUrl(request)}/chat/completions`, {
      method: 'POST',
      headers: headers(request),
      body: JSON.stringify({
        model: request.model,
        messages: [
          ...(request.systemPrompt?.trim()
            ? [{ role: 'system', content: request.systemPrompt.trim() }]
            : []),
          ...modelMessages(request.messages),
        ],
        stream: false,
      }),
    });
    return body.choices?.[0]?.message?.content || 'Empty response';
  },
};
