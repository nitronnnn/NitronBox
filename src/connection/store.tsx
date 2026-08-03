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
  ConnectionGateway,
  ConnectionPoint,
  ConnectionSnapshot,
  ChatMessage,
  RealtimeConnectionSource,
} from './types';

type State = ConnectionSnapshot & {
  points: ConnectionPoint[];
  selectedPointId: string;
  apiKeys: Record<string, string>;
  customBaseUrl: string;
  screen: 'connect' | 'settings' | 'chat';
  chatMessages: ChatMessage[];
  sending: boolean;
};

type Action =
  | { type: 'select'; pointId: string }
  | { type: 'key'; pointId: string; value: string }
  | { type: 'baseUrl'; value: string }
  | { type: 'screen'; screen: State['screen'] }
  | { type: 'model'; model: string }
  | { type: 'connecting' }
  | {
      type: 'connected';
      latencyMs: number | null;
      modelCount: number | null;
      models: string[];
    }
  | { type: 'sendStart'; user: ChatMessage; assistant: ChatMessage }
  | { type: 'sendSuccess'; assistantId: string; content: string }
  | { type: 'sendError'; assistantId: string; message: string }
  | { type: 'error'; message: string }
  | { type: 'disconnect' }
  | { type: 'realtime'; patch: Partial<ConnectionSnapshot> };

const initialState: State = {
  points: initialConnectionPoints,
  selectedPointId: initialConnectionPoints[0]!.id,
  apiKeys: {},
  customBaseUrl: '',
  screen: 'connect',
  chatMessages: [],
  sending: false,
  phase: 'idle',
  activePointId: null,
  latencyMs: null,
  modelCount: null,
  lastUpdatedAt: null,
  message: null,
  models: [],
  selectedModel: null,
};

const reducer = (state: State, action: Action): State => {
  switch (action.type) {
    case 'select':
      return { ...state, selectedPointId: action.pointId, message: null };
    case 'key':
      return {
        ...state,
        apiKeys: { ...state.apiKeys, [action.pointId]: action.value },
        message: null,
      };
    case 'baseUrl':
      return { ...state, customBaseUrl: action.value, message: null };
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
    case 'sendStart':
      return {
        ...state,
        sending: true,
        message: null,
        chatMessages: [...state.chatMessages, action.user, action.assistant],
      };
    case 'sendSuccess':
      return {
        ...state,
        sending: false,
        chatMessages: state.chatMessages.map((message) =>
          message.id === action.assistantId ? { ...message, content: action.content } : message,
        ),
      };
    case 'sendError':
      return {
        ...state,
        sending: false,
        message: action.message,
        chatMessages: state.chatMessages.filter((message) => message.id !== action.assistantId),
      };
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
        chatMessages: [],
      };
    case 'realtime':
      return { ...state, ...action.patch };
  }
};

type Store = State & {
  selectedPoint: ConnectionPoint;
  selectedKey: string;
  selectPoint(id: string): void;
  setKey(value: string): void;
  setCustomBaseUrl(value: string): void;
  setScreen(screen: State['screen']): void;
  setModel(model: string): void;
  connect(): Promise<void>;
  sendMessage(content: string): Promise<void>;
  disconnect(): void;
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

  useEffect(
    () => realtimeSource.subscribe((patch) => dispatch({ type: 'realtime', patch })),
    [realtimeSource],
  );

  const connect = useCallback(async () => {
    dispatch({ type: 'connecting' });
    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    try {
      const result = await gateway.connect({
        point: selectedPoint,
        apiKey: selectedKey,
        customBaseUrl: state.customBaseUrl,
      });
      dispatch({ type: 'connected', ...result });
      await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    } catch (error) {
      dispatch({
        type: 'error',
        message: error instanceof Error ? error.message : 'Unable to connect',
      });
      await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
    }
  }, [gateway, selectedKey, selectedPoint, state.customBaseUrl]);

  const sendMessage = useCallback(
    async (raw: string) => {
      const content = raw.trim();
      if (!content || state.sending || !state.selectedModel) return;
      const user: ChatMessage = {
        id: `u-${Date.now()}`,
        role: 'user',
        content,
      };
      const assistant: ChatMessage = {
        id: `a-${Date.now()}`,
        role: 'assistant',
        content: '',
      };
      dispatch({ type: 'sendStart', user, assistant });
      try {
        const response = await gateway.sendMessage({
          point: selectedPoint,
          apiKey: selectedKey,
          customBaseUrl: state.customBaseUrl,
          model: state.selectedModel,
          messages: [...state.chatMessages, user],
        });
        dispatch({ type: 'sendSuccess', assistantId: assistant.id, content: response });
        await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      } catch (error) {
        dispatch({
          type: 'sendError',
          assistantId: assistant.id,
          message: error instanceof Error ? error.message : 'Unable to send message',
        });
        await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
      }
    },
    [gateway, selectedKey, selectedPoint, state.chatMessages, state.customBaseUrl, state.selectedModel, state.sending],
  );

  const value = useMemo<Store>(
    () => ({
      ...state,
      selectedPoint,
      selectedKey,
      selectPoint(pointId) {
        dispatch({ type: 'select', pointId });
        void Haptics.selectionAsync();
      },
      setKey(value) {
        dispatch({ type: 'key', pointId: selectedPoint.id, value });
      },
      setCustomBaseUrl(value) {
        dispatch({ type: 'baseUrl', value });
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
        void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
      },
    }),
    [connect, selectedKey, selectedPoint, sendMessage, state],
  );

  return <ConnectionContext.Provider value={value}>{children}</ConnectionContext.Provider>;
}

export function useConnection() {
  const value = useContext(ConnectionContext);
  if (!value) throw new Error('useConnection must be used inside ConnectionProvider');
  return value;
}
