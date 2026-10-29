import type { Appointment } from "@/features/appointments/api";
import { getDoctor, type DoctorSearchResult } from "@/features/discovery/api";

export type DoctorMap = Record<string, DoctorSearchResult>;

export async function loadDoctors(appointments: Appointment[], known: DoctorMap = {}): Promise<DoctorMap> {
  const ids = [...new Set(appointments.map((a) => a.doctorId))].filter((id) => !known[id]);
  const results = await Promise.allSettled(ids.map(getDoctor));
  const doctors: DoctorMap = { ...known };
  results.forEach((result, i) => {
    if (result.status === "fulfilled") doctors[ids[i]] = result.value;
  });
  return doctors;
}
