import { api } from "@/lib/api";

export interface Specialty {
  id: string;
  name: string;
}

export interface DoctorSearchResult {
  doctorId: string;
  clinicId: string;
  clinicName: string;
  doctorName: string;
  specialtyName: string;
  bio: string | null;
  consultMinutes: number | null;
  location: string | null;
  rating: number;
  nextAvailableSlot: string | null;
  photoUrl: string | null;
}

export interface DoctorSearchPage {
  results: DoctorSearchResult[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
}

export function listSpecialties() {
  return api<Specialty[]>("/specialties");
}

export function getDoctor(doctorId: string) {
  return api<DoctorSearchResult>(`/search/doctors/${encodeURIComponent(doctorId)}`);
}

export function searchDoctors(params: {
  query?: string;
  specialtyId?: string;
  location?: string;
  page?: number;
  size?: number;
}) {
  const search = new URLSearchParams();
  if (params.query?.trim()) search.set("query", params.query.trim());
  if (params.specialtyId) search.set("specialtyId", params.specialtyId);
  if (params.location?.trim()) search.set("location", params.location.trim());
  search.set("page", String(params.page ?? 0));
  search.set("size", String(params.size ?? 12));
  return api<DoctorSearchPage>(`/search/doctors?${search}`);
}

export interface Slot {
  start: string;
  end: string;
}

export function getAvailability(clinicId: string, doctorId: string, from: string, to: string) {
  const search = new URLSearchParams({ from, to });
  return api<Slot[]>(
    `/clinics/${encodeURIComponent(clinicId)}/doctors/${encodeURIComponent(doctorId)}/availability?${search}`,
  );
}
