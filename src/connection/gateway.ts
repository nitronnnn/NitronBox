import type { ConnectionGateway, ConnectRequest } from './types';

const timeout = (duration: number) =>
  new Promise<never>((_, reject) => {
    setTimeout(() => reject(new Error('Connection timed out')), duration);
  });

const modelsUrl = ({ point, customBaseUrl }: ConnectRequest) => {
  if (point.id === 'custom') {
    const base = customBaseUrl?.trim().replace(/\/+$/, '');
    if (!base || !/^https?:\/\//i.test(base)) {
      throw new Error('Enter a valid HTTPS endpoint');
    }
    return `${base}${base.endsWith('/v1') ? '' : '/v1'}/models`;
  }
  return `${point.baseUrl}/models`;
};

export const httpConnectionGateway: ConnectionGateway = {
  async connect(request) {
    if (!request.apiKey.trim()) throw new Error('API key is required');
    const startedAt = Date.now();
    const headers: Record<string, string> = {};
    let url = modelsUrl(request);

    if (request.point.id === 'anthropic') {
      headers['x-api-key'] = request.apiKey.trim();
      headers['anthropic-version'] = '2023-06-01';
    } else if (request.point.id === 'gemini') {
      url += `?key=${encodeURIComponent(request.apiKey.trim())}&pageSize=1000`;
    } else {
      headers.Authorization = `Bearer ${request.apiKey.trim()}`;
    }

    const response = await Promise.race([
      fetch(url, { headers }),
      timeout(15000),
    ]);
    const body = (await response.json().catch(() => ({}))) as {
      data?: unknown[];
      models?: unknown[];
      error?: { message?: string };
      message?: string;
    };
    if (!response.ok) {
      throw new Error(body.error?.message ?? body.message ?? `Connection failed (${response.status})`);
    }
    return {
      latencyMs: Date.now() - startedAt,
      modelCount: (body.data ?? body.models ?? []).length,
    };
  },
};
