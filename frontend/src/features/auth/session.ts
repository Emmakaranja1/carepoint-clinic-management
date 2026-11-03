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

export function getPrimaryClinicRole(user: SessionUser | null): string | null {
  if (!user) return null;
  const roleSet = new Set(["CLINIC_ADMIN", "RECEPTIONIST", "DOCTOR"]);
  for (const [clinicId, role] of Object.entries(user.clinicRoles ?? {})) {
    if (roleSet.has(role) && clinicId) return role;
  }
  return null;
}

export function getWorkspaceHref(user: SessionUser | null) {
  if (!user) return "/login";
  const role = getPrimaryClinicRole(user);
  const clinicIdForRole = (targetRole: string) =>
    Object.entries(user.clinicRoles ?? {}).find(([, value]) => value === targetRole)?.[0];

  if (role === "CLINIC_ADMIN") return "/clinic-admin/overview";
  if (role === "RECEPTIONIST") {
    const clinicId = clinicIdForRole("RECEPTIONIST");
    if (clinicId) return `/staff/${clinicId}/agenda`;
  }
  if (role === "DOCTOR") {
    const clinicId = clinicIdForRole("DOCTOR");
    if (clinicId) return `/doctor/${clinicId}/agenda`;
  }
  return "/dashboard";
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
