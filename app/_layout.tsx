import { Stack } from 'expo-router';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { KeyboardProvider } from 'react-native-keyboard-controller';

import { ConnectionProvider } from '@/connection/store';

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <KeyboardProvider>
        <ConnectionProvider>
          <Stack screenOptions={{ headerShown: false, animation: 'fade' }} />
        </ConnectionProvider>
      </KeyboardProvider>
    </SafeAreaProvider>
  );
}
