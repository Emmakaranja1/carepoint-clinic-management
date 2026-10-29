import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { lastRefreshAt, refreshSession } from "./sessionRefresh";

function memoryStorage() {
  const data = new Map<string, string>();
  return {
    getItem: (key: string) => data.get(key) ?? null,
    setItem: (key: string, value: string) => void data.set(key, value),
    removeItem: (key: string) => void data.delete(key),
    clear: () => data.clear(),
  };
}

describe("refreshSession", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.stubGlobal("localStorage", memoryStorage());
    fetchMock = vi.fn(async () => new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => vi.unstubAllGlobals());

  it("records a successful refresh for other tabs", async () => {
    await expect(refreshSession()).resolves.toBe(true);
    expect(lastRefreshAt()).toBeGreaterThan(Date.now() - 1_000);
  });

  it("skips the network when any tab refreshed within the window", async () => {
    localStorage.setItem("cp:lastRefresh", String(Date.now() - 30_000));

    await expect(refreshSession(60_000)).resolves.toBe(true);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("refreshes once the window has passed", async () => {
    localStorage.setItem("cp:lastRefresh", String(Date.now() - 61_000));

    await refreshSession(60_000);
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it("reports failure without recording it", async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }));

    await expect(refreshSession(0)).resolves.toBe(false);
    expect(lastRefreshAt()).toBe(0);
  });
});
