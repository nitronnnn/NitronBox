import { Stack } from 'expo-router';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { ConnectionProvider } from '@/connection/store';

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <ConnectionProvider>
        <Stack screenOptions={{ headerShown: false, animation: 'fade' }} />
      </ConnectionProvider>
    </SafeAreaProvider>
  );
}
