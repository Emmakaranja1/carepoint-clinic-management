"use client";

import { useEffect, useState } from "react";
import Link from "next/link";

import { ApiError } from "@/lib/api";
import { Avatar } from "@/features/dashboard/Avatar";
import { Reveal } from "./Reveal";
import { SectionHeading } from "./SectionHeading";
import {
  clinicCity,
  listPublicClinicDoctors,
  listPublicClinics,
  type PublicClinic,
  type PublicDoctor,
} from "./publicCatalog";

interface Featured {
  doctor: PublicDoctor;
  clinic: PublicClinic;
}

function BookLink({ href, compact = false }: { href: string; compact?: boolean }) {
  return (
    <Link
      href={href}
      className={`inline-flex items-center rounded-full bg-navy px-5 text-sm font-semibold text-white transition hover:bg-brand-600 ${
        compact ? "min-h-10" : "min-h-11"
      }`}
    >
      Book
    </Link>
  );
}

export function FeaturedDoctors() {
  const [featured, setFeatured] = useState<Featured[] | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const clinics = await listPublicClinics(6);
        const pages = await Promise.all(
          clinics.content.slice(0, 4).map(async (clinic) => {
            const doctors = await listPublicClinicDoctors(clinic.id, 4);
            return doctors.content.map((doctor) => ({ doctor, clinic }));
          }),
        );
        const unique = new Map<string, Featured>();
        for (const item of pages.flat()) {
          if (!unique.has(item.doctor.id)) unique.set(item.doctor.id, item);
        }
        if (!cancelled) setFeatured([...unique.values()].slice(0, 3));
      } catch (e) {
        if (!cancelled) {
          setFeatured([]);
          setError(e instanceof ApiError ? e.message : "Doctors could not be loaded");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const lead = featured?.[0];
  const rest = featured?.slice(1) ?? [];

  return (
    <section className="bg-paper">
      <div className="page-container section-y">
        <Reveal className="flex flex-wrap items-end justify-between gap-6">
          <SectionHeading
            eyebrow="Clinicians"
            title="Doctors already taking appointments."
            description="Profiles come from live clinic catalogs. Book a slot once you have an account — no phone tag with the front desk."
          />
          <Link
            href="/dashboard/doctors"
            className="inline-flex items-center gap-2 rounded-full border border-navy/80 px-5 py-2.5 text-sm font-semibold text-heading transition hover:bg-navy hover:text-white"
          >
            Search the full list <span aria-hidden="true">→</span>
          </Link>
        </Reveal>

        <div className="mt-12">
          {featured === null ? (
            <div className="grid gap-4 lg:grid-cols-12" aria-label="Loading doctors">
              <div className="h-72 animate-pulse rounded-2xl bg-white/70 lg:col-span-7" />
              <div className="h-72 animate-pulse rounded-2xl bg-white/70 lg:col-span-5" />
            </div>
          ) : featured.length === 0 ? (
            <Reveal>
              <div className="rounded-2xl border border-dashed border-navy/20 bg-white/70 px-8 py-14 text-center">
                <p className="font-display text-2xl text-heading">Specialists will appear here</p>
                <p className="mx-auto mt-3 max-w-md text-zinc-500">
                  {error || "Partner clinics publish their doctors to this list. Search still works while catalogs are filling in."}
                </p>
                <Link
                  href="/dashboard/doctors"
                  className="mt-6 inline-flex min-h-11 items-center rounded-full bg-brand-500 px-5 text-sm font-semibold text-white hover:bg-brand-600"
                >
                  Open doctor search
                </Link>
              </div>
            </Reveal>
          ) : (
            <div className="grid gap-4 lg:grid-cols-12 lg:gap-5">
              {lead && (
                <Reveal className={rest.length ? "lg:col-span-7" : "lg:col-span-12"}>
                  <article className="flex h-full flex-col justify-between rounded-2xl border border-navy/10 bg-white p-7 sm:p-8 xl:p-10">
                    <div className="flex items-start gap-5">
                      <Avatar name={lead.doctor.fullName} photoUrl={lead.doctor.photoUrl} size="xl" />
                      <div className="min-w-0">
                        <p className="text-[0.68rem] font-semibold uppercase tracking-[0.16em] text-brass">
                          {lead.doctor.specialty}
                        </p>
                        <h3 className="mt-1 font-display text-3xl font-semibold tracking-tight text-heading">
                          {lead.doctor.fullName}
                        </h3>
                        <p className="mt-2 text-sm text-ink/70">
                          {lead.clinic.name}
                          {clinicCity(lead.clinic) ? ` · ${clinicCity(lead.clinic)}` : ""}
                        </p>
                      </div>
                    </div>
                    <div className="mt-8 flex flex-wrap items-end justify-between gap-4">
                      <p className="text-sm text-zinc-500">
                        {lead.doctor.consultMinutes
                          ? `${lead.doctor.consultMinutes} min consult`
                          : "Visit length set by clinic"}
                      </p>
                      <BookLink href={`/dashboard/doctors/${lead.doctor.id}`} />
                    </div>
                  </article>
                </Reveal>
              )}
              <ul className="grid gap-4 md:grid-cols-2 lg:col-span-5 lg:grid-cols-1 lg:gap-5">
                {rest.map((item, index) => (
                  <li key={item.doctor.id}>
                    <Reveal delayMs={80 + index * 40} className="h-full">
                      <article className="flex h-full items-center justify-between gap-4 rounded-2xl border border-navy/10 bg-white px-5 py-5">
                        <div className="flex min-w-0 items-center gap-4">
                          <Avatar name={item.doctor.fullName} photoUrl={item.doctor.photoUrl} size="lg" />
                          <div className="min-w-0">
                            <h3 className="truncate font-semibold text-heading">{item.doctor.fullName}</h3>
                            <p className="text-sm font-medium text-brand-600">{item.doctor.specialty}</p>
                            <p className="mt-0.5 truncate text-sm text-zinc-500">
                              {item.clinic.name}
                              {clinicCity(item.clinic) ? ` · ${clinicCity(item.clinic)}` : ""}
                            </p>
                          </div>
                        </div>
                        <BookLink href={`/dashboard/doctors/${item.doctor.id}`} compact />
                      </article>
                    </Reveal>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
