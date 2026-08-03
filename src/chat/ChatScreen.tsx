import { ArrowLeft, ArrowUp, Settings2 } from 'lucide-react-native';
import { useMemo, useRef, useState } from 'react';
import {
  FlatList,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Symbol } from '@/components/Symbol';
import { colors, radius, spacing } from '@/theme/semantic';
import { useConnection } from '@/connection/store';
import type { ChatMessage } from '@/connection/types';

export function ChatScreen() {
  const store = useConnection();
  const insets = useSafeAreaInsets();
  const [draft, setDraft] = useState('');
  const listRef = useRef<FlatList<ChatMessage>>(null);
  const data = useMemo(() => store.chatMessages, [store.chatMessages]);

  const send = () => {
    const content = draft.trim();
    if (!content || store.sending) return;
    setDraft('');
    void store.sendMessage(content);
  };

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      style={[styles.root, { paddingTop: insets.top }]}
    >
      <View style={styles.navigation}>
        <Pressable
          onPress={() => store.disconnect()}
          style={({ pressed }) => [styles.navigationButton, pressed && styles.pressed]}
        >
          <ArrowLeft size={21} color={colors.label} />
        </Pressable>
        <View style={styles.titleGroup}>
          <Text style={styles.title}>NitronBox</Text>
          <Text numberOfLines={1} style={styles.model}>
            {store.selectedModel}
          </Text>
        </View>
        <Pressable
          onPress={() => store.setScreen('settings')}
          style={({ pressed }) => [styles.navigationButton, pressed && styles.pressed]}
        >
          <Settings2 size={20} color={colors.label} />
        </Pressable>
      </View>

      {data.length === 0 ? (
        <View style={styles.empty}>
          <View style={styles.emptySymbol}>
            <Symbol name={store.selectedPoint.symbol} size={24} color={colors.label} />
          </View>
          <Text style={styles.emptyTitle}>How can I help?</Text>
          <Text style={styles.emptySubtitle}>Connected to {store.selectedPoint.name}</Text>
        </View>
      ) : (
        <FlatList
          ref={listRef}
          data={data}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.messages}
          keyboardDismissMode="interactive"
          onContentSizeChange={() => listRef.current?.scrollToEnd({ animated: true })}
          renderItem={({ item }) => <MessageBubble message={item} sending={store.sending} />}
        />
      )}

      {store.message && <Text style={styles.error}>{store.message}</Text>}

      <View style={[styles.composerWrap, { paddingBottom: Math.max(insets.bottom, spacing.xs) }]}>
        <View style={styles.composer}>
          <TextInput
            testID="chat-input"
            value={draft}
            onChangeText={setDraft}
            placeholder="Message"
            placeholderTextColor={colors.tertiaryLabel}
            multiline
            maxLength={12000}
            style={styles.input}
          />
          <Pressable
            testID="chat-send-button"
            disabled={!draft.trim() || store.sending}
            onPress={send}
            style={({ pressed }) => [
              styles.send,
              pressed && styles.sendPressed,
              (!draft.trim() || store.sending) && styles.disabled,
            ]}
          >
            <ArrowUp size={19} color="#FFFFFF" />
          </Pressable>
        </View>
      </View>
    </KeyboardAvoidingView>
  );
}

function MessageBubble({ message, sending }: { message: ChatMessage; sending: boolean }) {
  const user = message.role === 'user';
  return (
    <View style={[styles.messageRow, user && styles.userRow]}>
      {!user && <View style={styles.assistantMark} />}
      <View style={[styles.bubble, user && styles.userBubble]}>
        <Text style={[styles.messageText, user && styles.userText]}>
          {message.content || (sending ? 'Thinking…' : '')}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.background },
  navigation: {
    height: 54,
    paddingHorizontal: spacing.sm,
    flexDirection: 'row',
    alignItems: 'center',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderColor: colors.separator,
  },
  navigationButton: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  titleGroup: { flex: 1, alignItems: 'center' },
  title: { color: colors.label, fontSize: 16, fontWeight: '600' },
  model: { maxWidth: 220, color: colors.tertiaryLabel, fontSize: 10 },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingBottom: 70 },
  emptySymbol: {
    width: 54,
    height: 54,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 18,
    backgroundColor: colors.secondaryBackground,
  },
  emptyTitle: { marginTop: spacing.md, color: colors.label, fontSize: 28, fontWeight: '700' },
  emptySubtitle: { marginTop: spacing.xs, color: colors.secondaryLabel, fontSize: 13 },
  messages: { padding: spacing.md, gap: spacing.md },
  messageRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing.xs },
  userRow: { justifyContent: 'flex-end' },
  assistantMark: { width: 7, height: 7, marginTop: 10, borderRadius: 4, backgroundColor: colors.accent },
  bubble: { maxWidth: '88%', paddingHorizontal: spacing.sm, paddingVertical: 9 },
  userBubble: { borderRadius: 18, backgroundColor: colors.accent },
  messageText: { color: colors.label, fontSize: 15, lineHeight: 22 },
  userText: { color: '#FFFFFF' },
  error: { paddingHorizontal: spacing.md, paddingVertical: spacing.xs, color: colors.destructive, fontSize: 12 },
  composerWrap: { paddingHorizontal: spacing.sm, paddingTop: spacing.xs },
  composer: {
    minHeight: 50,
    paddingLeft: spacing.md,
    paddingRight: 5,
    flexDirection: 'row',
    alignItems: 'flex-end',
    borderRadius: 18,
    backgroundColor: colors.secondaryBackground,
  },
  input: {
    flex: 1,
    minHeight: 48,
    maxHeight: 130,
    paddingTop: 13,
    paddingBottom: 11,
    color: colors.label,
    fontSize: 16,
  },
  send: {
    width: 40,
    height: 40,
    marginBottom: 5,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.control,
    backgroundColor: colors.accent,
  },
  sendPressed: { transform: [{ scale: 0.94 }] },
  disabled: { opacity: 0.35 },
  pressed: { opacity: 0.5 },
});
