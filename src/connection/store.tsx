import AsyncStorage from '@react-native-async-storage/async-storage';
import * as DocumentPicker from 'expo-document-picker';
import * as FileSystem from 'expo-file-system/legacy';
import * as Haptics from 'expo-haptics';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useReducer,
  type PropsWithChildren,
} from 'react';

import { httpConnectionGateway } from './gateway';
import { initialConnectionPoints } from './points';
import { idleRealtimeSource } from './realtime';
import type {
  AppSettings,
  Attachment,
  ChatMessage,
  ChatThread,
  ConnectionGateway,
  ConnectionPoint,
  ConnectionSnapshot,
  RealtimeConnectionSource,
} from './types';

const STORAGE_KEY = 'nitronbox-state-v1';
const defaultSettings: AppSettings = {
  colorScheme: 'system',
  haptics: true,
  systemPrompt: '',
  textScale: 'default',
};

type Screen = 'connect' | 'settings' | 'chat' | 'chats' | 'appSettings';
type State = ConnectionSnapshot & {
  hydrated: boolean;
  points: ConnectionPoint[];
  selectedPointId: string;
  apiKeys: Record<string, string>;
  customBaseUrls: Record<string, string>;
  screen: Screen;
  threads: ChatThread[];
  activeThreadId: string | null;
  pendingAttachments: Attachment[];
  sending: boolean;
  settings: AppSettings;
};

type PersistedState = Pick<
  State,
  'points' | 'selectedPointId' | 'customBaseUrls' | 'threads' | 'activeThreadId' | 'settings'
>;

type Action =
  | { type: 'hydrate'; payload: Partial<PersistedState> }
  | { type: 'select'; pointId: string }
  | { type: 'key'; pointId: string; value: string }
  | { type: 'baseUrl'; pointId: string; value: string }
  | { type: 'screen'; screen: Screen }
  | { type: 'model'; model: string }
  | { type: 'connecting' }
  | { type: 'connected'; latencyMs: number | null; modelCount: number | null; models: string[] }
  | { type: 'addProvider'; point: ConnectionPoint }
  | { type: 'updateProvider'; point: ConnectionPoint }
  | { type: 'deleteProvider'; pointId: string }
  | { type: 'newThread'; thread: ChatThread }
  | { type: 'openThread'; threadId: string }
  | { type: 'deleteThread'; threadId: string }
  | { type: 'attachments'; attachments: Attachment[] }
  | { type: 'sendStart'; threadId: string; user: ChatMessage; assistant: ChatMessage }
  | { type: 'sendSuccess'; threadId: string; assistantId: string; content: string }
  | { type: 'sendError'; threadId: string; assistantId: string; message: string }
  | { type: 'settings'; settings: AppSettings }
  | { type: 'error'; message: string }
  | { type: 'disconnect' }
  | { type: 'realtime'; patch: Partial<ConnectionSnapshot> };

const initialState: State = {
  hydrated: false,
  points: initialConnectionPoints,
  selectedPointId: initialConnectionPoints[0]!.id,
  apiKeys: {},
  customBaseUrls: {},
  screen: 'connect',
  threads: [],
  activeThreadId: null,
  pendingAttachments: [],
  sending: false,
  settings: defaultSettings,
  phase: 'idle',
  activePointId: null,
  latencyMs: null,
  modelCount: null,
  lastUpdatedAt: null,
  message: null,
  models: [],
  selectedModel: null,
};

const updateThread = (
  threads: ChatThread[],
  id: string,
  update: (thread: ChatThread) => ChatThread,
) => threads.map((thread) => (thread.id === id ? update(thread) : thread));

