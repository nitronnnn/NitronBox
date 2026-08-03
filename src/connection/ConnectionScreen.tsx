import { ChevronRight, Power, Settings2 } from 'lucide-react-native';
import { useEffect } from 'react';
import {
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import Animated, {
  FadeIn,
  FadeInDown,
  FadeOut,
  LinearTransition,
  useAnimatedStyle,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { StatusIndicator } from '@/components/StatusIndicator';
import { Symbol } from '@/components/Symbol';
import { colors, radius, spacing } from '@/theme/semantic';
import { useConnection } from './store';

const phaseCopy = {
  idle: ['Not connected', 'Choose a connection point to begin.'],
  connecting: ['Connecting', 'Verifying credentials and availability.'],
  connected: ['Connected', 'NitronBox is ready.'],
  error: ['Connection failed', 'Review your details and try again.'],
} as const;

export function ConnectionScreen() {
  const store = useConnection();
  const insets = useSafeAreaInsets();
  const actionScale = useSharedValue(1);

  useEffect(() => {
    actionScale.value = withSpring(1, { damping: 15, stiffness: 190 });
  }, [actionScale, store.phase]);

  const actionStyle = useAnimatedStyle(() => ({
    transform: [{ scale: actionScale.value }],
  }));
  const [title, subtitle] = phaseCopy[store.phase];

  const primaryAction = () => {
    actionScale.value = 0.975;
    if (store.phase === 'connected') store.disconnect();
    else if (!store.selectedKey) store.setScreen('settings');
    else void store.connect();
  };

  return (
    <View style={[styles.root, { paddingTop: insets.top }]}>
      <View style={styles.navigation}>
        <Text style={styles.navigationTitle}>NitronBox</Text>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Connection settings"
          hitSlop={10}
          onPress={() => store.setScreen('settings')}
          style={({ pressed }) => [styles.navigationButton, pressed && styles.pressed]}
        >
          <Settings2 size={20} color={colors.label} strokeWidth={2} />
        </Pressable>
      </View>

      <ScrollView
        contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 120 }]}
        showsVerticalScrollIndicator={false}
      >
        <Animated.View
          key={store.phase}
          entering={FadeInDown.springify().damping(18).stiffness(170)}
          exiting={FadeOut.duration(120)}
          style={styles.status}
        >
          <View style={styles.statusLine}>
            <StatusIndicator phase={store.phase} />
            <Text style={styles.eyebrow}>{store.selectedPoint.name}</Text>
          </View>
          <Text style={styles.hero}>{title}</Text>
          <Text style={styles.subtitle}>{store.message ?? subtitle}</Text>
        </Animated.View>

        <Animated.View layout={LinearTransition.springify()} style={styles.metrics}>
          <Metric label="Latency" value={store.latencyMs == null ? '—' : `${store.latencyMs} ms`} />
          <View style={styles.metricSeparator} />
          <Metric label="Models" value={store.modelCount == null ? '—' : String(store.modelCount)} />
          <View style={styles.metricSeparator} />
          <Metric label="Status" value={store.phase === 'connected' ? 'Live' : 'Offline'} />
        </Animated.View>

        <Text style={styles.sectionTitle}>Connection points</Text>
        <View style={styles.list}>
          {store.points.map((point, index) => {
            const selected = point.id === store.selectedPointId;
            return (
              <Animated.View key={point.id} entering={FadeIn.delay(index * 40)}>
                <Pressable
                  testID={`point-${point.id}`}
                  accessibilityRole="button"
                  accessibilityState={{ selected }}
                  onPress={() => store.selectPoint(point.id)}
                  style={({ pressed }) => [styles.row, pressed && styles.pressed]}
                >
                  <View style={styles.symbolFrame}>
                    <Symbol name={point.symbol} size={19} color={colors.label} strokeWidth={1.8} />
                  </View>
                  <View style={styles.rowCopy}>
                    <Text style={styles.rowTitle}>{point.name}</Text>
                    <Text style={styles.rowDetail}>{point.detail}</Text>
                  </View>
                  <View
                    style={[
                      styles.health,
                      {
                        backgroundColor:
                          point.health === 'online'
                            ? colors.success
                            : point.health === 'degraded'
                              ? colors.warning
                              : colors.destructive,
                      },
                    ]}
                  />
                  {selected ? (
                    <View style={styles.selectedMark} />
                  ) : (
                    <ChevronRight size={17} color={colors.tertiaryLabel} />
                  )}
                </Pressable>
                {index < store.points.length - 1 && <View style={styles.separator} />}
              </Animated.View>
            );
          })}
        </View>
      </ScrollView>

      <View style={[styles.actionDock, { paddingBottom: Math.max(insets.bottom, spacing.sm) }]}>
        <Animated.View style={actionStyle}>
          <Pressable
            testID="primary-connect-button"
            accessibilityRole="button"
            onPress={primaryAction}
            style={({ pressed }) => [
              styles.primaryButton,
              store.phase === 'connected' && styles.disconnectButton,
              pressed && styles.primaryPressed,
            ]}
          >
            <Power
              size={18}
              color={store.phase === 'connected' ? colors.label : '#FFFFFF'}
              strokeWidth={2.2}
            />
            <Text
              style={[
                styles.primaryButtonText,
                store.phase === 'connected' && styles.disconnectButtonText,
              ]}
            >
              {store.phase === 'connected'
                ? 'Disconnect'
                : store.phase === 'connecting'
                  ? 'Connecting…'
                  : 'Connect'}
            </Text>
          </Pressable>
        </Animated.View>
      </View>

    </View>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.metric}>
      <Text style={styles.metricValue}>{value}</Text>
      <Text style={styles.metricLabel}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.background },
  navigation: {
    height: 48,
    paddingHorizontal: spacing.lg,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  navigationTitle: { color: colors.label, fontSize: 17, fontWeight: '600' },
  navigationButton: { width: 44, height: 44, alignItems: 'flex-end', justifyContent: 'center' },
  content: { paddingHorizontal: spacing.lg },
  status: { paddingTop: spacing.xl, minHeight: 210 },
  statusLine: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs },
  eyebrow: { color: colors.secondaryLabel, fontSize: 13, fontWeight: '600' },
  hero: {
    marginTop: spacing.md,
    color: colors.label,
    fontSize: 44,
    lineHeight: 49,
    fontWeight: '700',
    letterSpacing: -1.7,
  },
  subtitle: {
    maxWidth: 330,
    marginTop: spacing.xs,
    color: colors.secondaryLabel,
    fontSize: 15,
    lineHeight: 21,
  },
  metrics: {
    height: 74,
    flexDirection: 'row',
    alignItems: 'center',
    borderTopWidth: StyleSheet.hairlineWidth,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderColor: colors.separator,
  },
  metric: { flex: 1, alignItems: 'center' },
  metricValue: { color: colors.label, fontSize: 17, fontWeight: '600', fontVariant: ['tabular-nums'] },
  metricLabel: { marginTop: 3, color: colors.tertiaryLabel, fontSize: 11 },
  metricSeparator: { width: StyleSheet.hairlineWidth, height: 28, backgroundColor: colors.separator },
  sectionTitle: {
    marginTop: spacing.xl,
    marginBottom: spacing.sm,
    color: colors.secondaryLabel,
    fontSize: 13,
    fontWeight: '600',
  },
  list: { borderTopWidth: StyleSheet.hairlineWidth, borderColor: colors.separator },
  row: { height: 66, flexDirection: 'row', alignItems: 'center' },
  symbolFrame: {
    width: 34,
    height: 34,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.control,
    backgroundColor: colors.secondaryBackground,
  },
  rowCopy: { flex: 1, marginLeft: spacing.sm },
  rowTitle: { color: colors.label, fontSize: 15, fontWeight: '500' },
  rowDetail: { marginTop: 2, color: colors.tertiaryLabel, fontSize: 12 },
  health: { width: 6, height: 6, marginRight: spacing.sm, borderRadius: 3 },
  selectedMark: {
    width: 18,
    height: 18,
    borderWidth: 5,
    borderColor: colors.accent,
    borderRadius: 9,
  },
  separator: { height: StyleSheet.hairlineWidth, marginLeft: 46, backgroundColor: colors.separator },
  actionDock: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.sm,
    backgroundColor: colors.background,
  },
  primaryButton: {
    height: 50,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.xs,
    borderRadius: radius.control,
    backgroundColor: colors.accent,
  },
  disconnectButton: { backgroundColor: colors.secondaryBackground },
  primaryPressed: { transform: [{ scale: 0.985 }], opacity: 0.88 },
  primaryButtonText: { color: '#FFFFFF', fontSize: 17, fontWeight: '600' },
  disconnectButtonText: { color: colors.label },
  pressed: { opacity: 0.5 },
});
