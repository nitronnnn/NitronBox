import { StatusBar } from 'expo-status-bar';

import { ConnectionScreen } from '@/connection/ConnectionScreen';

export default function Index() {
  return (
    <>
      <StatusBar style="auto" />
      <ConnectionScreen />
    </>
  );
}