const reducer = (state: State, action: Action): State => {
  switch (action.type) {
    case 'hydrate':
      return { ...state, ...action.payload, apiKeys: {}, hydrated: true };
    case 'select':
      return { ...state, selectedPointId: action.pointId, models: [], selectedModel: null, message: null };
    case 'key':
      return { ...state, apiKeys: { ...state.apiKeys, [action.pointId]: action.value }, message: null };
    case 'baseUrl':
      return {
        ...state,
        customBaseUrls: { ...state.customBaseUrls, [action.pointId]: action.value },
        message: null,
      };
    case 'screen':
      return { ...state, screen: action.screen };
    case 'model':
      return { ...state, selectedModel: action.model };
    case 'connecting':
      return { ...state, phase: 'connecting', message: null };
    case 'connected':
      return {
        ...state,
        phase: 'connected',
        activePointId: state.selectedPointId,
        screen: 'chat',
        latencyMs: action.latencyMs,
        modelCount: action.modelCount,
        lastUpdatedAt: Date.now(),
        message: null,
        models: action.models,
        selectedModel:
          state.selectedModel && action.models.includes(state.selectedModel)
            ? state.selectedModel
            : action.models[0]!,
      };
    case 'addProvider':
      return {
        ...state,
        points: [...state.points, action.point],
        selectedPointId: action.point.id,
        customBaseUrls: { ...state.customBaseUrls, [action.point.id]: action.point.baseUrl },
      };
    case 'updateProvider':
      return {
        ...state,
        points: state.points.map((point) => (point.id === action.point.id ? action.point : point)),
        customBaseUrls: {
          ...state.customBaseUrls,
          [action.point.id]: action.point.baseUrl,
        },
      };
    case 'deleteProvider': {
      const points = state.points.filter((point) => point.id !== action.pointId);
      return {
        ...state,
        points,
        selectedPointId:
          state.selectedPointId === action.pointId ? initialConnectionPoints[0]!.id : state.selectedPointId,
      };
    }
    case 'newThread':
      return {
        ...state,
        threads: [action.thread, ...state.threads],
        activeThreadId: action.thread.id,
        screen: 'chat',
      };
    case 'openThread':
      return { ...state, activeThreadId: action.threadId, screen: 'chat' };
    case 'deleteThread':
      return {
        ...state,
        threads: state.threads.filter((thread) => thread.id !== action.threadId),
        activeThreadId: state.activeThreadId === action.threadId ? null : state.activeThreadId,
      };
    case 'attachments':
      return { ...state, pendingAttachments: action.attachments };
    case 'sendStart':
      return {
        ...state,
        sending: true,
        pendingAttachments: [],
        message: null,
        threads: updateThread(state.threads, action.threadId, (thread) => ({
          ...thread,
          updatedAt: Date.now(),
          messages: [...thread.messages, action.user, action.assistant],
        })),
      };
    case 'sendSuccess':
      return {
        ...state,
        sending: false,
        threads: updateThread(state.threads, action.threadId, (thread) => ({
          ...thread,
          updatedAt: Date.now(),
          messages: thread.messages.map((message) =>
            message.id === action.assistantId ? { ...message, content: action.content } : message,
          ),
        })),
      };
    case 'sendError':
      return {
        ...state,
        sending: false,
        message: action.message,
        threads: updateThread(state.threads, action.threadId, (thread) => ({
          ...thread,
          messages: thread.messages.filter((message) => message.id !== action.assistantId),
        })),
      };
    case 'settings':
      return { ...state, settings: action.settings };
    case 'error':
      return { ...state, phase: 'error', message: action.message };
    case 'disconnect':
      return {
        ...state,
        phase: 'idle',
        activePointId: null,
        latencyMs: null,
        modelCount: null,
        message: null,
        screen: 'connect',
        models: [],
        selectedModel: null,
      };
    case 'realtime':
      return { ...state, ...action.patch };
  }
};

type Store = State & {
  selectedPoint: ConnectionPoint;
  selectedKey: string;
  customBaseUrl: string;
  activeThread: ChatThread | null;
  chatMessages: ChatMessage[];
  selectPoint(id: string): void;
  setKey(value: string): void;
  setCustomBaseUrl(value: string): void;
  setScreen(screen: Screen): void;
  setModel(model: string): void;
  connect(): Promise<void>;
  sendMessage(content: string): Promise<void>;
  disconnect(): void;
  createCustomProvider(name: string, baseUrl: string): void;
  updateCustomProvider(point: ConnectionPoint): void;
  deleteCustomProvider(id: string): void;
  createThread(): void;
  openThread(id: string): void;
  deleteThread(id: string): void;
  pickAttachments(): Promise<void>;
  removeAttachment(id: string): void;
  updateSettings(patch: Partial<AppSettings>): void;
};

