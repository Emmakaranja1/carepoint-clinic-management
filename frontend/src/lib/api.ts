/**
 * Thin API client — the only place fetch() is called.
 *
 * - Same-origin: requests go to /api/v1/* and next.config.ts proxies to
 *   the backend (ADR-0002). No base URL juggling, no CORS.
 * - Cookie session: credentials are always included; the httpOnly
 *   cp_access/cp_refresh cookies do the work — never touch a token here.
 * - CSRF: every mutating call needs X-Requested-With (CsrfHeaderFilter).
 * - Errors: the backend envelope {code, message, fields?} is preserved
 *   in ApiError so forms can map field errors directly.
 */

export class ApiError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly fields?: Record<string, string>,
    public readonly status = 0,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

type Json = Record<string, unknown> | unknown[] | string | number | boolean | null;

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: Json;
  idempotencyKey?: string;
  /** caller-side timeout guard (default 15s) */
  timeoutMs?: number;
}

let refreshing: Promise<boolean> | null = null;
const expiredListeners = new Set<() => void>();

export function onSessionExpired(listener: () => void) {
  expiredListeners.add(listener);
  return () => void expiredListeners.delete(listener);
}

function refreshSession() {
  refreshing ??= fetch("/api/v1/auth/refresh", {
    method: "POST",
    headers: { "X-Requested-With": "XMLHttpRequest" },
    credentials: "include",
  })
    .then((res) => res.ok)
    .catch(() => false)
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

async function send(path: string, opts: RequestOptions) {
  const { method = "GET", body, idempotencyKey, timeoutMs = 15_000 } = opts;
  const headers: Record<string, string> = {};
  if (method !== "GET") {
    headers["X-Requested-With"] = "XMLHttpRequest";
  }
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (idempotencyKey) {
    headers["Idempotency-Key"] = idempotencyKey;
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(`/api/v1${path}`, {
      method,
      headers,
      credentials: "include",
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    });
  } catch {
    throw new ApiError("NETWORK_ERROR", "Could not reach the server");
  } finally {
    clearTimeout(timer);
  }
}

export async function api<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  let res = await send(path, opts);
  if (res.status === 401 && (path === "/auth/me" || !path.startsWith("/auth/"))) {
    if (await refreshSession()) {
      res = await send(path, opts);
    }
    if (res.status === 401) {
      expiredListeners.forEach((listener) => listener());
    }
  }

  if (res.status === 204) {
    return undefined as T;
  }
  const data = (await res.json().catch(() => null)) as
    | { code?: string; message?: string; fields?: Record<string, string> }
    | T
    | null;
  if (!res.ok) {
    const err = data as { code?: string; message?: string; fields?: Record<string, string> } | null;
    throw new ApiError(
      err?.code ?? "UNKNOWN_ERROR",
      err?.message ?? `Request failed (${res.status})`,
      err?.fields,
      res.status,
    );
  }
  return data as T;
}
