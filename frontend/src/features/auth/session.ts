"use client";

import { create } from "zustand";

import { api, onSessionExpired } from "@/lib/api";

/** Shape of GET /api/v1/auth/me — the session-hydration contract. */
export interface SessionUser {
  id: string;
  email: string;
  fullName: string;
  phone: string | null;
  passwordSet: boolean;
  /** clinicId → role (DOCTOR | RECEPTIONIST | CLINIC_ADMIN); empty = patient */
  clinicRoles: Record<string, string>;
}

export type SessionStatus = "loading" | "anonymous" | "authenticated";

interface SessionState {
  user: SessionUser | null;
  status: SessionStatus;
  /** Call once on app mount — resolves the cookie session, if any. */
  hydrate: () => Promise<void>;
  /** Set directly after login/verify/google — no extra /me round-trip. */
  setUser: (user: SessionUser) => void;
  logout: () => Promise<void>;
}

export const useSession = create<SessionState>((set) => ({
  user: null,
  status: "loading",
  setUser: (user) => set({ user, status: "authenticated" }),
  hydrate: async () => {
    try {
      const user = await api<SessionUser>("/auth/me");
      set({ user, status: "authenticated" });
    } catch {
      // 401 = no session — anonymous is a normal state, not an error
      set({ user: null, status: "anonymous" });
    }
  },
  logout: async () => {
    try {
      await api("/auth/logout", { method: "POST" });
    } finally {
      set({ user: null, status: "anonymous" });
    }
  },
}));

onSessionExpired(() => useSession.setState({ user: null, status: "anonymous" }));