const ConnectionContext = createContext<Store | null>(null);
type ProviderProps = PropsWithChildren<{
  gateway?: ConnectionGateway;
  realtimeSource?: RealtimeConnectionSource;
}>;

export function ConnectionProvider({
  children,
  gateway = httpConnectionGateway,
  realtimeSource = idleRealtimeSource,
}: ProviderProps) {
  const [state, dispatch] = useReducer(reducer, initialState);
  const selectedPoint =
    state.points.find((point) => point.id === state.selectedPointId) ?? state.points[0]!;
  const selectedKey = state.apiKeys[selectedPoint.id] ?? '';
  const customBaseUrl = state.customBaseUrls[selectedPoint.id] ?? selectedPoint.baseUrl;
  const activeThread =
    state.threads.find((thread) => thread.id === state.activeThreadId) ?? null;

  useEffect(() => {
    void AsyncStorage.getItem(STORAGE_KEY)
      .then((raw) => {
        if (!raw) return dispatch({ type: 'hydrate', payload: {} });
        dispatch({ type: 'hydrate', payload: JSON.parse(raw) as PersistedState });
      })
      .catch(() => dispatch({ type: 'hydrate', payload: {} }));
  }, []);

  useEffect(() => {
    if (!state.hydrated) return;
    const persisted: PersistedState = {
      points: state.points,
      selectedPointId: state.selectedPointId,
      customBaseUrls: state.customBaseUrls,
      threads: state.threads,
      activeThreadId: state.activeThreadId,
      settings: state.settings,
    };
    void AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(persisted));
  }, [
    state.activeThreadId,
    state.customBaseUrls,
    state.hydrated,
    state.points,
    state.selectedPointId,
    state.settings,
    state.threads,
  ]);

  useEffect(
    () => realtimeSource.subscribe((patch) => dispatch({ type: 'realtime', patch })),
    [realtimeSource],
  );

  const haptic = useCallback(
    (kind: 'select' | 'success' | 'error' | 'light') => {
      if (!state.settings.haptics) return Promise.resolve();
      if (kind === 'select') return Haptics.selectionAsync();
      if (kind === 'success')
        return Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      if (kind === 'error')
        return Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
      return Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    },
    [state.settings.haptics],
  );

  const connect = useCallback(async () => {
    dispatch({ type: 'connecting' });
    await haptic('light');
    try {
      const result = await gateway.connect({
        point: selectedPoint,
        apiKey: selectedKey,
        customBaseUrl,
      });
      dispatch({ type: 'connected', ...result });
      await haptic('success');
    } catch (error) {
      dispatch({
        type: 'error',
        message: error instanceof Error ? error.message : 'Unable to connect',
      });
      await haptic('error');
    }
  }, [customBaseUrl, gateway, haptic, selectedKey, selectedPoint]);

  const sendMessage = useCallback(
    async (raw: string) => {
      const content = raw.trim();
      if ((!content && state.pendingAttachments.length === 0) || state.sending || !state.selectedModel)
        return;

      let thread = activeThread;
      if (!thread) {
        const id = `thread-${Date.now()}`;
        thread = {
          id,
          title: content.slice(0, 42) || state.pendingAttachments[0]?.name || 'New chat',
          createdAt: Date.now(),
          updatedAt: Date.now(),
          messages: [],
        };
        dispatch({ type: 'newThread', thread });
      }
      const user: ChatMessage = {
        id: `u-${Date.now()}`,
        role: 'user',
        content,
        attachments: state.pendingAttachments,
      };
      const assistant: ChatMessage = {
        id: `a-${Date.now()}`,
        role: 'assistant',
        content: '',
        attachments: [],
      };
      dispatch({ type: 'sendStart', threadId: thread.id, user, assistant });
      try {
        const response = await gateway.sendMessage({
          point: selectedPoint,
          apiKey: selectedKey,
          customBaseUrl,
          model: state.selectedModel,
          messages: [...thread.messages, user],
          systemPrompt: state.settings.systemPrompt,
        });
        dispatch({ type: 'sendSuccess', threadId: thread.id, assistantId: assistant.id, content: response });
        await haptic('success');
      } catch (error) {
        dispatch({
          type: 'sendError',
          threadId: thread.id,
          assistantId: assistant.id,
          message: error instanceof Error ? error.message : 'Unable to send message',
        });
        await haptic('error');
      }
    },
    [
      activeThread,
      customBaseUrl,
      gateway,
      haptic,
      selectedKey,
      selectedPoint,
      state.pendingAttachments,
      state.selectedModel,
      state.sending,
      state.settings.systemPrompt,
    ],
  );

  const value = useMemo<Store>(
    () => ({
      ...state,
      selectedPoint,
      selectedKey,
      customBaseUrl,
      activeThread,
      chatMessages: activeThread?.messages ?? [],
      selectPoint(pointId) {
        dispatch({ type: 'select', pointId });
        void haptic('select');
      },
      setKey(value) {
        dispatch({ type: 'key', pointId: selectedPoint.id, value });
      },
      setCustomBaseUrl(value) {
        dispatch({ type: 'baseUrl', pointId: selectedPoint.id, value });
      },
      setScreen(screen) {
        dispatch({ type: 'screen', screen });
      },
      setModel(model) {
        dispatch({ type: 'model', model });
      },
      connect,
      sendMessage,
      disconnect() {
        dispatch({ type: 'disconnect' });
        void haptic('light');
      },
      createCustomProvider(name, baseUrl) {
        const point: ConnectionPoint = {
          id: `custom-${Date.now()}`,
          name: name.trim() || 'Custom provider',
          detail: 'OpenAI-compatible',
          symbol: 'server',
          baseUrl: baseUrl.trim(),
          keyHint: 'API key',
          health: 'degraded',
          latencyMs: null,
          modelCount: null,
          custom: true,
        };
        dispatch({ type: 'addProvider', point });
      },
      updateCustomProvider(point) {
        dispatch({ type: 'updateProvider', point });
      },
      deleteCustomProvider(id) {
        dispatch({ type: 'deleteProvider', pointId: id });
      },
      createThread() {
        const now = Date.now();
        dispatch({
          type: 'newThread',
          thread: {
            id: `thread-${now}`,
            title: 'New chat',
            createdAt: now,
            updatedAt: now,
            messages: [],
          },
        });
      },
      openThread(id) {
        dispatch({ type: 'openThread', threadId: id });
      },
      deleteThread(id) {
        dispatch({ type: 'deleteThread', threadId: id });
      },
      async pickAttachments() {
        const result = await DocumentPicker.getDocumentAsync({
          multiple: true,
          copyToCacheDirectory: true,
          type: ['text/*', 'application/json', 'application/pdf', 'image/*'],
        });
        if (result.canceled) return;
        const files = await Promise.all(
          result.assets.map(async (asset): Promise<Attachment> => {
            let textContent: string | undefined;
            if (
              asset.mimeType?.startsWith('text/') ||
              asset.mimeType === 'application/json' ||
              /\.(md|txt|json|csv|js|ts|tsx|jsx|py|kt|swift)$/i.test(asset.name)
            ) {
              try {
                textContent = await FileSystem.readAsStringAsync(asset.uri);
                if (textContent.length > 80000) textContent = textContent.slice(0, 80000);
              } catch {
                textContent = undefined;
              }
            }
            return {
              id: `attachment-${Date.now()}-${asset.name}`,
              name: asset.name,
              uri: asset.uri,
              mimeType: asset.mimeType ?? null,
              size: asset.size ?? null,
              textContent,
            };
          }),
        );
        dispatch({ type: 'attachments', attachments: [...state.pendingAttachments, ...files] });
      },
      removeAttachment(id) {
        dispatch({
          type: 'attachments',
          attachments: state.pendingAttachments.filter((file) => file.id !== id),
        });
      },
      updateSettings(patch) {
        dispatch({ type: 'settings', settings: { ...state.settings, ...patch } });
      },
    }),
    [activeThread, connect, customBaseUrl, haptic, selectedKey, selectedPoint, sendMessage, state],
  );

  return <ConnectionContext.Provider value={value}>{children}</ConnectionContext.Provider>;
}

export function useConnection() {
  const value = useContext(ConnectionContext);
  if (!value) throw new Error('useConnection must be used inside ConnectionProvider');
  return value;
}
