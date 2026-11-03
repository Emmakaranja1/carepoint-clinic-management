import { api } from "@/lib/api";

export interface InvitationPreview {
  clinicName: string;
  email: string;
  fullName: string;
  role: string;
  expiresAt: string;
}

export function previewInvitation(token: string) {
  return api<InvitationPreview>("/invitations/preview", { method: "POST", body: { token } });
}

export function acceptInvitation(token: string, password: string, confirmPassword: string) {
  return api<void>("/invitations/accept", { method: "POST", body: { token, password, confirmPassword } });
}
