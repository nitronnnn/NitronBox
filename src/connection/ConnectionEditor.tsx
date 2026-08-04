import {
  ArrowLeft,
  Check,
  ChevronDown,
  Eye,
  EyeOff,
  Plus,
  Trash2,
} from 'lucide-react-native';
import { useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import Animated, { FadeIn, FadeOut } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Symbol } from '@/components/Symbol';
import { colors, radius, spacing } from '@/theme/semantic';
import { useConnection } from './store';

export function ConnectionEditor() {
  const store = useConnection();
  const insets = useSafeAreaInsets();
  const [showModels, setShowModels] = useState(false);
  const [keyVisible, setKeyVisible] = useState(false);
  const [addingCustom, setAddingCustom] = useState(false);
  const [customName, setCustomName] = useState('');
  const [customUrl, setCustomUrl] = useState('');

  if (store.screen !== 'settings') return null;

  return (
    <Animated.View
      entering={FadeIn.duration(180)}
      exiting={FadeOut.duration(140)}
      style={[styles.root, { paddingTop: insets.top, paddingBottom: insets.bottom }]}
    >
      <View style={styles.navigation}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Back"
          onPress={() => store.setScreen(store.phase === 'connected' ? 'chat' : 'connect')}
          style={({ pressed }) => [styles.navigationButton, pressed && styles.pressed]}
        >
          <ArrowLeft size={21} color={colors.label} />
        </Pressable>
        <Text style={styles.navigationTitle}>Connection</Text>
        <View style={styles.navigationButton} />
      </View>

      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        style={styles.flex}
      >
        <ScrollView
          keyboardDismissMode="interactive"
          keyboardShouldPersistTaps="handled"
          contentContainerStyle={styles.content}
        >
          <Text style={styles.hero}>Connect your model provider.</Text>
          <Text style={styles.subtitle}>Credentials stay in memory until the app closes.</Text>

          <Text style={styles.sectionLabel}>Provider</Text>
          <View style={styles.providerList}>
            {store.points.map((point, index) => {
              const selected = point.id === store.selectedPointId;
              return (
                <View key={point.id}>
                  <Pressable
                    testID={`editor-point-${point.id}`}
                    onPress={() => store.selectPoint(point.id)}
                    style={({ pressed }) => [styles.providerRow, pressed && styles.pressed]}
                  >
                    <View style={styles.symbolFrame}>
                      <Symbol name={point.symbol} size={18} color={colors.label} />
                    </View>
                    <View style={styles.providerCopy}>
                      <Text style={styles.providerName}>{point.name}</Text>
                      <Text style={styles.providerDetail}>{point.detail}</Text>
                    </View>
                    {point.custom && point.id !== 'custom' && (
                      <Pressable
                        hitSlop={10}
                        onPress={() => store.deleteCustomProvider(point.id)}
                        style={({ pressed }) => pressed && styles.pressed}
                      >
                        <Trash2 size={17} color={colors.tertiaryLabel} />
                      </Pressable>
                    )}
                    {selected && <Check size={19} color={colors.accent} />}
                  </Pressable>
                  {index < store.points.length - 1 && <View style={styles.separator} />}
                </View>
              );
            })}
          </View>

          {addingCustom ? (
            <View style={styles.customForm}>
              <TextInput
                testID="custom-provider-name"
                value={customName}
                onChangeText={setCustomName}
                placeholder="Provider name"
                placeholderTextColor={colors.tertiaryLabel}
                style={styles.input}
              />
              <TextInput
                testID="custom-provider-url"
                value={customUrl}
                onChangeText={setCustomUrl}
                placeholder="https://api.example.com"
                placeholderTextColor={colors.tertiaryLabel}
                autoCapitalize="none"
                keyboardType="url"
                style={[styles.input, styles.secondaryInput]}
              />
              <View style={styles.customActions}>
                <Pressable
                  accessibilityLabel="Cancel custom provider"
                  onPress={() => setAddingCustom(false)}
                  style={({ pressed }) => [styles.secondaryAction, pressed && styles.pressed]}
                >
                  <Text style={styles.secondaryActionText}>Cancel</Text>
                </Pressable>
                <Pressable
                  testID="save-custom-provider"
                  accessibilityLabel="Save custom provider"
                  disabled={!customName.trim() || !customUrl.trim()}
                  onPress={() => {
                    store.createCustomProvider(customName, customUrl);
                    setCustomName('');
                    setCustomUrl('');
                    setAddingCustom(false);
                  }}
                  style={({ pressed }) => [
                    styles.customSave,
                    pressed && styles.primaryPressed,
                    (!customName.trim() || !customUrl.trim()) && styles.disabled,
                  ]}
                >
                  <Text style={styles.customSaveText}>Add provider</Text>
                </Pressable>
              </View>
            </View>
          ) : (
            <Pressable
              testID="add-custom-provider"
              onPress={() => setAddingCustom(true)}
              style={({ pressed }) => [styles.addProvider, pressed && styles.pressed]}
            >
              <Plus size={17} color={colors.accent} />
              <Text style={styles.addProviderText}>Add custom provider</Text>
            </Pressable>
          )}

          <Text style={styles.sectionLabel}>Credentials</Text>
          <View style={styles.keyField}>
            <TextInput
              key={store.selectedPointId}
              testID="api-key-input"
              value={store.selectedKey}
              onChangeText={store.setKey}
              placeholder={store.selectedPoint.keyHint}
              placeholderTextColor={colors.tertiaryLabel}
              autoCapitalize="none"
              autoCorrect={false}
              secureTextEntry={!keyVisible}
              style={styles.keyInput}
            />
            <Pressable
              testID="toggle-api-key"
              accessibilityLabel={keyVisible ? 'Hide API key' : 'Show API key'}
              onPress={() => setKeyVisible((value) => !value)}
              style={({ pressed }) => [styles.eyeButton, pressed && styles.pressed]}
            >
              {keyVisible ? (
                <EyeOff size={19} color={colors.secondaryLabel} />
              ) : (
                <Eye size={19} color={colors.secondaryLabel} />
              )}
            </Pressable>
          </View>
          {store.selectedPoint.id === 'custom' && (
            <TextInput
              testID="base-url-input"
              value={store.customBaseUrl}
              onChangeText={store.setCustomBaseUrl}
              placeholder="https://api.example.com"
              placeholderTextColor={colors.tertiaryLabel}
              autoCapitalize="none"
              autoCorrect={false}
              keyboardType="url"
              style={[styles.input, styles.secondaryInput]}
            />
          )}

          {store.models.length > 0 && (
            <>
              <Text style={styles.sectionLabel}>Model</Text>
              <Pressable
                onPress={() => setShowModels((value) => !value)}
                style={({ pressed }) => [styles.modelControl, pressed && styles.pressed]}
              >
                <Text numberOfLines={1} style={styles.modelControlText}>
                  {store.selectedModel ?? 'Choose model'}
                </Text>
                <ChevronDown size={18} color={colors.secondaryLabel} />
              </Pressable>
              {showModels && (
                <View style={styles.modelList}>
                  {store.models.map((model) => (
                    <Pressable
                      key={model}
                      onPress={() => {
                        store.setModel(model);
                        setShowModels(false);
                      }}
                      style={({ pressed }) => [styles.modelRow, pressed && styles.pressed]}
                    >
                      <Text numberOfLines={1} style={styles.modelRowText}>
                        {model}
                      </Text>
                      {model === store.selectedModel && <Check size={18} color={colors.accent} />}
                    </Pressable>
                  ))}
                </View>
              )}
            </>
          )}

          {store.message && <Text style={styles.error}>{store.message}</Text>}

          <Pressable
            testID="editor-connect-button"
            disabled={store.phase === 'connecting'}
            onPress={() => void store.connect()}
            style={({ pressed }) => [
              styles.primary,
              pressed && styles.primaryPressed,
              store.phase === 'connecting' && styles.disabled,
            ]}
          >
            <Text style={styles.primaryText}>
              {store.phase === 'connecting' ? 'Connecting…' : 'Connect'}
            </Text>
          </Pressable>
        </ScrollView>
      </KeyboardAvoidingView>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  root: { ...StyleSheet.absoluteFillObject, zIndex: 20, backgroundColor: colors.background },
  flex: { flex: 1 },
  navigation: {
    height: 50,
    paddingHorizontal: spacing.md,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  navigationButton: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  navigationTitle: { color: colors.label, fontSize: 17, fontWeight: '600' },
  content: { paddingHorizontal: spacing.lg, paddingBottom: spacing.xxl },
  hero: {
    maxWidth: 340,
    marginTop: spacing.lg,
    color: colors.label,
    fontSize: 36,
    lineHeight: 41,
    fontWeight: '700',
    letterSpacing: -1.3,
  },
  subtitle: { marginTop: spacing.xs, color: colors.secondaryLabel, fontSize: 14, lineHeight: 20 },
  sectionLabel: {
    marginTop: spacing.xl,
    marginBottom: spacing.xs,
    color: colors.secondaryLabel,
    fontSize: 13,
    fontWeight: '600',
  },
  providerList: { borderTopWidth: StyleSheet.hairlineWidth, borderColor: colors.separator },
  providerRow: { height: 58, flexDirection: 'row', alignItems: 'center' },
  symbolFrame: {
    width: 32,
    height: 32,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 10,
    backgroundColor: colors.secondaryBackground,
  },
  providerCopy: { flex: 1, marginLeft: spacing.sm },
  providerName: { color: colors.label, fontSize: 15, fontWeight: '500' },
  providerDetail: { color: colors.tertiaryLabel, fontSize: 11 },
  separator: { height: StyleSheet.hairlineWidth, marginLeft: 44, backgroundColor: colors.separator },
  addProvider: {
    height: 44,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs,
  },
  addProviderText: { color: colors.accent, fontSize: 14, fontWeight: '500' },
  customForm: { marginTop: spacing.sm },
  customActions: { marginTop: spacing.xs, flexDirection: 'row', gap: spacing.xs },
  secondaryAction: {
    flex: 1,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.control,
    backgroundColor: colors.secondaryBackground,
  },
  secondaryActionText: { color: colors.label, fontSize: 14, fontWeight: '500' },
  customSave: {
    flex: 1,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.control,
    backgroundColor: colors.accent,
  },
  customSaveText: { color: '#FFFFFF', fontSize: 14, fontWeight: '600' },
  input: {
    height: 50,
    paddingHorizontal: spacing.md,
    borderRadius: radius.control,
    color: colors.label,
    backgroundColor: colors.secondaryBackground,
    fontSize: 16,
  },
  keyField: {
    height: 50,
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: radius.control,
    backgroundColor: colors.secondaryBackground,
  },
  keyInput: {
    flex: 1,
    height: 50,
    paddingLeft: spacing.md,
    color: colors.label,
    fontSize: 16,
  },
  eyeButton: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center' },
  secondaryInput: { marginTop: spacing.xs },
  modelControl: {
    height: 50,
    paddingHorizontal: spacing.md,
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: radius.control,
    backgroundColor: colors.secondaryBackground,
  },
  modelControlText: { flex: 1, color: colors.label, fontSize: 15 },
  modelList: {
    maxHeight: 260,
    marginTop: spacing.xs,
    paddingHorizontal: spacing.sm,
    borderRadius: radius.control,
    backgroundColor: colors.secondaryBackground,
  },
  modelRow: { minHeight: 46, flexDirection: 'row', alignItems: 'center' },
  modelRowText: { flex: 1, color: colors.label, fontSize: 13 },
  error: { marginTop: spacing.md, color: colors.destructive, fontSize: 13 },
  primary: {
    height: 50,
    marginTop: spacing.xl,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.control,
    backgroundColor: colors.accent,
  },
  primaryPressed: { transform: [{ scale: 0.985 }], opacity: 0.9 },
  primaryText: { color: '#FFFFFF', fontSize: 17, fontWeight: '600' },
  disabled: { opacity: 0.55 },
  pressed: { opacity: 0.5 },
});
