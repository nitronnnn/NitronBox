import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { Appearance } from 'react-native';
import { View } from 'react-native';
import * as NavigationBar from 'expo-navigation-bar';
import Animated, { FadeIn, FadeOut, SlideInRight } from 'react-native-reanimated';

import { ConnectionScreen } from '@/connection/ConnectionScreen';
import { ChatScreen } from '@/chat/ChatScreen';
import { ChatListScreen } from '@/chat/ChatListScreen';
import { ConnectionEditor } from '@/connection/ConnectionEditor';
import { useConnection } from '@/connection/store';
import { AppSettingsScreen } from '@/settings/AppSettingsScreen';

export default function Index() {
  const store = useConnection();
  useEffect(() => {
    const scheme = store.settings.colorScheme === 'system' ? null : store.settings.colorScheme;
    Appearance.setColorScheme(scheme);
    const resolved = scheme ?? Appearance.getColorScheme() ?? 'dark';
    void NavigationBar.setBackgroundColorAsync(resolved === 'dark' ? '#000000' : '#FFFFFF').catch(
      () => undefined,
    );
    void NavigationBar.setButtonStyleAsync(resolved === 'dark' ? 'light' : 'dark').catch(
      () => undefined,
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
      <Animated.View
        key={store.screen}
        entering={
          store.screen === 'chat'
            ? FadeIn.duration(220)
            : SlideInRight.springify().damping(22).stiffness(190)
        }
        exiting={FadeOut.duration(120)}
        style={{ flex: 1 }}
      >
        {screen}
      </Animated.View>
      <ConnectionEditor />
    </>
  );
}
