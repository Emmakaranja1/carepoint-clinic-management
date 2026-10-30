"use client";

import { useEffect, useState } from "react";
import Link from "next/link";

import { listSpecialties, type Specialty } from "@/features/discovery/api";
import { Reveal } from "./Reveal";
import { SectionHeading } from "./SectionHeading";
import { SpecialtyIcon } from "./SpecialtyIcon";

const fallback = [
  "Cardiology",
  "Dermatology",
  "Paediatrics",
  "General Practice",
  "Orthopaedics",
  "Obstetrics & Gynaecology",
  "Mental Health",
  "Dentistry",
];

const toItems = (prefix: string) => fallback.map((name, i) => ({ id: `${prefix}-${i}`, name }));
const searchHref = (name: string) => `/dashboard/doctors?q=${encodeURIComponent(name)}`;

function Arrow({ className = "h-3.5 w-3.5" }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="none" stroke="currentColor" strokeWidth="2.2" aria-hidden="true">
      <path d="M5 12h14M13 6l6 6-6 6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function FeaturedCard({ specialty }: { specialty: Specialty }) {
  return (
    <Link
      href={searchHref(specialty.name)}
      className="home-card group relative flex h-full min-h-[22rem] flex-col overflow-hidden rounded-2xl bg-navy p-7 text-white sm:p-8 xl:p-10"
    >
      <div className="flex items-start justify-between">
        <span className="flex h-12 w-12 items-center justify-center rounded-xl bg-white/[0.07] text-brand-300 ring-1 ring-white/10">
          <SpecialtyIcon name={specialty.name} className="h-6 w-6" />
        </span>
        <span className="inline-flex items-center gap-2 rounded-full bg-white/[0.07] px-3 py-1.5 text-xs font-semibold text-white/85 ring-1 ring-white/10">
          <span className="h-1.5 w-1.5 rounded-full bg-mint" />
          Live availability
        </span>
      </div>

      <svg viewBox="0 0 320 60" className="ecg-line mt-8 h-14 w-full text-brand-400" fill="none" aria-hidden="true">
        <path
          d="M0 34h80l8-6 8 6h28l10-30 12 52 10-30 6 8h40l8-14 8 14h92"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>

      <div className="mt-auto pt-8">
        <h3 className="font-display text-4xl font-semibold tracking-tight xl:text-5xl">{specialty.name}</h3>
        <p className="mt-3 max-w-sm text-[0.95rem] leading-relaxed text-white/65">
          Open the live catalog for this specialty and pick a clinic that already has a slot.
        </p>
        <span className="mt-7 inline-flex items-center gap-2 rounded-full bg-white px-5 py-2.5 text-sm font-semibold text-navy transition group-hover:bg-mint">
          Find a doctor
          <Arrow />
        </span>
      </div>
    </Link>
  );
}

function SpecialtyCard({ specialty }: { specialty: Specialty }) {
  return (
    <Link
      href={searchHref(specialty.name)}
      className="home-card group flex h-full items-center gap-4 rounded-2xl border border-navy/10 bg-white p-4 hover:border-brand-300 hover:shadow-[0_18px_36px_-24px_rgba(7,18,37,0.45)] xl:p-5"
    >
      <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-brass-soft text-brass transition group-hover:bg-brand-50 group-hover:text-brand-600">
        <SpecialtyIcon name={specialty.name} />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block font-semibold leading-snug text-heading">{specialty.name}</span>
        <span className="mt-0.5 block text-sm text-zinc-500">Browse doctors</span>
      </span>
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-canvas text-heading transition group-hover:bg-brand-500 group-hover:text-white">
        <Arrow />
      </span>
    </Link>
  );
}

export function Specialties() {
  const [items, setItems] = useState<Specialty[] | null>(null);

  useEffect(() => {
    listSpecialties()
      .then((list) => setItems(list.length ? list : toItems("f")))
      .catch(() => setItems(toItems("f")));
  }, []);

  const [featured, ...rest] = (items ?? toItems("s")).slice(0, 8);

  return (
    <section id="services" className="scroll-mt-24 bg-canvas">
      <div className="page-container section-y">
        <Reveal className="flex flex-wrap items-end justify-between gap-6">
          <SectionHeading
            eyebrow="Specialties"
            title="Start with what you need care for."
            description="Browse the way a receptionist would: by specialty first, then by the person who actually has time for you."
          />
          <Link
            href="/dashboard/doctors"
            className="group inline-flex items-center gap-2 rounded-full border border-navy/80 px-5 py-2.5 text-sm font-semibold text-heading transition hover:bg-navy hover:text-white"
          >
            View all doctors
            <Arrow className="h-3.5 w-3.5 transition group-hover:translate-x-0.5" />
          </Link>
        </Reveal>

        {featured && (
          <div className="mt-12 grid gap-4 lg:grid-cols-12 lg:gap-5">
            <Reveal className="lg:col-span-5">
              <FeaturedCard specialty={featured} />
            </Reveal>
            <ul className="grid gap-3 sm:grid-cols-2 lg:col-span-7 lg:gap-4">
              {rest.map((specialty, index) => (
                <li key={specialty.id}>
                  <Reveal delayMs={Math.min(index * 40, 200)} className="h-full">
                    <SpecialtyCard specialty={specialty} />
                  </Reveal>
                </li>
              ))}
              <li>
                <Reveal delayMs={240} className="h-full">
                  <Link
                    href="/dashboard/doctors"
                    className="group flex h-full items-center justify-between gap-4 rounded-2xl border border-dashed border-brass/50 bg-brass-soft/40 p-4 transition hover:border-brass hover:bg-brass-soft/70 xl:p-5"
                  >
                    <span>
                      <span className="block font-semibold text-brass">Not sure where to start?</span>
                      <span className="mt-0.5 block text-sm text-ink/70">See every doctor and filter by clinic</span>
                    </span>
                    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brass text-white transition group-hover:translate-x-0.5">
                      <Arrow />
                    </span>
                  </Link>
                </Reveal>
              </li>
            </ul>
          </div>
        )}
      </div>
    </section>
  );
}
