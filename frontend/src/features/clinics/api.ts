import { api } from "@/lib/api";

export interface ClinicMembership {
  membershipId: string;
  clinicId: string;
  clinicName: string;
  role: "DOCTOR" | "RECEPTIONIST" | "CLINIC_ADMIN" | string;
}

export function myClinics() {
  return api<ClinicMembership[]>("/clinics/mine");
}
