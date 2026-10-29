import { api } from "@/lib/api";
import type { SessionUser } from "@/features/auth/session";

export function updateProfile(body: { fullName: string; phone: string }) {
  return api<SessionUser>("/auth/me", { method: "PATCH", body });
}

export function changePassword(body: { currentPassword: string; newPassword: string; confirmPassword: string }) {
  return api<void>("/auth/change-password", { method: "POST", body });
}
