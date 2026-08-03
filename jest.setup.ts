jest.mock('expo-haptics', () => ({
  impactAsync: jest.fn(),
  notificationAsync: jest.fn(),
  selectionAsync: jest.fn(),
  ImpactFeedbackStyle: { Light: 'Light', Medium: 'Medium' },
  NotificationFeedbackType: { Success: 'Success', Error: 'Error' },
}));

jest.mock('react-native-reanimated', () => require('react-native-reanimated/mock'));

jest.mock('lucide-react-native', () => {
  const React = require('react');
  const { View } = require('react-native');
  const Icon = (props: Record<string, unknown>) =>
    React.createElement(View, { ...props, testID: props.testID ?? 'lucide-icon' });
  return {
    Brain: Icon,
    ChevronRight: Icon,
    Gem: Icon,
    Power: Icon,
    Server: Icon,
    Settings2: Icon,
    Sparkles: Icon,
    X: Icon,
  };
});

jest.mock('expo-blur', () => ({
  BlurView: ({ children }: { children?: React.ReactNode }) => children,
}));
