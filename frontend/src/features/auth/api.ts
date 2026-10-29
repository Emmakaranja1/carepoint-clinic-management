import { api } from "@/lib/api";

import type { SessionUser } from "./session";

/** POST /auth/register → 201 {id, email, message} — no session yet. */
export function register(body: {
  email: string;
  password: string;
  confirmPassword: string;
  fullName: string;
  phone?: string;
}) {
  return api<{ id: string; email: string; message: string }>("/auth/register", {
    method: "POST",
    body,
  });
}

/** POST /auth/verify-email → 200 UserResponse + session cookies. */
export function verifyEmail(email: string, code: string) {
  return api<SessionUser>("/auth/verify-email", {
    method: "POST",
    body: { email, code },
  });
}

/** POST /auth/resend-code → 204 (rate-limited server-side). */
export function resendCode(email: string, purpose: "VERIFY_EMAIL" | "PASSWORD_RESET") {
  return api<void>("/auth/resend-code", {
    method: "POST",
    body: { email, purpose },
  });
}

/** POST /auth/forgot-password → always 204 (no account enumeration). */
export function forgotPassword(email: string) {
  return api<void>("/auth/forgot-password", {
    method: "POST",
    body: { email },
  });
}

/** POST /auth/verify-reset-code → 204; validates the code without consuming it. */
export function verifyResetCode(email: string, code: string) {
  return api<void>("/auth/verify-reset-code", {
    method: "POST",
    body: { email, code },
  });
}

/** POST /auth/reset-password → 204; all sessions revoked server-side. */
export function resetPassword(body: {
  email: string;
  code: string;
  password: string;
  confirmPassword: string;
}) {
  return api<void>("/auth/reset-password", { method: "POST", body });
}

/** POST /auth/login → 200 UserResponse + session cookies. */
export function login(email: string, password: string) {
  return api<SessionUser>("/auth/login", {
    method: "POST",
    body: { email, password },
  });
}

/** POST /auth/google → 200 UserResponse + session cookies. */
export function googleLogin(idToken: string) {
  return api<SessionUser>("/auth/google", {
    method: "POST",
    body: { idToken },
  });
}
