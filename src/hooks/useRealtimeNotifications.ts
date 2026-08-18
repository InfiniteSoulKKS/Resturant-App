import { useEffect, useRef } from 'react';
import { getRealtimeStreamUrl } from '../lib/apiClient';
import { getToken } from '../lib/tokenManager';

/**
 * Subscribes to the backend SSE stream and invokes `onEvent` for every
 * "notification" event. EventSource auto-reconnects on network errors.
 */
export function useRealtimeNotifications(onEvent: (data: any) => void, enabled: boolean) {
  const onEventRef = useRef(onEvent);
  onEventRef.current = onEvent;

  useEffect(() => {
    if (!enabled || !getToken()) return;

    let es: EventSource | null = null;

    const connect = () => {
      es = new EventSource(getRealtimeStreamUrl());

      es.addEventListener('notification', (e) => {
        try {
          onEventRef.current(JSON.parse((e as MessageEvent).data));
        } catch (err) {
          console.error('Failed to parse SSE notification:', err);
        }
      });

      es.onerror = () => {
        // EventSource auto-reconnects; nothing to do here.
      };
    };

    connect();
    return () => {
      es?.close();
    };
  }, [enabled]);
}
