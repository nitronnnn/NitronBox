import { Brain, Gem, Server, Sparkles, type LucideIcon } from 'lucide-react-native';
import type { ComponentProps } from 'react';
import type { ConnectionPoint } from '@/connection/types';

const icons: Record<ConnectionPoint['symbol'], LucideIcon> = {
  sparkles: Sparkles,
  brain: Brain,
  gem: Gem,
  server: Server,
};

type Props = Omit<ComponentProps<LucideIcon>, 'ref'> & {
  name: ConnectionPoint['symbol'];
};

export function Symbol({ name, ...props }: Props) {
  const Icon = icons[name];
  return <Icon {...props} />;
}
