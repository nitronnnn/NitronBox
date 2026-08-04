import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { Appearance } from 'react-native';
import { View } from 'react-native';

import { ConnectionScreen } from '@/connection/ConnectionScreen';
import { ChatScreen } from '@/chat/ChatScreen';
import { ChatListScreen } from '@/chat/ChatListScreen';
import { ConnectionEditor } from '@/connection/ConnectionEditor';
import { useConnection } from '@/connection/store';
import { AppSettingsScreen } from '@/settings/AppSettingsScreen';

export default function Index() {
  const store = useConnection();
  useEffect(() => {
    Appearance.setColorScheme(
      store.settings.colorScheme === 'system' ? null : store.settings.colorScheme,
    );
  }, [store.settings.colorScheme]);

  const screen = (() => {
    if (store.screen === 'chat') return <ChatScreen />;
    if (store.screen === 'chats') return <ChatListScreen />;
    if (store.screen === 'appSettings') return <AppSettingsScreen />;
    return <ConnectionScreen />;
  })();
  if (!store.hydrated) {
    return <View style={{ flex: 1, backgroundColor: '#000000' }} />;
  }
  return (
    <>
      <StatusBar style="auto" />
      {screen}
      <ConnectionEditor />
    </>
  );
}
