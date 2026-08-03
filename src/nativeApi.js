import { Capacitor, CapacitorHttp } from '@capacitor/core';

const PRESETS = {
  openai: { url: 'https://api.openai.com/v1/chat/completions', protocol: 'openai' },
  anthropic: { url: 'https://api.anthropic.com/v1/messages', protocol: 'anthropic' },
  gemini: { url: 'https://generativelanguage.googleapis.com/v1beta', protocol: 'gemini' },
  openrouter: { url: 'https://openrouter.ai/api/v1/chat/completions', protocol: 'openai' },
  groq: { url: 'https://api.groq.com/openai/v1/chat/completions', protocol: 'openai' },
  mistral: { url: 'https://api.mistral.ai/v1/chat/completions', protocol: 'openai' },
  xai: { url: 'https://api.x.ai/v1/chat/completions', protocol: 'openai' },
};

export function isNativeApp() {
  return Capacitor.isNativePlatform();
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
      ...(provider === 'openrouter' ? { 'HTTP-Referer': 'https://prism.local', 'X-Title': 'Prism AI' } : {}),
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
