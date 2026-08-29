import { useEffect, useRef } from 'react';
import { getRealtimeStreamUrl } from '../lib/apiClient';
import { getToken } from '../lib/tokenManager';

export type RealtimeEvent =
  | { type: 'notification'; data: any }
  | { type: 'menu_availability'; data: any }
  | { type: 'table_availability'; data: any }
  | { type: 'ingredient_low_stock'; data: any }
  | { type: 'ingredient_stock_update'; data: any };

/**
 * Subscribes to the backend SSE stream and invokes `onEvent` for every
 * event (notifications, menu availability, table availability).
 * EventSource auto-reconnects on network errors.
 */
export function useRealtimeNotifications(onEvent: (event: RealtimeEvent) => void, enabled: boolean) {
  const onEventRef = useRef(onEvent);
  onEventRef.current = onEvent;

  useEffect(() => {
    if (!enabled || !getToken()) return;

    let es: EventSource | null = null;

    const connect = () => {
      es = new EventSource(getRealtimeStreamUrl());

      es.addEventListener('notification', (e) => {
        try {
          onEventRef.current({ type: 'notification', data: JSON.parse((e as MessageEvent).data) });
        } catch (err) {
          console.error('Failed to parse SSE notification:', err);
        }
      });

      es.addEventListener('menu_availability', (e) => {
        try {
          onEventRef.current({ type: 'menu_availability', data: JSON.parse((e as MessageEvent).data) });
        } catch (err) {
          console.error('Failed to parse SSE menu_availability:', err);
        }
      });

      es.addEventListener('table_availability', (e) => {
        try {
          onEventRef.current({ type: 'table_availability', data: JSON.parse((e as MessageEvent).data) });
        } catch (err) {
          console.error('Failed to parse SSE table_availability:', err);
        }
      });

      es.addEventListener('ingredient_low_stock', (e) => {
        try {
          onEventRef.current({ type: 'ingredient_low_stock', data: JSON.parse((e as MessageEvent).data) });
        } catch (err) {
          console.error('Failed to parse SSE ingredient_low_stock:', err);
        }
      });

      es.addEventListener('ingredient_stock_update', (e) => {
        try {
          onEventRef.current({ type: 'ingredient_stock_update', data: JSON.parse((e as MessageEvent).data) });
        } catch (err) {
          console.error('Failed to parse SSE ingredient_stock_update:', err);
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
