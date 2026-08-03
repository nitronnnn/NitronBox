import { StatusBar } from 'expo-status-bar';

import { ConnectionScreen } from '@/connection/ConnectionScreen';
import { ChatScreen } from '@/chat/ChatScreen';
import { ConnectionEditor } from '@/connection/ConnectionEditor';
import { useConnection } from '@/connection/store';

export default function Index() {
  const store = useConnection();
  return (
    <>
      <StatusBar style="auto" />
      {store.screen === 'chat' ? <ChatScreen /> : <ConnectionScreen />}
      <ConnectionEditor />
    </>
  );
}
