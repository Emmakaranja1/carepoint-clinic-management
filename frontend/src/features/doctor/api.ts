import { api } from "@/lib/api";

export interface DoctorAgendaEntry {
  id: string;
  patientId: string;
  patientName: string;
  status: "BOOKED" | "CANCELLED" | "COMPLETED" | "NO_SHOW" | string;
  start: string;
  end: string;
  reason: string | null;
  bookedForGuest: boolean;
}

export function listDoctorAgenda(clinicId: string, params: { date?: string } = {}) {
  const search = new URLSearchParams();
  if (params.date) search.set("date", params.date);
  const qs = search.toString();
  return api<DoctorAgendaEntry[]>(`/clinics/${encodeURIComponent(clinicId)}/me/appointments${qs ? `?${qs}` : ""}`);
}
