import { Capacitor, CapacitorHttp } from '@capacitor/core';

const PRESETS = {
  openai: { url: 'https://api.openai.com/v1/chat/completions', models: 'https://api.openai.com/v1/models', protocol: 'openai' },
  anthropic: { url: 'https://api.anthropic.com/v1/messages', models: 'https://api.anthropic.com/v1/models?limit=1000', protocol: 'anthropic' },
  gemini: { url: 'https://generativelanguage.googleapis.com/v1beta', models: 'https://generativelanguage.googleapis.com/v1beta/models', protocol: 'gemini' },
  openrouter: { url: 'https://openrouter.ai/api/v1/chat/completions', models: 'https://openrouter.ai/api/v1/models', protocol: 'openai' },
  groq: { url: 'https://api.groq.com/openai/v1/chat/completions', models: 'https://api.groq.com/openai/v1/models', protocol: 'openai' },
  mistral: { url: 'https://api.mistral.ai/v1/chat/completions', models: 'https://api.mistral.ai/v1/models', protocol: 'openai' },
  xai: { url: 'https://api.x.ai/v1/chat/completions', models: 'https://api.x.ai/v1/models', protocol: 'openai' },
};

export function isNativeApp() {
  return Capacitor.isNativePlatform();
}

function customModelsUrl(value) {
  const clean = value.replace(/\/+$/, '');
  if (/\/chat\/completions$/.test(clean)) return clean.replace(/\/chat\/completions$/, '/models');
  return `${clean}${/\/v1$/.test(clean) ? '' : '/v1'}/models`;
}

export async function requestNativeModels({ provider, apiKey, baseUrl }) {
  const preset = PRESETS[provider];
  const protocol = provider === 'custom' ? 'openai' : preset?.protocol;
  let url = provider === 'custom' ? customModelsUrl(baseUrl) : preset.models;
  const headers = protocol === 'anthropic'
    ? { 'x-api-key': apiKey, 'anthropic-version': '2023-06-01' }
    : protocol === 'gemini' ? {} : { Authorization: `Bearer ${apiKey}` };
  if (protocol === 'gemini') url += `?key=${encodeURIComponent(apiKey)}&pageSize=1000`;
  const response = await CapacitorHttp.get({ url, headers, connectTimeout: 30000, readTimeout: 60000 });
  const body = typeof response.data === 'string' ? JSON.parse(response.data) : response.data;
  if (response.status < 200 || response.status >= 300) {
    throw new Error(body?.error?.message || body?.message || `Ошибка каталога ${response.status}`);
  }
  const source = protocol === 'gemini' ? body.models || [] : body.data || body.models || [];
  return source
    .filter((item) => protocol !== 'gemini' || item.supportedGenerationMethods?.includes('generateContent'))
    .map((item) => ({
      id: protocol === 'gemini' ? String(item.name || '').replace(/^models\//, '') : item.id || item.name,
      name: item.displayName || item.name || item.id,
      description: item.description || item.owned_by || '',
    }))
    .filter((item) => item.id)
    .sort((a, b) => a.name.localeCompare(b.name));
}

function customUrl(value) {
  const clean = value.replace(/\/+$/, '');
  if (/\/chat\/completions$/.test(clean)) return clean;
  return `${clean}${/\/v1$/.test(clean) ? '' : '/v1'}/chat/completions`;
}

export async function requestNative({ provider, apiKey, baseUrl, model, temperature, messages }) {
  const preset = PRESETS[provider];
  const protocol = provider === 'custom' ? 'openai' : preset?.protocol;
  let url = provider === 'custom' ? customUrl(baseUrl) : preset.url;
  let headers;
  let data;

  if (protocol === 'anthropic') {
    const system = messages.filter((item) => item.role === 'system').map((item) => item.content).join('\n\n');
    headers = { 'Content-Type': 'application/json', 'x-api-key': apiKey, 'anthropic-version': '2023-06-01' };
    data = {
      model,
      max_tokens: 4096,
      temperature,
      ...(system ? { system } : {}),
      messages: messages.filter((item) => item.role !== 'system'),
    };
  } else if (protocol === 'gemini') {
    const system = messages.filter((item) => item.role === 'system').map((item) => item.content).join('\n\n');
    url = `${preset.url}/models/${encodeURIComponent(model)}:generateContent?key=${encodeURIComponent(apiKey)}`;
    headers = { 'Content-Type': 'application/json' };
    data = {
      ...(system ? { systemInstruction: { parts: [{ text: system }] } } : {}),
      contents: messages.filter((item) => item.role !== 'system').map((item) => ({
        role: item.role === 'assistant' ? 'model' : 'user',
        parts: [{ text: item.content }],
      })),
      generationConfig: { temperature },
    };
  } else {
    headers = {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${apiKey}`,
      ...(provider === 'openrouter' ? { 'HTTP-Referer': 'https://nitronbox.local', 'X-Title': 'NitronBox' } : {}),
    };
    data = { model, messages, temperature, stream: false };
  }

  const response = await CapacitorHttp.post({ url, headers, data, connectTimeout: 30000, readTimeout: 180000 });
  const body = typeof response.data === 'string' ? JSON.parse(response.data) : response.data;
  if (response.status < 200 || response.status >= 300) {
    throw new Error(body?.error?.message || body?.message || `Ошибка провайдера ${response.status}`);
  }
  if (protocol === 'anthropic') return body.content?.map((item) => item.text || '').join('') || '';
  if (protocol === 'gemini') return body.candidates?.[0]?.content?.parts?.map((item) => item.text || '').join('') || '';
  return body.choices?.[0]?.message?.content || '';
}
