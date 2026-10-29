const LAST_REFRESH_KEY = "cp:lastRefresh";
const LOCK_NAME = "cp-session-refresh";
const RECENT_MS = 10_000;

let inFlight: Promise<boolean> | null = null;

export function lastRefreshAt() {
  try {
    return Number(localStorage.getItem(LAST_REFRESH_KEY)) || 0;
  } catch {
    return 0;
  }
}

function markRefreshed() {
  try {
    localStorage.setItem(LAST_REFRESH_KEY, String(Date.now()));
  } catch {}
}

function withCrossTabLock(task: () => Promise<boolean>): Promise<boolean> {
  const locks = typeof navigator === "undefined" ? undefined : navigator.locks;
  return locks ? locks.request(LOCK_NAME, task).then((result) => result) : task();
}

/**
 * Rotates the refresh cookie at most once across all tabs: rotation revokes the
 * presented token, so two tabs racing would trip reuse detection and kill the
 * session. A refresh by any tab within `minAgeMs` counts as done.
 */
export function refreshSession(minAgeMs = RECENT_MS): Promise<boolean> {
  if (Date.now() - lastRefreshAt() < minAgeMs) return Promise.resolve(true);
  inFlight ??= withCrossTabLock(async () => {
    if (Date.now() - lastRefreshAt() < minAgeMs) return true;
    const ok = await fetch("/api/v1/auth/refresh", {
      method: "POST",
      headers: { "X-Requested-With": "XMLHttpRequest" },
      credentials: "include",
    })
      .then((res) => res.ok)
      .catch(() => false);
    if (ok) markRefreshed();
    return ok;
  }).finally(() => {
    inFlight = null;
  });
  return inFlight;
}
