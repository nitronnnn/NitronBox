import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, {
  cancelAnimation,
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

import { colors } from '@/theme/semantic';
import type { ConnectionPhase } from '@/connection/types';

export function StatusIndicator({ phase }: { phase: ConnectionPhase }) {
  const scale = useSharedValue(1);
  const opacity = useSharedValue(1);

  useEffect(() => {
    cancelAnimation(scale);
    cancelAnimation(opacity);
    if (phase === 'connecting') {
      scale.value = withRepeat(
        withSequence(
          withTiming(1.24, { duration: 700 }),
          withTiming(1, { duration: 700 }),
        ),
        -1,
      );
      opacity.value = withRepeat(
        withSequence(
          withTiming(0.45, { duration: 700 }),
          withTiming(1, { duration: 700 }),
        ),
        -1,
      );
    } else {
      scale.value = withSpring(1, { damping: 15, stiffness: 180 });
      opacity.value = withTiming(1, { duration: 180 });
    }
  }, [opacity, phase, scale]);

  const animated = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
    opacity: opacity.value,
  }));
  const color =
    phase === 'connected'
      ? colors.success
      : phase === 'error'
        ? colors.destructive
        : phase === 'connecting'
          ? colors.accent
          : colors.tertiaryLabel;

  return (
    <View style={styles.frame}>
      <Animated.View style={[styles.glow, { backgroundColor: color }, animated]} />
      <View style={[styles.dot, { backgroundColor: color }]} />
    </View>
  );
}

const styles = StyleSheet.create({
  frame: { width: 18, height: 18, alignItems: 'center', justifyContent: 'center' },
  glow: { position: 'absolute', width: 18, height: 18, borderRadius: 9, opacity: 0.2 },
  dot: { width: 7, height: 7, borderRadius: 4 },
});
