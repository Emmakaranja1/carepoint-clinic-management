import { afterEach, describe, expect, it, vi } from "vitest";

import { api, ApiError, onSessionExpired } from "./api";

describe("ApiError", () => {
  it("preserves the backend error envelope", () => {
    const err = new ApiError("VALIDATION_FAILED", "Request validation failed", {
      email: "Must be a valid email",
    }, 400);
    expect(err.code).toBe("VALIDATION_FAILED");
    expect(err.fields?.email).toBe("Must be a valid email");
    expect(err.status).toBe(400);
    expect(err).toBeInstanceOf(Error);
  });
});

describe("api session refresh", () => {
  const json = (status: number, body: unknown = {}) =>
    new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

  function mockFetch(handler: (url: string) => Response) {
    const fn = vi.fn(async (url: string) => handler(url));
    vi.stubGlobal("fetch", fn);
    return fn;
  }

  afterEach(() => vi.unstubAllGlobals());

  it("refreshes once on 401 and retries the request", async () => {
    let authed = false;
    const fetchMock = mockFetch((url) => {
      if (url.endsWith("/auth/refresh")) {
        authed = true;
        return new Response(null, { status: 204 });
      }
      return authed ? json(200, { ok: true }) : json(401, { code: "UNAUTHENTICATED" });
    });

    await expect(api<{ ok: boolean }>("/appointments/mine")).resolves.toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it("shares a single refresh between concurrent 401s", async () => {
    let authed = false;
    const fetchMock = mockFetch((url) => {
      if (url.endsWith("/auth/refresh")) {
        authed = true;
        return new Response(null, { status: 204 });
      }
      return authed ? json(200, []) : json(401);
    });

    await Promise.all([api("/appointments/mine"), api("/clinics/mine"), api("/auth/me")]);
    expect(fetchMock.mock.calls.filter(([url]) => url.endsWith("/auth/refresh"))).toHaveLength(1);
  });

  it("notifies session-expired listeners when refresh fails", async () => {
    mockFetch(() => json(401, { code: "UNAUTHENTICATED", message: "Authentication required" }));
    const listener = vi.fn();
    const unsubscribe = onSessionExpired(listener);

    await expect(api("/appointments/mine")).rejects.toMatchObject({ status: 401 });
    expect(listener).toHaveBeenCalledOnce();
    unsubscribe();
  });

  it("refreshes for authenticated account endpoints", async () => {
    let authed = false;
    const fetchMock = mockFetch((url) => {
      if (url.endsWith("/auth/refresh")) {
        authed = true;
        return new Response(null, { status: 204 });
      }
      return authed ? new Response(null, { status: 204 }) : json(401);
    });

    await api("/auth/change-password", { method: "POST", body: {} });
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it("does not refresh for credential endpoints", async () => {
    const fetchMock = mockFetch(() => json(401, { code: "INVALID_CREDENTIALS" }));

    await expect(api("/auth/login", { method: "POST", body: {} })).rejects.toBeInstanceOf(ApiError);
    expect(fetchMock).toHaveBeenCalledOnce();
  });
});
