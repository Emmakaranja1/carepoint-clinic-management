"use client";

import { createContext, useContext } from "react";

import type { SessionUser } from "@/features/auth/session";
import type { ClinicMembership } from "@/features/clinics/api";

export interface AdminWorkspace {
  user: SessionUser;
  clinics: ClinicMembership[];
  clinic: ClinicMembership;
  selectClinic: (clinicId: string) => void;
  signOut: () => void;
}

export const AdminWorkspaceContext = createContext<AdminWorkspace | null>(null);

export function useAdminWorkspace() {
  const ctx = useContext(AdminWorkspaceContext);
  if (!ctx) throw new Error("useAdminWorkspace must be used inside the clinic admin layout");
  return ctx;
}
