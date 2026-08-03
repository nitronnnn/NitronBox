import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { Text } from 'react-native';

import { ConnectionScreen } from './ConnectionScreen';
import { ConnectionProvider, useConnection } from './store';
import type { ConnectionGateway } from './types';

const gateway: ConnectionGateway = {
  connect: jest.fn().mockResolvedValue({ latencyMs: 37, modelCount: 12 }),
};

function StateProbe() {
  const state = useConnection();
  return <Text testID="state-probe">{`${state.selectedPointId}|${state.selectedKey}|${state.phase}`}</Text>;
}

describe('ConnectionScreen', () => {
  it('selects a provider, stores its key, and connects', async () => {
    const screen = render(
      <ConnectionProvider gateway={gateway}>
        <ConnectionScreen />
        <StateProbe />
      </ConnectionProvider>,
    );

    fireEvent.press(screen.getByTestId('primary-connect-button'));
    await waitFor(() => expect(screen.getByTestId('api-key-input')).toBeTruthy());

    fireEvent.press(screen.getByTestId('editor-point-anthropic'));
    fireEvent.changeText(screen.getByTestId('api-key-input'), 'sk-ant-test-key');

    expect(screen.getByTestId('state-probe').props.children).toBe(
      'anthropic|sk-ant-test-key|idle',
    );

    fireEvent.press(screen.getByTestId('editor-connect-button'));
    await waitFor(() => {
      expect(screen.getByTestId('state-probe').props.children).toBe(
        'anthropic|sk-ant-test-key|connected',
      );
    });
    expect(gateway.connect).toHaveBeenCalledWith(
      expect.objectContaining({
        apiKey: 'sk-ant-test-key',
        point: expect.objectContaining({ id: 'anthropic' }),
      }),
    );
  });
});
