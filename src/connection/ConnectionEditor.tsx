import { X } from 'lucide-react-native';
import { useEffect, useRef } from 'react';
import {
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import Animated, {
  FadeIn,
  FadeOut,
  SlideInDown,
  SlideOutDown,
} from 'react-native-reanimated';

import { Symbol } from '@/components/Symbol';
import { useConnection } from './store';
import { colors, radius, spacing } from '@/theme/semantic';

export function ConnectionEditor() {
  const store = useConnection();
  const inputRef = useRef<TextInput>(null);

  useEffect(() => {
    if (!store.editorVisible) return;
    const timer = setTimeout(() => inputRef.current?.focus(), 420);
    return () => clearTimeout(timer);
  }, [store.editorVisible, store.selectedPointId]);

  return (
    <Modal
      visible={store.editorVisible}
      animationType="none"
      presentationStyle={Platform.OS === 'ios' ? 'pageSheet' : 'overFullScreen'}
      transparent={Platform.OS !== 'ios'}
      onRequestClose={() => store.setEditorVisible(false)}
    >
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.modalRoot}
      >
        {Platform.OS !== 'ios' && (
          <Animated.View entering={FadeIn} exiting={FadeOut} style={styles.scrim} />
        )}
        <Animated.View
          entering={SlideInDown.springify().damping(19).stiffness(180)}
          exiting={SlideOutDown.duration(220)}
          style={styles.sheet}
        >
          <View style={styles.grabber} />
          <View style={styles.navigation}>
            <Text style={styles.navigationTitle}>Connection</Text>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Close"
              hitSlop={10}
              onPress={() => store.setEditorVisible(false)}
              style={({ pressed }) => [styles.closeButton, pressed && styles.pressed]}
            >
              <X size={18} color={colors.secondaryLabel} strokeWidth={2.2} />
            </Pressable>
          </View>

          <ScrollView
            keyboardDismissMode="interactive"
            keyboardShouldPersistTaps="handled"
            contentContainerStyle={styles.content}
          >
            <Text style={styles.sectionLabel}>Connection point</Text>
            <ScrollView
              horizontal
              showsHorizontalScrollIndicator={false}
              contentContainerStyle={styles.pointPicker}
            >
              {store.points.map((point) => {
                const selected = point.id === store.selectedPointId;
                return (
                  <Pressable
                    key={point.id}
                    testID={`editor-point-${point.id}`}
                    accessibilityRole="button"
                    accessibilityState={{ selected }}
                    onPress={() => store.selectPoint(point.id)}
                    style={({ pressed }) => [
                      styles.pointChip,
                      selected && styles.pointChipSelected,
                      pressed && styles.pressed,
                    ]}
                  >
                    <Symbol
                      name={point.symbol}
                      size={16}
                      color={selected ? colors.label : colors.secondaryLabel}
                      strokeWidth={2}
                    />
                    <Text
                      numberOfLines={1}
                      style={[styles.pointChipText, selected && styles.pointChipTextSelected]}
                    >
                      {point.name}
                    </Text>
                  </Pressable>
                );
              })}
            </ScrollView>

            <View style={styles.formGroup}>
              <Text style={styles.fieldLabel}>API key</Text>
              <TextInput
                ref={inputRef}
                testID="api-key-input"
                value={store.selectedKey}
                onChangeText={store.setKey}
                placeholder={store.selectedPoint.keyHint}
                placeholderTextColor={colors.tertiaryLabel}
                autoCapitalize="none"
                autoCorrect={false}
                secureTextEntry
                style={styles.input}
              />
              <Text style={styles.footnote}>Stored in memory for this session only.</Text>
            </View>

            {store.selectedPoint.id === 'custom' && (
              <Animated.View entering={FadeIn.duration(180)} style={styles.formGroup}>
                <Text style={styles.fieldLabel}>Base URL</Text>
                <TextInput
                  testID="base-url-input"
                  value={store.customBaseUrl}
                  onChangeText={store.setCustomBaseUrl}
                  placeholder="https://api.example.com"
                  placeholderTextColor={colors.tertiaryLabel}
                  autoCapitalize="none"
                  autoCorrect={false}
                  keyboardType="url"
                  style={styles.input}
                />
              </Animated.View>
            )}

            {store.message && (
              <Animated.Text entering={FadeIn} style={styles.errorText}>
                {store.message}
              </Animated.Text>
            )}

            <Pressable
              testID="editor-connect-button"
              accessibilityRole="button"
              disabled={store.phase === 'connecting'}
              onPress={() => void store.connect()}
              style={({ pressed }) => [
                styles.connectButton,
                pressed && styles.connectPressed,
                store.phase === 'connecting' && styles.disabled,
              ]}
            >
              <Text style={styles.connectButtonText}>
                {store.phase === 'connecting' ? 'Connecting…' : 'Connect'}
              </Text>
            </Pressable>
          </ScrollView>
        </Animated.View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  modalRoot: { flex: 1, justifyContent: 'flex-end' },
  scrim: { ...StyleSheet.absoluteFillObject, backgroundColor: '#00000080' },
  sheet: {
    maxHeight: '94%',
    minHeight: 560,
    borderTopLeftRadius: radius.sheet,
    borderTopRightRadius: radius.sheet,
    backgroundColor: colors.background,
    overflow: 'hidden',
  },
  grabber: {
    alignSelf: 'center',
    width: 36,
    height: 5,
    marginTop: spacing.xs,
    borderRadius: 3,
    backgroundColor: colors.fill,
  },
  navigation: {
    height: 54,
    paddingHorizontal: spacing.md,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  navigationTitle: { color: colors.label, fontSize: 17, fontWeight: '600' },
  closeButton: {
    width: 30,
    height: 30,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 15,
    backgroundColor: colors.fill,
  },
  content: { padding: spacing.lg, paddingBottom: spacing.xxl },
  sectionLabel: {
    marginBottom: spacing.sm,
    color: colors.secondaryLabel,
    fontSize: 13,
    fontWeight: '600',
  },
  pointPicker: { gap: spacing.xs, paddingRight: spacing.lg },
  pointChip: {
    height: 40,
    maxWidth: 150,
    paddingHorizontal: spacing.sm,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    borderRadius: 20,
    backgroundColor: colors.secondaryBackground,
  },
  pointChipSelected: { backgroundColor: colors.fill },
  pointChipText: { color: colors.secondaryLabel, fontSize: 14, fontWeight: '500' },
  pointChipTextSelected: { color: colors.label },
  formGroup: { marginTop: spacing.lg },
  fieldLabel: { marginBottom: spacing.xs, color: colors.label, fontSize: 15, fontWeight: '500' },
  input: {
    height: 50,
    paddingHorizontal: spacing.md,
    borderRadius: radius.control,
    color: colors.label,
    backgroundColor: colors.secondaryBackground,
    fontSize: 16,
  },
  footnote: { marginTop: 7, color: colors.tertiaryLabel, fontSize: 12 },
  errorText: { marginTop: spacing.md, color: colors.destructive, fontSize: 13 },
  connectButton: {
    height: 50,
    marginTop: spacing.xl,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.control,
    backgroundColor: colors.accent,
  },
  connectPressed: { transform: [{ scale: 0.985 }], opacity: 0.9 },
  connectButtonText: { color: '#FFFFFF', fontSize: 17, fontWeight: '600' },
  pressed: { opacity: 0.58 },
  disabled: { opacity: 0.55 },
});
