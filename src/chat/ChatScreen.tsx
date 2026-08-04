import { ArrowUp, Check, Copy, Menu, Paperclip, Settings2, X } from 'lucide-react-native';
import * as Clipboard from 'expo-clipboard';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  FlatList,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import Markdown from 'react-native-markdown-display';
import Animated, { FadeInDown, FadeOut, LinearTransition } from 'react-native-reanimated';
import { KeyboardAvoidingView } from 'react-native-keyboard-controller';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Symbol } from '@/components/Symbol';
import { colors, radius, spacing } from '@/theme/semantic';
import { useConnection } from '@/connection/store';
import type { ChatMessage } from '@/connection/types';

const textSizes = { compact: 14, default: 15, large: 17 } as const;

export function ChatScreen() {
  const store = useConnection();
  const insets = useSafeAreaInsets();
  const [draft, setDraft] = useState('');
  const listRef = useRef<FlatList<ChatMessage>>(null);
  const data = useMemo(() => store.chatMessages, [store.chatMessages]);

  const send = () => {
    const content = draft.trim();
    if ((!content && store.pendingAttachments.length === 0) || store.sending) return;
    setDraft('');
    void store.sendMessage(content);
  };

  return (
    <KeyboardAvoidingView
      behavior="translate-with-padding"
      automaticOffset
      style={[styles.root, { paddingTop: insets.top }]}
    >
      <View style={styles.navigation}>
        <Pressable
          testID="chat-menu-button"
          onPress={() => store.setScreen('chats')}
          style={({ pressed }) => [styles.navigationButton, pressed && styles.pressed]}
        >
          <Menu size={21} color={colors.label} />
        </Pressable>
        <View style={styles.titleGroup}>
          <Text numberOfLines={1} style={styles.title}>
            {store.activeThread?.title ?? 'New chat'}
          </Text>
          <Text numberOfLines={1} style={styles.model}>
            {store.selectedModel}
          </Text>
        </View>
        <Pressable
          onPress={() => store.setScreen('appSettings')}
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
          contentContainerStyle={[styles.messages, { paddingBottom: 130 + insets.bottom }]}
          keyboardDismissMode="interactive"
          keyboardShouldPersistTaps="handled"
          onContentSizeChange={() => listRef.current?.scrollToEnd({ animated: true })}
          renderItem={({ item, index }) => (
            <Animated.View
              entering={FadeInDown.delay(Math.min(index * 18, 120)).springify().damping(20)}
              exiting={FadeOut.duration(120)}
              layout={LinearTransition.springify().damping(20)}
            >
              <MessageBubble message={item} sending={store.sending} />
            </Animated.View>
          )}
        />
      )}

      {store.message && <Text style={styles.error}>{store.message}</Text>}

      <View style={[styles.composerWrap, { paddingBottom: Math.max(insets.bottom, spacing.xs) }]}>
        {store.pendingAttachments.length > 0 && (
          <FlatList
            horizontal
            data={store.pendingAttachments}
            keyExtractor={(file) => file.id}
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={styles.attachmentList}
            renderItem={({ item }) => (
              <View style={styles.attachmentChip}>
                <Text numberOfLines={1} style={styles.attachmentName}>
                  {item.name}
                </Text>
                <Pressable onPress={() => store.removeAttachment(item.id)} hitSlop={8}>
                  <X size={14} color={colors.secondaryLabel} />
                </Pressable>
              </View>
            )}
          />
        )}
        <View style={styles.composer}>
          <Pressable
            testID="attach-file-button"
            onPress={() => void store.pickAttachments()}
            style={({ pressed }) => [styles.attach, pressed && styles.pressed]}
          >
            <Paperclip size={20} color={colors.secondaryLabel} />
          </Pressable>
          <TextInput
            testID="chat-input"
            value={draft}
            onChangeText={setDraft}
            placeholder="Message"
            placeholderTextColor={colors.tertiaryLabel}
            multiline
            maxLength={12000}
            scrollEnabled
            textAlignVertical="top"
            style={styles.input}
          />
          <Pressable
            testID="chat-send-button"
            disabled={(!draft.trim() && store.pendingAttachments.length === 0) || store.sending}
            onPress={send}
            style={({ pressed }) => [
              styles.send,
              pressed && styles.sendPressed,
              ((!draft.trim() && store.pendingAttachments.length === 0) || store.sending) &&
                styles.disabled,
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
  const store = useConnection();
  const user = message.role === 'user';
  const [copied, setCopied] = useState(false);
  const fontSize = textSizes[store.settings.textScale];
  const copy = async () => {
    if (!message.content) return;
    await Clipboard.setStringAsync(message.content);
    setCopied(true);
    if (store.settings.haptics) {
      await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    }
    setTimeout(() => setCopied(false), 1200);
  };
  return (
    <View style={[styles.messageRow, user && styles.userRow]}>
      {!user && <View style={styles.assistantMark} />}
      <Pressable
        onLongPress={() => void copy()}
        delayLongPress={350}
        style={[styles.bubble, user && styles.userBubble]}
      >
        {message.attachments.map((file) => (
          <View key={file.id} style={styles.messageAttachment}>
            <Paperclip size={13} color={user ? '#FFFFFFCC' : colors.secondaryLabel} />
            <Text numberOfLines={1} style={[styles.messageAttachmentText, user && styles.userText]}>
              {file.name}
            </Text>
          </View>
        ))}
        {user ? (
          <Text style={[styles.messageText, styles.userText, { fontSize }]}>
            {message.content}
          </Text>
        ) : message.content ? (
          <Markdown style={markdownStyles}>{message.content}</Markdown>
        ) : (
          <Text style={[styles.messageText, { fontSize }]}>{sending ? 'Thinking…' : ''}</Text>
        )}
        {!user && message.content ? (
          <Pressable
            testID={`copy-message-${message.id}`}
            accessibilityLabel="Copy response"
            onPress={() => void copy()}
            style={({ pressed }) => [styles.copyButton, pressed && styles.pressed]}
          >
            {copied ? (
              <Check size={14} color={colors.success} />
            ) : (
              <Copy size={14} color={colors.tertiaryLabel} />
            )}
            <Text style={[styles.copyText, copied && { color: colors.success }]}>
              {copied ? 'Copied' : 'Copy'}
            </Text>
          </Pressable>
        ) : null}
      </Pressable>
    </View>
  );
}

const markdownStyles = StyleSheet.create({
  body: { color: colors.label, fontSize: 15, lineHeight: 22 },
  paragraph: { marginTop: 0, marginBottom: 9 },
  heading1: { color: colors.label, fontSize: 24, lineHeight: 29, fontWeight: '700' },
  heading2: { color: colors.label, fontSize: 20, lineHeight: 25, fontWeight: '700' },
  heading3: { color: colors.label, fontSize: 17, lineHeight: 22, fontWeight: '600' },
  code_inline: {
    color: colors.label,
    backgroundColor: colors.secondaryBackground,
    borderRadius: 5,
    paddingHorizontal: 5,
  },
  fence: {
    color: colors.label,
    backgroundColor: colors.secondaryBackground,
    borderColor: colors.separator,
    borderRadius: radius.control,
    padding: spacing.sm,
  },
  blockquote: {
    backgroundColor: colors.secondaryBackground,
    borderLeftColor: colors.accent,
    borderLeftWidth: 3,
    paddingHorizontal: spacing.sm,
  },
  link: { color: colors.accent },
  bullet_list: { marginVertical: 5 },
  ordered_list: { marginVertical: 5 },
});

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
  title: { maxWidth: 220, color: colors.label, fontSize: 16, fontWeight: '600' },
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
  bubble: { maxWidth: '90%', paddingHorizontal: spacing.sm, paddingVertical: 9 },
  userBubble: { borderRadius: 18, backgroundColor: colors.accent },
  messageText: { color: colors.label, lineHeight: 22 },
  userText: { color: '#FFFFFF' },
  messageAttachment: { flexDirection: 'row', alignItems: 'center', gap: 5, marginBottom: 6 },
  messageAttachmentText: { maxWidth: 230, color: colors.secondaryLabel, fontSize: 11 },
  copyButton: {
    alignSelf: 'flex-start',
    height: 30,
    marginTop: 4,
    paddingHorizontal: 4,
    flexDirection: 'row',
    alignItems: 'center',
  },
  copyText: { color: colors.tertiaryLabel, fontSize: 11 },
  error: { paddingHorizontal: spacing.md, paddingVertical: spacing.xs, color: colors.destructive, fontSize: 12 },
  composerWrap: {
    paddingHorizontal: spacing.sm,
    paddingTop: spacing.xs,
    backgroundColor: colors.background,
  },
  attachmentList: { gap: spacing.xs, paddingBottom: spacing.xs },
  attachmentChip: {
    maxWidth: 220,
    height: 32,
    paddingHorizontal: spacing.sm,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs,
    borderRadius: radius.pill,
    backgroundColor: colors.secondaryBackground,
  },
  attachmentName: { flexShrink: 1, color: colors.label, fontSize: 11 },
  composer: {
    minHeight: 52,
    paddingHorizontal: 5,
    flexDirection: 'row',
    alignItems: 'flex-end',
    borderRadius: 18,
    backgroundColor: colors.secondaryBackground,
  },
  attach: { width: 42, height: 48, alignItems: 'center', justifyContent: 'center' },
  input: {
    flex: 1,
    minHeight: 48,
    maxHeight: 128,
    paddingTop: 13,
    paddingBottom: 11,
    color: colors.label,
    fontSize: 16,
  },
  send: {
    width: 40,
    height: 40,
    marginBottom: 6,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.control,
    backgroundColor: colors.accent,
  },
  sendPressed: { transform: [{ scale: 0.94 }] },
  disabled: { opacity: 0.35 },
  pressed: { opacity: 0.5 },
});
