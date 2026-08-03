export type ConnectionPhase = 'idle' | 'connecting' | 'connected' | 'error';
export type PointHealth = 'online' | 'degraded' | 'offline';

export type ConnectionPoint = {
  id: string;
  name: string;
  detail: string;
  symbol: 'sparkles' | 'brain' | 'gem' | 'server';
  baseUrl: string;
  keyHint: string;
  health: PointHealth;
  latencyMs: number | null;
  modelCount: number | null;
};

export type ConnectionSnapshot = {
  phase: ConnectionPhase;
  activePointId: string | null;
  latencyMs: number | null;
  modelCount: number | null;
  lastUpdatedAt: number | null;
  message: string | null;
  models: string[];
  selectedModel: string | null;
};

export type ChatMessage = {
  id: string;
  role: 'user' | 'assistant';
  content: string;
};

export type RealtimeConnectionSource = {
  subscribe(listener: (snapshot: Partial<ConnectionSnapshot>) => void): () => void;
};

export type ConnectRequest = {
  point: ConnectionPoint;
  apiKey: string;
  customBaseUrl?: string;
};

export type ConnectionGateway = {
  connect(
    request: ConnectRequest,
  ): Promise<Pick<ConnectionSnapshot, 'latencyMs' | 'modelCount' | 'models'>>;
  sendMessage(request: ConnectRequest & { model: string; messages: ChatMessage[] }): Promise<string>;
};
