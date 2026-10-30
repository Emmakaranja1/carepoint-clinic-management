import { api } from "@/lib/api";

export interface SpringPage<T> {
  content: T[];
  totalElements: number;
}

export interface PublicClinic {
  id: string;
  name: string;
  slug: string;
  address?: Record<string, unknown> | null;
  logoUrl?: string | null;
}

export interface PublicDoctor {
  id: string;
  fullName: string;
  specialty: string;
  consultMinutes: number | null;
  photoUrl: string | null;
}

export function listPublicClinics(size = 8) {
  return api<SpringPage<PublicClinic>>(`/clinics?page=0&size=${size}`);
}

export function listPublicClinicDoctors(clinicId: string, size = 6) {
  return api<SpringPage<PublicDoctor>>(
    `/clinics/${encodeURIComponent(clinicId)}/doctors?page=0&size=${size}`,
  );
}

export function clinicCity(clinic: PublicClinic) {
  const address = clinic.address;
  if (!address) return null;
  const city = address.city ?? address.town ?? address.locality;
  return typeof city === "string" && city.trim() ? city.trim() : null;
}
