import express from 'express';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const app = express();
const port = Number(process.env.PORT) || 8788;
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

app.use(express.json({ limit: '2mb' }));

const PROVIDERS = {
  openai: { url: 'https://api.openai.com/v1/chat/completions', models: 'https://api.openai.com/v1/models', protocol: 'openai' },
  anthropic: { url: 'https://api.anthropic.com/v1/messages', models: 'https://api.anthropic.com/v1/models?limit=1000', protocol: 'anthropic' },
  gemini: { url: 'https://generativelanguage.googleapis.com/v1beta', models: 'https://generativelanguage.googleapis.com/v1beta/models', protocol: 'gemini' },
  openrouter: { url: 'https://openrouter.ai/api/v1/chat/completions', models: 'https://openrouter.ai/api/v1/models', protocol: 'openai' },
  groq: { url: 'https://api.groq.com/openai/v1/chat/completions', models: 'https://api.groq.com/openai/v1/models', protocol: 'openai' },
  mistral: { url: 'https://api.mistral.ai/v1/chat/completions', models: 'https://api.mistral.ai/v1/models', protocol: 'openai' },
  xai: { url: 'https://api.x.ai/v1/chat/completions', models: 'https://api.x.ai/v1/models', protocol: 'openai' },
};

function cleanBaseUrl(value) {
  const url = new URL(value);
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error('Разрешены только HTTP и HTTPS URL');
  return value.replace(/\/+$/, '');
}

function openAiUrl(base) {
  if (/\/chat\/completions\/?$/.test(base)) return base;
  return `${base}${/\/v1$/.test(base) ? '' : '/v1'}/chat/completions`;
}

function modelsUrl(base) {
  if (/\/chat\/completions\/?$/.test(base)) return base.replace(/\/chat\/completions\/?$/, '/models');
  return `${base}${/\/v1$/.test(base) ? '' : '/v1'}/models`;
}

function createRequest({ provider, apiKey, baseUrl, model, messages, temperature }) {
  const preset = PROVIDERS[provider];
  const protocol = provider === 'custom' ? 'openai' : preset?.protocol;
  if (!protocol) throw new Error('Неизвестный провайдер');

  if (protocol === 'anthropic') {
    const system = messages.filter((item) => item.role === 'system').map((item) => item.content).join('\n\n');
    return {
      url: preset.url,
      headers: {
        'content-type': 'application/json',
        'x-api-key': apiKey,
        'anthropic-version': '2023-06-01',
      },
      body: {
        model,
        max_tokens: 4096,
        stream: true,
        temperature,
        ...(system ? { system } : {}),
        messages: messages.filter((item) => item.role !== 'system'),
      },
      protocol,
    };
  }

  if (protocol === 'gemini') {
    const system = messages.filter((item) => item.role === 'system').map((item) => item.content).join('\n\n');
    return {
      url: `${preset.url}/models/${encodeURIComponent(model)}:streamGenerateContent?alt=sse`,
      headers: { 'content-type': 'application/json', 'x-goog-api-key': apiKey },
      body: {
        ...(system ? { systemInstruction: { parts: [{ text: system }] } } : {}),
        contents: messages.filter((item) => item.role !== 'system').map((item) => ({
          role: item.role === 'assistant' ? 'model' : 'user',
          parts: [{ text: item.content }],
        })),
        generationConfig: { temperature },
      },
      protocol,
    };
  }

  const rawUrl = provider === 'custom' ? cleanBaseUrl(baseUrl) : preset.url;
  return {
    url: provider === 'custom' ? openAiUrl(rawUrl) : rawUrl,
    headers: {
      'content-type': 'application/json',
      authorization: `Bearer ${apiKey}`,
      ...(provider === 'openrouter' ? { 'HTTP-Referer': 'http://localhost', 'X-Title': 'NitronBox' } : {}),
    },
    body: { model, messages, stream: true, temperature },
    protocol,
  };
}

