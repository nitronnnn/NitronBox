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
  custom?: boolean;
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
  attachments: Attachment[];
};

export type Attachment = {
  id: string;
  name: string;
  uri: string;
  mimeType: string | null;
  size: number | null;
  textContent?: string;
};

export type ChatThread = {
  id: string;
  title: string;
  createdAt: number;
  updatedAt: number;
  messages: ChatMessage[];
};

export type AppSettings = {
  colorScheme: 'system' | 'light' | 'dark';
  haptics: boolean;
  systemPrompt: string;
  textScale: 'compact' | 'default' | 'large';
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
  sendMessage(
    request: ConnectRequest & {
      model: string;
      messages: ChatMessage[];
      systemPrompt?: string;
    },
  ): Promise<string>;
};
