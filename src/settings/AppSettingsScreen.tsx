import { ArrowLeft, Check } from 'lucide-react-native';
import {
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { KeyboardAvoidingView } from 'react-native-keyboard-controller';

import { useConnection } from '@/connection/store';
import { colors, radius, spacing } from '@/theme/semantic';
import type { AppSettings } from '@/connection/types';

const schemes: AppSettings['colorScheme'][] = ['system', 'light', 'dark'];
const scales: AppSettings['textScale'][] = ['compact', 'default', 'large'];

export function AppSettingsScreen() {
  const store = useConnection();
  const insets = useSafeAreaInsets();

  return (
    <View style={[styles.root, { paddingTop: insets.top, paddingBottom: insets.bottom }]}>
      <View style={styles.navigation}>
        <Pressable
          onPress={() =>
            store.setScreen(store.selectedModel || store.activeThread ? 'chat' : 'connect')
          }
          style={({ pressed }) => [styles.navigationButton, pressed && styles.pressed]}
        >
          <ArrowLeft size={21} color={colors.label} />
        </Pressable>
        <Text style={styles.navigationTitle}>Settings</Text>
        <View style={styles.navigationButton} />
      </View>

      <KeyboardAvoidingView behavior="padding" automaticOffset style={styles.flex}>
      <ScrollView keyboardDismissMode="interactive" contentContainerStyle={styles.content}>
        <Text style={styles.hero}>Make it yours.</Text>

        <Text style={styles.sectionLabel}>Appearance</Text>
        <View style={styles.segmented}>
          {schemes.map((scheme) => (
            <Pressable
              key={scheme}
              onPress={() => store.updateSettings({ colorScheme: scheme })}
              style={[
                styles.segment,
                store.settings.colorScheme === scheme && styles.segmentSelected,
              ]}
            >
              <Text style={styles.segmentText}>{scheme[0]!.toUpperCase() + scheme.slice(1)}</Text>
              {store.settings.colorScheme === scheme && <Check size={14} color={colors.accent} />}
            </Pressable>
          ))}
        </View>

        <Text style={styles.sectionLabel}>Text size</Text>
        <View style={styles.segmented}>
          {scales.map((scale) => (
            <Pressable
              key={scale}
              onPress={() => store.updateSettings({ textScale: scale })}
              style={[
                styles.segment,
                store.settings.textScale === scale && styles.segmentSelected,
              ]}
            >
              <Text style={styles.segmentText}>{scale[0]!.toUpperCase() + scale.slice(1)}</Text>
            </Pressable>
          ))}
        </View>

        <View style={styles.toggleRow}>
          <View style={styles.toggleCopy}>
            <Text style={styles.toggleTitle}>Haptic feedback</Text>
            <Text style={styles.toggleDetail}>Subtle feedback for actions and status.</Text>
          </View>
          <Switch
            value={store.settings.haptics}
            onValueChange={(haptics) => store.updateSettings({ haptics })}
          />
        </View>

        <Text style={styles.sectionLabel}>System prompt</Text>
        <TextInput
          value={store.settings.systemPrompt}
          onChangeText={(systemPrompt) => store.updateSettings({ systemPrompt })}
          placeholder="How should the assistant behave?"
          placeholderTextColor={colors.tertiaryLabel}
          multiline
          textAlignVertical="top"
          style={styles.prompt}
        />

        <Text style={styles.footnote}>
          Chat history and custom providers are stored locally. API keys are never persisted.
        </Text>
      </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.background },
  flex: { flex: 1 },
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
  sectionLabel: {
    marginTop: spacing.xl,
    marginBottom: spacing.xs,
    color: colors.secondaryLabel,
    fontSize: 13,
    fontWeight: '600',
  },
  segmented: { borderTopWidth: StyleSheet.hairlineWidth, borderColor: colors.separator },
  segment: {
    height: 50,
    paddingHorizontal: spacing.sm,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderColor: colors.separator,
  },
  segmentSelected: { backgroundColor: colors.secondaryBackground },
  segmentText: { color: colors.label, fontSize: 15 },
  toggleRow: {
    minHeight: 66,
    marginTop: spacing.xl,
    flexDirection: 'row',
    alignItems: 'center',
    borderTopWidth: StyleSheet.hairlineWidth,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderColor: colors.separator,
  },
  toggleCopy: { flex: 1, paddingRight: spacing.md },
  toggleTitle: { color: colors.label, fontSize: 15, fontWeight: '500' },
  toggleDetail: { marginTop: 2, color: colors.tertiaryLabel, fontSize: 11 },
  prompt: {
    minHeight: 130,
    padding: spacing.md,
    borderRadius: radius.control,
    color: colors.label,
    backgroundColor: colors.secondaryBackground,
    fontSize: 15,
    lineHeight: 21,
  },
  footnote: { marginTop: spacing.xl, color: colors.tertiaryLabel, fontSize: 12, lineHeight: 18 },
  pressed: { opacity: 0.5 },
});
