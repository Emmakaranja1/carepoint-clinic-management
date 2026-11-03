import { api } from "@/lib/api";

export interface AgendaEntry {
  id: string;
  patientId: string;
  patientName: string;
  doctorId: string;
  doctorName: string;
  status: "BOOKED" | "CANCELLED" | "COMPLETED" | "NO_SHOW" | string;
  start: string;
  end: string;
  reason: string | null;
  bookedForGuest: boolean;
  cancelledAt: string | null;
}

export interface AgendaPage {
  content: AgendaEntry[];
  page: number;
  size: number;
  totalElements: number;
}

export function listStaffAgenda(
  clinicId: string,
  params: {
    from?: string;
    to?: string;
    doctorId?: string;
    status?: string;
    page?: number;
    size?: number;
  } = {},
) {
  const search = new URLSearchParams();
  if (params.from) search.set("from", params.from);
  if (params.to) search.set("to", params.to);
  if (params.doctorId) search.set("doctorId", params.doctorId);
  if (params.status) search.set("status", params.status);
  if (params.page !== undefined) search.set("page", String(params.page));
  if (params.size !== undefined) search.set("size", String(params.size));
  const qs = search.toString();
  return api<AgendaPage>(`/clinics/${encodeURIComponent(clinicId)}/appointments${qs ? `?${qs}` : ""}`);
}

export function transitionAppointment(clinicId: string, id: string, status: "CANCELLED" | "COMPLETED" | "NO_SHOW") {
  return api<AgendaEntry>(`/clinics/${encodeURIComponent(clinicId)}/appointments/${encodeURIComponent(id)}`, {
    method: "PATCH",
    body: { status },
  });
}

export function bookOnBehalf(
  clinicId: string,
  body: {
    doctorId: string;
    start: string;
    end: string;
    reason?: string | null;
    patientId?: string;
    patient?: {
      email: string;
      fullName: string;
      phone?: string;
    };
  },
  idempotencyKey?: string,
) {
  return api<AgendaEntry>(`/clinics/${encodeURIComponent(clinicId)}/appointments`, {
    method: "POST",
    body,
    idempotencyKey,
  });
}

export function rescheduleAppointment(
  clinicId: string,
  id: string,
  body: { doctorId?: string; start: string; end: string; reason?: string | null },
) {
  return api<AgendaEntry>(`/clinics/${encodeURIComponent(clinicId)}/appointments/${encodeURIComponent(id)}/reschedule`, {
    method: "POST",
    body,
  });
}
