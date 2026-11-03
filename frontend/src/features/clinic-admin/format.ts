import type { ActivityItem, InvitationState, MembershipStatus, StaffRole } from "./api";

export const roleLabels: Record<StaffRole, string> = {
  CLINIC_ADMIN: "Clinic administrator",
  DOCTOR: "Doctor",
  RECEPTIONIST: "Receptionist",
};

export function roleLabel(role: string) {
  return roleLabels[role as StaffRole] ?? role;
}

export type AccessState = "active" | "pending" | "expired" | "disabled";

export function accessState(status: MembershipStatus, invitation: InvitationState): AccessState {
  if (status === "DISABLED") return "disabled";
  if (invitation === "PENDING") return "pending";
  if (invitation === "EXPIRED") return "expired";
  return "active";
}

export const accessLabels: Record<AccessState, string> = {
  active: "Active",
  pending: "Invitation sent",
  expired: "Invitation expired",
  disabled: "Deactivated",
};

export function describeActivity(item: ActivityItem): string {
  const actor = item.actorName ?? "Someone";
  const target = item.targetName ?? "a staff member";
  switch (item.action) {
    case "STAFF_ADDED":
      return `${actor} added ${target} as ${roleLabel(item.role ?? "").toLowerCase()}`;
    case "STAFF_INVITATION_SENT":
      return item.actorIsTarget
        ? `An activation invitation was sent to ${target}`
        : `${actor} sent an invitation to ${target}`;
    case "STAFF_INVITATION_ACCEPTED":
      return `${target} accepted their invitation and activated their account`;
    case "STAFF_STATUS_CHANGED":
      return item.to === "DISABLED"
        ? `${actor} deactivated ${target}`
        : `${actor} reactivated ${target}`;
    case "STAFF_ROLE_CHANGED":
      return `${actor} changed ${target}'s role from ${roleLabel(item.from ?? "").toLowerCase()} to ${roleLabel(item.to ?? "").toLowerCase()}`;
    case "CLINIC_ADMIN_PROVISIONED":
      return `${target} was provisioned as clinic administrator`;
    default:
      return `${actor} · ${item.action.toLowerCase().replaceAll("_", " ")}`;
  }
}

export function clinicToday(timeZone: string, now = new Date()) {
  return new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" }).format(now);
}

export function formatDate(iso: string, timeZone?: string) {
  return new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric", timeZone }).format(
    new Date(iso),
  );
}

export function formatDateTime(iso: string, timeZone?: string) {
  return new Intl.DateTimeFormat("en-GB", {
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
    timeZone,
  }).format(new Date(iso));
}

export function relativeTime(iso: string, now = Date.now()) {
  const minutes = Math.round((now - new Date(iso).getTime()) / 60_000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  const days = Math.round(hours / 24);
  return days < 30 ? `${days} d ago` : formatDate(iso);
}

export function percent(value: number) {
  return `${Math.round(value * 100)}%`;
}
