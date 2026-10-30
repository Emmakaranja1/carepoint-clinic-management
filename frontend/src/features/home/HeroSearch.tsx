"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";

import { listSpecialties, type Specialty } from "@/features/discovery/api";

const fieldClass =
  "w-full bg-transparent py-2.5 text-[0.95rem] text-heading outline-none " +
  "placeholder:text-zinc-400";

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4 text-brass" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      <circle cx="11" cy="11" r="6.5" />
      <path d="M16 16.5 20 20.5" strokeLinecap="round" />
    </svg>
  );
}

function PinIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4 text-brass" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      <path d="M12 21s7-5.2 7-11a7 7 0 1 0-14 0c0 5.8 7 11 7 11Z" />
      <circle cx="12" cy="10" r="2.2" />
    </svg>
  );
}

export function HeroSearch() {
  const router = useRouter();
  const [query, setQuery] = useState("");
  const [specialtyId, setSpecialtyId] = useState("");
  const [location, setLocation] = useState("");
  const [specialties, setSpecialties] = useState<Specialty[]>([]);

  useEffect(() => {
    listSpecialties()
      .then(setSpecialties)
      .catch(() => setSpecialties([]));
  }, []);

  const specialtyName = specialties.find((item) => item.id === specialtyId)?.name ?? "";

  return (
    <form
      role="search"
      aria-label="Find a doctor"
      className="border border-navy/10 bg-white p-3 shadow-[0_24px_50px_-28px_rgba(7,18,37,0.45)] sm:p-4"
      onSubmit={(event) => {
        event.preventDefault();
        const term = [query.trim(), specialtyName, location.trim()].filter(Boolean).join(" ");
        router.push(term ? `/dashboard/doctors?q=${encodeURIComponent(term)}` : "/dashboard/doctors");
      }}
    >
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[1.45fr_1fr_1fr_auto] lg:gap-0">
        <label className="block px-3 sm:col-span-2 lg:col-span-1 lg:border-r lg:border-navy/10">
          <span className="flex items-center gap-2 text-[0.68rem] font-semibold uppercase tracking-[0.16em] text-zinc-500">
            <SearchIcon />
            Doctor or condition
          </span>
          <input
            className={fieldClass}
            value={query}
            maxLength={200}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Mwangi, cardiology, migraine"
            autoComplete="off"
          />
        </label>
        <label className="block px-3 lg:border-r lg:border-navy/10">
          <span className="block text-[0.68rem] font-semibold uppercase tracking-[0.16em] text-zinc-500">
            Specialty
          </span>
          <select
            className={`${fieldClass} cursor-pointer appearance-none bg-[length:12px] bg-[right_0.25rem_center] bg-no-repeat pr-7`}
            style={{
              backgroundImage:
                "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='8' fill='none'%3E%3Cpath stroke='%238a5a32' stroke-width='1.6' d='m1 1 5 5 5-5'/%3E%3C/svg%3E\")",
            }}
            value={specialtyId}
            onChange={(event) => setSpecialtyId(event.target.value)}
          >
            <option value="">All specialties</option>
            {specialties.map((specialty) => (
              <option key={specialty.id} value={specialty.id}>
                {specialty.name}
              </option>
            ))}
          </select>
        </label>
        <label className="block px-3">
          <span className="flex items-center gap-2 text-[0.68rem] font-semibold uppercase tracking-[0.16em] text-zinc-500">
            <PinIcon />
            Location
          </span>
          <input
            className={fieldClass}
            value={location}
            maxLength={120}
            onChange={(event) => setLocation(event.target.value)}
            placeholder="Nairobi, Kilimani…"
            autoComplete="off"
          />
        </label>
        <button
          type="submit"
          className="min-h-12 bg-brand-500 px-8 sm:col-span-2 lg:col-span-1 text-sm font-semibold text-white transition hover:bg-brand-600 lg:min-h-0 lg:self-stretch"
        >
          Search
        </button>
      </div>
    </form>
  );
}
