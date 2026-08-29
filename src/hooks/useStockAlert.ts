import { useRef, useCallback, useEffect } from 'react';

/**
 * Plays an audible alert and triggers device vibration when ingredients
 * are depleted. Uses the Web Audio API to generate a tone (no external
 * sound files needed) and the Vibration API for haptic feedback.
 *
 * Sound pattern: two short beeps (urgent, kitchen-friendly).
 * Vibration pattern: short-pulse × 2 (matches the beeps).
 */
export function useStockAlert() {
  const audioCtxRef = useRef<AudioContext | null>(null);
  const lastAlertRef = useRef<number>(0);

  /** Get or create the AudioContext (must be created after user interaction). */
  const getAudioContext = useCallback(() => {
    if (!audioCtxRef.current) {
      audioCtxRef.current = new AudioContext();
    }
    return audioCtxRef.current;
  }, []);

  /** Play a two-beep alert tone. */
  const playAlertSound = useCallback(() => {
    try {
      const ctx = getAudioContext();
      if (ctx.state === 'suspended') {
        ctx.resume();
      }

      const now = ctx.currentTime;

      // Two short beeps at 880Hz (A5) — urgent but not annoying
      for (let i = 0; i < 2; i++) {
        const oscillator = ctx.createOscillator();
        const gainNode = ctx.createGain();

        oscillator.type = 'sine';
        oscillator.frequency.setValueAtTime(880, now); // A5
        oscillator.frequency.setValueAtTime(880, now + 0.08);

        gainNode.gain.setValueAtTime(0, now + i * 0.2);
        gainNode.gain.linearRampToValueAtTime(0.3, now + i * 0.2 + 0.02);
        gainNode.gain.linearRampToValueAtTime(0, now + i * 0.2 + 0.12);

        oscillator.connect(gainNode);
        gainNode.connect(ctx.destination);

        oscillator.start(now + i * 0.2);
        oscillator.stop(now + i * 0.2 + 0.15);
      }
    } catch {
      // Audio not available — silent fallback
    }
  }, [getAudioContext]);

  /** Trigger device vibration (mobile only). */
  const vibrate = useCallback(() => {
    try {
      if ('vibrate' in navigator) {
        // Two short pulses: vibrate 100ms, pause 100ms, vibrate 100ms
        navigator.vibrate([100, 100, 100]);
      }
    } catch {
      // Vibration not available — silent fallback
    }
  }, []);

  /**
   * Fire the alert if the depleted count has increased since the last check.
   * Debounced to 5 seconds to avoid spam.
   */
  const checkAndAlert = useCallback((depletedCount: number, previousDepletedCount: number) => {
    if (depletedCount > previousDepletedCount) {
      const now = Date.now();
      if (now - lastAlertRef.current > 5000) {
        lastAlertRef.current = now;
        playAlertSound();
        vibrate();
      }
    }
  }, [playAlertSound, vibrate]);

  /** Clean up AudioContext on unmount. */
  useEffect(() => {
    return () => {
      audioCtxRef.current?.close();
    };
  }, []);

  return { playAlertSound, vibrate, checkAndAlert };
}
