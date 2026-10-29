import { api } from "@/lib/api";

export interface Appointment {
  id: string;
  clinicId: string;
  doctorId: string;
  patientId: string;
  status: "BOOKED" | "CANCELLED" | "COMPLETED" | "NO_SHOW" | string;
  start: string;
  end: string;
  reason: string | null;
  bookedForGuest: boolean;
  cancelledAt: string | null;
}

export function myAppointments(when: "upcoming" | "past") {
  return api<Appointment[]>(`/appointments/mine?when=${when}`);
}

export function cancelAppointment(id: string) {
  return api<Appointment>(`/appointments/${id}/cancel`, { method: "POST" });
}

export function bookAppointment(
  body: { clinicId: string; doctorId: string; start: string; end: string; reason: string | null },
  idempotencyKey: string,
) {
  return api<Appointment>("/appointments", { method: "POST", body, idempotencyKey });
}
