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
  RealtimeConnectionSource,
} from './types';

type State = ConnectionSnapshot & {
  points: ConnectionPoint[];
  selectedPointId: string;
  apiKeys: Record<string, string>;
  customBaseUrl: string;
  editorVisible: boolean;
};

type Action =
  | { type: 'select'; pointId: string }
  | { type: 'key'; pointId: string; value: string }
  | { type: 'baseUrl'; value: string }
  | { type: 'editor'; visible: boolean }
  | { type: 'connecting' }
  | { type: 'connected'; latencyMs: number | null; modelCount: number | null }
  | { type: 'error'; message: string }
  | { type: 'disconnect' }
  | { type: 'realtime'; patch: Partial<ConnectionSnapshot> };

const initialState: State = {
  points: initialConnectionPoints,
  selectedPointId: initialConnectionPoints[0]!.id,
  apiKeys: {},
  customBaseUrl: '',
  editorVisible: false,
  phase: 'idle',
  activePointId: null,
  latencyMs: null,
  modelCount: null,
  lastUpdatedAt: null,
  message: null,
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
    case 'editor':
      return { ...state, editorVisible: action.visible };
    case 'connecting':
      return { ...state, phase: 'connecting', message: null };
    case 'connected':
      return {
        ...state,
        phase: 'connected',
        activePointId: state.selectedPointId,
        editorVisible: false,
        latencyMs: action.latencyMs,
        modelCount: action.modelCount,
        lastUpdatedAt: Date.now(),
        message: null,
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
  setEditorVisible(value: boolean): void;
  connect(): Promise<void>;
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
      setEditorVisible(visible) {
        dispatch({ type: 'editor', visible });
      },
      connect,
      disconnect() {
        dispatch({ type: 'disconnect' });
        void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
      },
    }),
    [connect, selectedKey, selectedPoint, state],
  );

  return <ConnectionContext.Provider value={value}>{children}</ConnectionContext.Provider>;
}

export function useConnection() {
  const value = useContext(ConnectionContext);
  if (!value) throw new Error('useConnection must be used inside ConnectionProvider');
  return value;
}
