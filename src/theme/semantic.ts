import {
  DynamicColorIOS,
  Platform,
  type ColorValue,
} from 'react-native';

const dynamic = (
  light: string,
  dark: string,
  _androidResource: string,
): ColorValue =>
  Platform.OS === 'ios'
    ? DynamicColorIOS({ light, dark })
    : light;

export const colors = {
  background: dynamic('#FFFFFF', '#000000', '?attr/colorBackground'),
  secondaryBackground: dynamic(
    '#F2F2F7',
    '#1C1C1E',
    '?attr/colorBackgroundFloating',
  ),
  tertiaryBackground: dynamic('#FFFFFF', '#2C2C2E', '?attr/colorBackground'),
  label: dynamic('#000000', '#FFFFFF', '?attr/textColorPrimary'),
  secondaryLabel: dynamic('#3C3C4399', '#EBEBF599', '?attr/textColorSecondary'),
  tertiaryLabel: dynamic('#3C3C434D', '#EBEBF54D', '?attr/textColorTertiary'),
  separator: dynamic('#3C3C434A', '#54545899', '?attr/colorControlNormal'),
  fill: dynamic('#78788029', '#7878805C', '?attr/colorControlHighlight'),
  accent: dynamic('#007AFF', '#0A84FF', '?attr/colorAccent'),
  success: dynamic('#248A3D', '#30D158', '@android:color/holo_green_light'),
  warning: dynamic('#B25000', '#FF9F0A', '@android:color/holo_orange_light'),
  destructive: dynamic('#FF3B30', '#FF453A', '@android:color/holo_red_light'),
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
