import type { RealtimeConnectionSource } from './types';

export const idleRealtimeSource: RealtimeConnectionSource = {
  subscribe() {
    return () => undefined;
  },
};

// Supabase adapter example. The UI/store only depends on RealtimeConnectionSource.
// Pass an initialized Supabase client and the store requires no changes.
export const createSupabaseRealtimeSource = (client: {
  channel(name: string): {
    on(
      type: 'postgres_changes',
      filter: Record<string, string>,
      callback: (payload: { new: Record<string, unknown> }) => void,
    ): { subscribe(): unknown };
    unsubscribe(): unknown;
  };
}): RealtimeConnectionSource => ({
  subscribe(listener) {
    const channel = client.channel('connection-status');
    channel
      .on(
        'postgres_changes',
        { event: '*', schema: 'public', table: 'connection_status' },
        ({ new: row }) => {
          listener({
            latencyMs: typeof row.latency_ms === 'number' ? row.latency_ms : null,
            modelCount: typeof row.model_count === 'number' ? row.model_count : null,
            lastUpdatedAt: Date.now(),
          });
        },
      )
      .subscribe();
    return () => void channel.unsubscribe();
  },
});