app.post('/api/models', async (req, res) => {
  const { provider, apiKey, baseUrl } = req.body || {};
  const preset = PROVIDERS[provider];
  const protocol = provider === 'custom' ? 'openai' : preset?.protocol;
  if (!protocol || !apiKey) return res.status(400).json({ error: 'Укажите провайдера и API-ключ' });
  try {
    let url = provider === 'custom' ? modelsUrl(cleanBaseUrl(baseUrl)) : preset.models;
    const headers = protocol === 'anthropic'
      ? { 'x-api-key': apiKey, 'anthropic-version': '2023-06-01' }
      : protocol === 'gemini' ? {} : { authorization: `Bearer ${apiKey}` };
    if (protocol === 'gemini') url += `?key=${encodeURIComponent(apiKey)}&pageSize=1000`;
    const upstream = await fetch(url, { headers });
    const body = await upstream.json().catch(() => ({}));
    if (!upstream.ok) return res.status(upstream.status).json({ error: body.error?.message || body.message || `Ошибка каталога ${upstream.status}` });
    const source = protocol === 'gemini' ? body.models || [] : body.data || body.models || [];
    const models = source
      .filter((item) => protocol !== 'gemini' || item.supportedGenerationMethods?.includes('generateContent'))
      .map((item) => ({
        id: protocol === 'gemini' ? String(item.name || '').replace(/^models\//, '') : item.id || item.name,
        name: item.displayName || item.name || item.id,
        description: item.description || item.owned_by || '',
      }))
      .filter((item) => item.id)
      .sort((a, b) => a.name.localeCompare(b.name));
    res.json({ models });
  } catch (error) {
    res.status(500).json({ error: error.message || 'Не удалось загрузить модели' });
  }
});

function extractDelta(data, protocol) {
  if (protocol === 'anthropic') return data.type === 'content_block_delta' ? data.delta?.text || '' : '';
  if (protocol === 'gemini') return data.candidates?.[0]?.content?.parts?.map((part) => part.text || '').join('') || '';
  return data.choices?.[0]?.delta?.content || '';
}

app.post('/api/chat', async (req, res) => {
  const { provider, apiKey, baseUrl, model, messages, temperature = 0.7 } = req.body || {};
  if (!apiKey || !model || !Array.isArray(messages)) {
    return res.status(400).json({ error: 'Укажите API-ключ, модель и сообщение' });
  }

  const controller = new AbortController();
  req.on('close', () => controller.abort());

  try {
    const request = createRequest({ provider, apiKey, baseUrl, model, messages, temperature });
    const upstream = await fetch(request.url, {
      method: 'POST',
      headers: request.headers,
      body: JSON.stringify(request.body),
      signal: controller.signal,
    });

    if (!upstream.ok) {
      const raw = await upstream.text();
      let detail = raw;
      try {
        const parsed = JSON.parse(raw);
        detail = parsed.error?.message || parsed.message || raw;
      } catch {}
      return res.status(upstream.status).json({ error: detail.slice(0, 1200) || `Ошибка провайдера ${upstream.status}` });
    }

    res.status(200);
    res.setHeader('Content-Type', 'application/x-ndjson; charset=utf-8');
    res.setHeader('Cache-Control', 'no-cache, no-transform');
    res.setHeader('X-Accel-Buffering', 'no');

    const reader = upstream.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const events = buffer.split(/\r?\n\r?\n/);
      buffer = events.pop() || '';
      for (const event of events) {
        const payload = event.split(/\r?\n/).find((line) => line.startsWith('data:'))?.slice(5).trim();
        if (!payload || payload === '[DONE]') continue;
        try {
          const parsed = JSON.parse(payload);
          if (parsed.error) throw new Error(parsed.error.message || 'Ошибка потока');
          const delta = extractDelta(parsed, request.protocol);
          if (delta) res.write(`${JSON.stringify({ delta })}\n`);
        } catch (error) {
          if (error instanceof SyntaxError) continue;
          res.write(`${JSON.stringify({ error: error.message })}\n`);
        }
      }
    }
    res.write(`${JSON.stringify({ done: true })}\n`);
    res.end();
  } catch (error) {
    if (error.name === 'AbortError') return;
    if (res.headersSent) {
      res.write(`${JSON.stringify({ error: error.message || 'Соединение прервано' })}\n`);
      return res.end();
    }
    res.status(500).json({ error: error.message || 'Не удалось выполнить запрос' });
  }
});

app.use(express.static(path.join(root, 'dist')));
app.get('*', (req, res, next) => {
  if (req.path.startsWith('/api/')) return next();
  res.sendFile(path.join(root, 'dist', 'index.html'));
});

app.listen(port, '127.0.0.1', () => {
  console.log(`NitronBox server: http://127.0.0.1:${port}`);
});
