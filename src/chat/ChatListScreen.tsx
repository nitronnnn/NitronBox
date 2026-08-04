import { ArrowLeft, MessageSquarePlus, Trash2 } from 'lucide-react-native';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useConnection } from '@/connection/store';
import { colors, radius, spacing } from '@/theme/semantic';

export function ChatListScreen() {
  const store = useConnection();
  const insets = useSafeAreaInsets();

  return (
    <View style={[styles.root, { paddingTop: insets.top, paddingBottom: insets.bottom }]}>
      <View style={styles.navigation}>
        <Pressable
          onPress={() => store.setScreen('chat')}
          style={({ pressed }) => [styles.navigationButton, pressed && styles.pressed]}
        >
          <ArrowLeft size={21} color={colors.label} />
        </Pressable>
        <Text style={styles.navigationTitle}>Chats</Text>
        <Pressable
          testID="new-chat-button"
          onPress={store.createThread}
          style={({ pressed }) => [styles.navigationButton, pressed && styles.pressed]}
        >
          <MessageSquarePlus size={21} color={colors.accent} />
        </Pressable>
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.hero}>Your conversations.</Text>
        <Text style={styles.subtitle}>Stored locally on this device.</Text>

        <View style={styles.list}>
          {store.threads.length === 0 ? (
            <Text style={styles.empty}>No chats yet.</Text>
          ) : (
            store.threads.map((thread, index) => (
              <View key={thread.id}>
                <Pressable
                  onPress={() => store.openThread(thread.id)}
                  style={({ pressed }) => [styles.row, pressed && styles.pressed]}
                >
                  <View style={styles.rowCopy}>
                    <Text numberOfLines={1} style={styles.rowTitle}>
                      {thread.title}
                    </Text>
                    <Text style={styles.rowDetail}>
                      {thread.messages.length} messages ·{' '}
                      {new Date(thread.updatedAt).toLocaleDateString()}
                    </Text>
                  </View>
                  <Pressable
                    hitSlop={10}
                    onPress={() => store.deleteThread(thread.id)}
                    style={({ pressed }) => pressed && styles.pressed}
                  >
                    <Trash2 size={18} color={colors.tertiaryLabel} />
                  </Pressable>
                </Pressable>
                {index < store.threads.length - 1 && <View style={styles.separator} />}
              </View>
            ))
          )}
        </View>
      </ScrollView>
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
    justifyContent: 'space-between',
  },
  navigationButton: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  navigationTitle: { color: colors.label, fontSize: 17, fontWeight: '600' },
  content: { paddingHorizontal: spacing.lg, paddingBottom: spacing.xxl },
  hero: {
    marginTop: spacing.lg,
    color: colors.label,
    fontSize: 36,
    lineHeight: 41,
    fontWeight: '700',
    letterSpacing: -1.3,
  },
  subtitle: { marginTop: spacing.xs, color: colors.secondaryLabel, fontSize: 14 },
  list: { marginTop: spacing.xl, borderTopWidth: StyleSheet.hairlineWidth, borderColor: colors.separator },
  row: { minHeight: 68, flexDirection: 'row', alignItems: 'center' },
  rowCopy: { flex: 1, paddingRight: spacing.md },
  rowTitle: { color: colors.label, fontSize: 15, fontWeight: '500' },
  rowDetail: { marginTop: 3, color: colors.tertiaryLabel, fontSize: 11 },
  separator: { height: StyleSheet.hairlineWidth, backgroundColor: colors.separator },
  empty: { paddingVertical: spacing.xl, color: colors.tertiaryLabel, fontSize: 14 },
  pressed: { opacity: 0.5 },
});
