import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { Text } from 'react-native';
import * as DocumentPicker from 'expo-document-picker';
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
      {`${state.selectedPointId}|${state.selectedKey}|${state.phase}|${state.screen}|${state.points.length}|${state.pendingAttachments.length}`}
    </Text>
    <Pressable testID="probe-pick-files" onPress={() => void state.pickAttachments()} />
  </>;
}

function TestRoot() {
  const state = useConnection();
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
  });

  it('selects a provider, stores its key, and connects', async () => {
    const screen = renderHarness();

    fireEvent.press(screen.getByTestId('primary-connect-button'));
    await waitFor(() => expect(screen.getByTestId('api-key-input')).toBeTruthy());
    await waitFor(() => expect(screen.getByTestId('api-key-input')).toBeTruthy());

    fireEvent.press(screen.getByTestId('editor-point-anthropic'));
    fireEvent.changeText(screen.getByTestId('api-key-input'), 'sk-ant-test-key');

    expect(screen.getByTestId('state-probe').props.children).toBe(
      'anthropic|sk-ant-test-key|idle|settings|4|0',
    );

    fireEvent.press(screen.getByTestId('editor-connect-button'));
    await waitFor(() => {
      expect(screen.getByTestId('state-probe').props.children).toBe(
        'anthropic|sk-ant-test-key|connected|chat|4|0',
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
  });

  it('shows the key and adds multiple custom providers', async () => {
    const screen = renderHarness();
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
});
