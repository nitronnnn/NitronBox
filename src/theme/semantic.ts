import { DynamicColorIOS, Platform, type ColorValue } from 'react-native';

const dynamic = (light: string, dark: string): ColorValue =>
  Platform.OS === 'ios' ? DynamicColorIOS({ light, dark }) : dark;

export const colors = {
  background: dynamic('#FFFFFF', '#000000'),
  secondaryBackground: dynamic('#F2F2F7', '#1C1C1E'),
  tertiaryBackground: dynamic('#FFFFFF', '#2C2C2E'),
  label: dynamic('#000000', '#FFFFFF'),
  secondaryLabel: dynamic('#3C3C4399', '#EBEBF599'),
  tertiaryLabel: dynamic('#3C3C434D', '#EBEBF54D'),
  separator: dynamic('#3C3C434A', '#54545899'),
  fill: dynamic('#78788029', '#7878805C'),
  accent: dynamic('#007AFF', '#0A84FF'),
  success: dynamic('#248A3D', '#30D158'),
  warning: dynamic('#B25000', '#FF9F0A'),
  destructive: dynamic('#FF3B30', '#FF453A'),
} as const;

export const spacing = {
  xxs: 4,
  xs: 8,
  sm: 12,
  md: 16,
  lg: 24,
  xl: 32,
  xxl: 48,
} as const;

export const radius = {
  control: 12,
  sheet: 28,
  pill: 999,
} as const;
