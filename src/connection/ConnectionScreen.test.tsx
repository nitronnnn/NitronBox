import { act, fireEvent, render, waitFor } from '@testing-library/react-native';
import { Text } from 'react-native';
import * as DocumentPicker from 'expo-document-picker';
import * as Clipboard from 'expo-clipboard';
import * as SecureStore from 'expo-secure-store';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Pressable } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { ConnectionScreen } from './ConnectionScreen';
import { ConnectionEditor } from './ConnectionEditor';
import { ConnectionProvider, useConnection } from './store';
import type { ConnectionGateway } from './types';
import { ChatScreen } from '@/chat/ChatScreen';

const gateway: ConnectionGateway = {
  connect: jest.fn().mockResolvedValue({
    latencyMs: 37,
    modelCount: 2,
    models: ['claude-test', 'claude-fast'],
  }),
  sendMessage: jest.fn().mockResolvedValue('Hello from the model'),
};

function StateProbe() {
  const state = useConnection();
  return <>
    <Text testID="state-probe">
      {`${state.selectedPointId}|${state.selectedKey}|${state.phase}|${state.screen}|${state.points.length}|${state.pendingAttachments.length}|${state.hydrated}`}
    </Text>
    <Pressable testID="probe-pick-files" onPress={() => void state.pickAttachments()} />
  </>;
}

function TestRoot() {
  const state = useConnection();
  if (!state.hydrated) return <StateProbe />;
  return <>
    {state.screen === 'chat' ? <ChatScreen /> : <ConnectionScreen />}
    <ConnectionEditor />
    <StateProbe />
  </>;
}

const renderHarness = () =>
  render(
    <ConnectionProvider gateway={gateway}>
      <SafeAreaProvider
        initialMetrics={{
          frame: { x: 0, y: 0, width: 390, height: 844 },
          insets: { top: 47, left: 0, right: 0, bottom: 34 },
        }}
      >
        <TestRoot />
      </SafeAreaProvider>
    </ConnectionProvider>,
  );

describe('ConnectionScreen', () => {
  beforeEach(async () => {
    await AsyncStorage.clear();
    jest.clearAllMocks();
    (SecureStore.getItemAsync as jest.Mock).mockResolvedValue(null);
  });

  it('selects a provider, stores its key, and connects', async () => {
    const screen = renderHarness();

    await waitFor(() => expect(screen.getByTestId('state-probe').props.children).toContain('|true'));
    fireEvent.press(screen.getByTestId('primary-connect-button'));
    await waitFor(() => expect(screen.getByTestId('api-key-input')).toBeTruthy());

    fireEvent.press(screen.getByTestId('editor-point-anthropic'));
    fireEvent.changeText(screen.getByTestId('api-key-input'), 'sk-ant-test-key');

    expect(screen.getByTestId('state-probe').props.children).toBe(
      'anthropic|sk-ant-test-key|idle|settings|4|0|true',
    );

    fireEvent.press(screen.getByTestId('editor-connect-button'));
    await waitFor(() => {
      expect(screen.getByTestId('state-probe').props.children).toBe(
        'anthropic|sk-ant-test-key|connected|chat|4|0|true',
      );
    });
    expect(gateway.connect).toHaveBeenCalledWith(
      expect.objectContaining({
        apiKey: 'sk-ant-test-key',
        point: expect.objectContaining({ id: 'anthropic' }),
      }),
    );

    fireEvent.changeText(screen.getByTestId('chat-input'), 'Hello');
    fireEvent.press(screen.getByTestId('chat-send-button'));
    await waitFor(() => expect(screen.getByText('Hello from the model')).toBeTruthy());
    expect(gateway.sendMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        model: 'claude-test',
        messages: expect.arrayContaining([
          expect.objectContaining({ role: 'user', content: 'Hello' }),
        ]),
      }),
    );
    await act(async () => {
      fireEvent.press(screen.getByLabelText('Copy response'));
      await Promise.resolve();
    });
    expect(Clipboard.setStringAsync).toHaveBeenCalledWith('Hello from the model');
  });

  it('shows the key and adds multiple custom providers', async () => {
    const screen = renderHarness();
    await waitFor(() => expect(screen.getByTestId('state-probe').props.children).toContain('|true'));
    fireEvent.press(screen.getByTestId('primary-connect-button'));
    fireEvent.changeText(screen.getByTestId('api-key-input'), 'visible-key');
    fireEvent.press(screen.getByTestId('toggle-api-key'));
    expect(screen.getByTestId('api-key-input').props.secureTextEntry).toBe(false);

    for (const [name, url] of [
      ['Local One', 'https://one.example.com'],
      ['Local Two', 'https://two.example.com'],
    ]) {
      fireEvent.press(screen.getByTestId('add-custom-provider'));
      fireEvent.changeText(screen.getByTestId('custom-provider-name'), name);
      fireEvent.changeText(screen.getByTestId('custom-provider-url'), url);
      fireEvent.press(screen.getByTestId('save-custom-provider'));
      await waitFor(() => {
        const count = Number(
          String(screen.getByTestId('state-probe').props.children).split('|')[4],
        );
        expect(count).toBe(name === 'Local One' ? 5 : 6);
      });
    }
    expect(screen.getByTestId('state-probe').props.children).toContain('|6|0');
  });

  it('picks an attachment into pending input', async () => {
    const screen = renderHarness();
    await waitFor(() => expect(screen.getByTestId('state-probe').props.children).toContain('|true'));
    (DocumentPicker.getDocumentAsync as jest.Mock).mockResolvedValueOnce({
      canceled: false,
      assets: [
        {
          name: 'notes.txt',
          uri: 'file:///notes.txt',
          mimeType: 'text/plain',
          size: 12,
        },
      ],
    });
    fireEvent.press(screen.getByTestId('probe-pick-files'));
    await waitFor(() => {
      expect(screen.getByTestId('state-probe').props.children).toContain('|1');
    });
    expect(DocumentPicker.getDocumentAsync).toHaveBeenCalled();
  });

  it('restores the selected provider, model, chat, and secure API key on restart', async () => {
    await AsyncStorage.setItem(
      'nitronbox-state-v2',
      JSON.stringify({
        points: [
          {
            id: 'anthropic',
            name: 'Anthropic',
            detail: 'Official API',
            symbol: 'brain',
            baseUrl: 'https://api.anthropic.com/v1',
            keyHint: 'sk-ant-...',
            health: 'online',
            latencyMs: null,
            modelCount: null,
            custom: false,
          },
        ],
        selectedPointId: 'anthropic',
        customBaseUrls: {},
        threads: [
          {
            id: 'thread-restored',
            title: 'Restored chat',
            createdAt: 1,
            updatedAt: 2,
            messages: [
              {
                id: 'm1',
                role: 'user',
                content: 'Saved message',
                attachments: [],
              },
            ],
          },
        ],
        activeThreadId: 'thread-restored',
        selectedModel: 'claude-restored',
        models: ['claude-restored'],
        lastScreen: 'chat',
        settings: {
          colorScheme: 'system',
          haptics: true,
          systemPrompt: '',
          textScale: 'default',
        },
      }),
    );
    (SecureStore.getItemAsync as jest.Mock).mockResolvedValueOnce(
      JSON.stringify({ anthropic: 'secure-restored-key' }),
    );

    const screen = renderHarness();
    await waitFor(() => {
      expect(screen.getByTestId('state-probe').props.children).toBe(
        'anthropic|secure-restored-key|idle|chat|1|0|true',
      );
    });
    expect(screen.getByText('Saved message')).toBeTruthy();
    expect(screen.getByText('claude-restored')).toBeTruthy();
  });
});
