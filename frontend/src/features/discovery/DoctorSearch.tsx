"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";

import { ApiError } from "@/lib/api";
import { Avatar } from "@/features/dashboard/Avatar";
import {
  listSpecialties,
  searchDoctors,
  type DoctorSearchPage,
  type DoctorSearchResult,
  type Specialty,
} from "./api";

const inputClass =
  "w-full rounded-xl border border-zinc-200 bg-white px-4 py-3 text-sm text-zinc-900 " +
  "outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-500/20";

const nextFormatter = new Intl.DateTimeFormat("en-US", { weekday: "short", month: "short", day: "numeric" });

function nextDate(value: string) {
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime()) ? value : nextFormatter.format(date);
}

function DoctorCard({ doctor }: { doctor: DoctorSearchResult }) {
  return (
    <article className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm">
      <div className="flex items-start gap-4">
        <Avatar name={doctor.doctorName} photoUrl={doctor.photoUrl} size="lg" />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <div>
              <h3 className="text-lg font-semibold text-heading">{doctor.doctorName}</h3>
              <p className="text-sm font-medium text-brand-600">{doctor.specialtyName}</p>
            </div>
            <span className="rounded-full bg-zinc-100 px-3 py-1 text-xs font-semibold text-zinc-600">
              {doctor.consultMinutes ? `${doctor.consultMinutes} min` : "Duration varies"}
            </span>
          </div>
          <p className="mt-3 line-clamp-2 text-sm text-zinc-600">
            {doctor.bio?.trim() || "Profile details coming soon."}
          </p>
          <div className="mt-4 flex flex-wrap gap-x-4 gap-y-1 text-sm text-zinc-500">
            <span>{doctor.clinicName}</span>
            {doctor.location && <span>{doctor.location}</span>}
            <span>{doctor.rating > 0 ? `${doctor.rating.toFixed(1)} rating` : "No reviews yet"}</span>
          </div>
          <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
            <span className="text-sm text-zinc-500">
              {doctor.nextAvailableSlot ? `Next available ${nextDate(doctor.nextAvailableSlot)}` : "No open slots listed"}
            </span>
            <Link
              href={`/dashboard/doctors/${doctor.doctorId}`}
              className="rounded-xl bg-brand-500 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-600"
            >
              Book appointment
            </Link>
          </div>
        </div>
      </div>
    </article>
  );
}

export function DoctorSearch({ initialQuery = "" }: { initialQuery?: string }) {
  const [query, setQuery] = useState(initialQuery);
  const [specialtyId, setSpecialtyId] = useState("");
  const [location, setLocation] = useState("");
  const [specialties, setSpecialties] = useState<Specialty[]>([]);
  const [page, setPage] = useState<DoctorSearchPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [specialtyError, setSpecialtyError] = useState("");

  const load = useCallback(async (filters: { query: string; specialtyId: string; location: string }) => {
    setLoading(true);
    setError("");
    try {
      setPage(await searchDoctors(filters));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load doctors");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load({ query: initialQuery, specialtyId: "", location: "" });
  }, [initialQuery, load]);

  useEffect(() => {
    listSpecialties()
      .then(setSpecialties)
      .catch(() => setSpecialtyError("Specialties could not be loaded"));
  }, []);

  const filters = { query, specialtyId, location };

  return (
    <section aria-labelledby="doctor-search-heading" className="mt-5 scroll-mt-4 rounded-2xl border border-brand-100/70 bg-white p-5 shadow-[0_2px_10px_rgba(1,25,77,0.04)] lg:p-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 id="doctor-search-heading" className="text-sm font-bold text-heading">Find a Doctor</h2>
          <p className="mt-1 text-sm text-zinc-500">
            Search doctors, specialties and locations across active clinics.
          </p>
        </div>
      </div>

      <form
        className="mt-6 grid gap-3 lg:grid-cols-[2fr_1fr_1fr_auto]"
        onSubmit={(event) => {
          event.preventDefault();
          void load(filters);
        }}
      >
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-zinc-700">Search</span>
          <input
            className={inputClass}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Doctor, specialty or condition"
          />
        </label>
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-zinc-700">Specialty</span>
          <select
            className={inputClass}
            value={specialtyId}
            onChange={(event) => setSpecialtyId(event.target.value)}
          >
            <option value="">All specialties</option>
            {specialties.map((specialty) => (
              <option key={specialty.id} value={specialty.id}>{specialty.name}</option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-zinc-700">Location</span>
          <input
            className={inputClass}
            value={location}
            onChange={(event) => setLocation(event.target.value)}
            placeholder="City or country"
          />
        </label>
        <button
          type="submit"
          disabled={loading}
          className="self-end rounded-xl bg-brand-500 px-7 py-3 text-sm font-semibold text-white hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {loading ? "Searching…" : "Search"}
        </button>
      </form>
      {specialtyError && <p className="mt-2 text-sm text-amber-700">{specialtyError}</p>}

      <div className="mt-6">
        {loading && !page ? (
          <div className="space-y-3" aria-label="Loading doctors">
            {[0, 1].map((item) => <div key={item} className="h-24 animate-pulse rounded-2xl bg-zinc-100" />)}
          </div>
        ) : error ? (
          <div className="rounded-2xl border border-red-200 bg-red-50 p-6">
            <h3 className="font-semibold text-red-800">Doctor search unavailable</h3>
            <p className="mt-1 text-sm text-red-700">{error}</p>
            <button
              type="button"
              onClick={() => void load(filters)}
              className="mt-4 rounded-lg bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-700"
            >
              Try again
            </button>
          </div>
        ) : !page || page.results.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-zinc-300 p-8 text-center">
            <h3 className="font-semibold text-heading">No doctors found</h3>
            <p className="mt-2 text-sm text-zinc-500">
              Try widening your filters. Doctor profiles and photos will appear here once clinics publish them.
            </p>
          </div>
        ) : (
          <>
            <p className="mb-3 text-sm text-zinc-500">
              {page.total} doctor{page.total === 1 ? "" : "s"} found
            </p>
            <div className="grid gap-4 xl:grid-cols-2">
              {page.results.map((doctor) => <DoctorCard key={doctor.doctorId} doctor={doctor} />)}
            </div>
          </>
        )}
      </div>
    </section>
  );
}
