import { api } from "@/lib/api";
import type { SessionUser } from "@/features/auth/session";

export type StaffRole = "CLINIC_ADMIN" | "DOCTOR" | "RECEPTIONIST";
export type MembershipStatus = "ACTIVE" | "DISABLED";
export type InvitationState = "ACCEPTED" | "PENDING" | "EXPIRED" | "NONE";

export interface StaffMember {
  membershipId: string;
  userId: string;
  email: string;
  fullName: string;
  phone: string | null;
  role: StaffRole;
  status: MembershipStatus;
  invitation: InvitationState;
  invitationExpiresAt: string | null;
  joinedAt: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
}

export interface ClinicOverview {
  clinic: {
    id: string;
    name: string;
    slug: string;
    timezone: string;
    cancellationCutoffHours: number;
    slotLookaheadDays: number;
    status: string;
  };
  staff: {
    total: number;
    active: number;
    disabled: number;
    pendingInvitations: number;
    activeByRole: Record<StaffRole, number>;
  };
}

export interface ActivityItem {
  id: number;
  action: string;
  occurredAt: string;
  actorName: string | null;
  targetName: string | null;
  actorIsTarget: boolean;
  role: string | null;
  from: string | null;
  to: string | null;
}

export interface DoctorRow {
  doctorId: string;
  doctorName: string;
  booked: number;
  cancelled: number;
  completed: number;
  noShow: number;
  capacity: number;
  utilization: number;
}

export interface SummaryReport {
  from: string;
  to: string;
  byStatus: Record<"BOOKED" | "CANCELLED" | "COMPLETED" | "NO_SHOW", number>;
  total: number;
  noShowRate: number;
  perDoctor: DoctorRow[];
}

export interface StaffQuery {
  q?: string;
  role?: StaffRole | "";
  status?: MembershipStatus | "";
  page?: number;
  size?: number;
}

const clinicPath = (clinicId: string) => `/clinics/${encodeURIComponent(clinicId)}`;

function query(params: Record<string, string | number | undefined>) {
  const qs = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") qs.set(key, String(value));
  }
  const s = qs.toString();
  return s ? `?${s}` : "";
}

export function clinicAdminLogin(email: string, password: string) {
  return api<SessionUser>("/auth/clinic-admin/login", { method: "POST", body: { email, password } });
}

export function getOverview(clinicId: string) {
  return api<ClinicOverview>(`${clinicPath(clinicId)}/admin/overview`);
}

export function getActivity(clinicId: string, page = 0, size = 8) {
  return api<Page<ActivityItem>>(`${clinicPath(clinicId)}/admin/activity${query({ page, size })}`);
}

export function getSummary(clinicId: string, from?: string, to?: string) {
  return api<SummaryReport>(`${clinicPath(clinicId)}/reports/summary${query({ from, to })}`);
}

export function listStaff(clinicId: string, params: StaffQuery = {}) {
  return api<Page<StaffMember>>(
    `${clinicPath(clinicId)}/staff${query({ ...params, page: params.page ?? 0, size: params.size ?? 20 })}`,
  );
}

export function getStaffMember(clinicId: string, membershipId: string) {
  return api<StaffMember>(`${clinicPath(clinicId)}/staff/${encodeURIComponent(membershipId)}`);
}

export function inviteStaff(
  clinicId: string,
  body: { fullName: string; email: string; phone?: string; role: StaffRole },
) {
  return api<StaffMember>(`${clinicPath(clinicId)}/staff`, { method: "POST", body });
}

export function setStaffStatus(clinicId: string, membershipId: string, status: MembershipStatus) {
  return api<StaffMember>(`${clinicPath(clinicId)}/staff/${encodeURIComponent(membershipId)}`, {
    method: "PATCH",
    body: { status },
  });
}

export function resendInvitation(clinicId: string, membershipId: string) {
  return api<void>(`${clinicPath(clinicId)}/staff/${encodeURIComponent(membershipId)}/invitation`, {
    method: "POST",
  });
}
