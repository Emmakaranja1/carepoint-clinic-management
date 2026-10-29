"use client";

import { useEffect, useRef } from "react";

import { refreshSession } from "@/lib/sessionRefresh";

const IDLE_MS = (Number(process.env.NEXT_PUBLIC_SESSION_IDLE_MINUTES) || 15) * 60_000;
const KEEPALIVE_MS = 60_000;
const CHECK_MS = 5_000;
const ACTIVITY_KEY = "cp:lastActivity";
const ACTIVITY_EVENTS = ["pointerdown", "keydown", "scroll", "touchstart", "mousemove"] as const;

function sharedActivity() {
  try {
    return Number(localStorage.getItem(ACTIVITY_KEY)) || 0;
  } catch {
    return 0;
  }
}

function shareActivity(at: number) {
  try {
    localStorage.setItem(ACTIVITY_KEY, String(at));
  } catch {}
}

/**
 * Silently ends the session after the idle limit with no interaction in any tab.
 * While the user is active the session is refreshed every KEEPALIVE_MS, so the
 * server's sliding window (idle limit + 1 min) never lapses first.
 */
export function useIdleTimeout(onTimeout: () => void) {
  const lastActivity = useRef(0);

  useEffect(() => {
    const markActive = () => {
      const now = Date.now();
      if (now - lastActivity.current >= CHECK_MS) shareActivity(now);
      lastActivity.current = now;
    };
    const onStorage = (event: StorageEvent) => {
      if (event.key === ACTIVITY_KEY && event.newValue) {
        lastActivity.current = Math.max(lastActivity.current, Number(event.newValue));
      }
    };

    markActive();
    ACTIVITY_EVENTS.forEach((name) => window.addEventListener(name, markActive, { passive: true }));
    window.addEventListener("storage", onStorage);

    const timer = window.setInterval(() => {
      const idle = Date.now() - Math.max(lastActivity.current, sharedActivity());
      if (idle >= IDLE_MS) {
        window.clearInterval(timer);
        onTimeout();
      } else if (idle < KEEPALIVE_MS) {
        void refreshSession(KEEPALIVE_MS);
      }
    }, CHECK_MS);

    return () => {
      window.clearInterval(timer);
      ACTIVITY_EVENTS.forEach((name) => window.removeEventListener(name, markActive));
      window.removeEventListener("storage", onStorage);
    };
  }, [onTimeout]);
}
