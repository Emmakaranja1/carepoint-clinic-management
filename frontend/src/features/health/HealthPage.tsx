"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";

import { ApiError } from "@/lib/api";
import { myAppointments, type Appointment } from "@/features/appointments/api";
import { AppointmentCard, appointmentWhen } from "@/features/dashboard/AppointmentCard";
import { Avatar } from "@/features/dashboard/Avatar";
import { loadDoctors, type DoctorMap } from "@/features/dashboard/doctorLookup";
import { StatCard } from "@/features/dashboard/StatCard";
import { cardClass, EmptyState, ErrorPanel, PageHeader, primaryButtonClass, Skeleton } from "@/features/dashboard/ui";
import { CalendarIcon, ClipboardIcon, DoctorBadgeIcon, DoctorsIcon, DocumentIcon, HeartShieldIcon } from "@/features/dashboard/icons";

interface History {
  upcoming: Appointment[];
  past: Appointment[];
}

function summarize({ upcoming, past }: History) {
  const visits = past.filter((a) => a.status === "COMPLETED");
  return {
    visits,
    missed: past.filter((a) => a.status === "NO_SHOW").length,
    seen: [...new Set(visits.map((a) => a.doctorId))],
    next: upcoming[0] ?? null,
  };
}

export function HealthPage() {
  const [history, setHistory] = useState<History | null>(null);
  const [doctors, setDoctors] = useState<DoctorMap>({});
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setError("");
    try {
      const [upcoming, past] = await Promise.all([myAppointments("upcoming"), myAppointments("past")]);
      setHistory({ upcoming, past });
      setDoctors(await loadDoctors([...upcoming, ...past]));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load your visit history");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const summary = useMemo(() => history && summarize(history), [history]);

  return (
    <>
      <PageHeader title="My Health" description="Your visit history and the doctors who have cared for you." />

      {error ? (
        <div className="mt-6"><ErrorPanel message={error} onRetry={() => void load()} /></div>
      ) : (
        <>
          <div className="mt-6 grid gap-5 sm:grid-cols-3">
            <StatCard label="Completed Visits" value={summary?.visits.length ?? null} icon={ClipboardIcon} badge={DocumentIcon} tone="teal" />
            <StatCard label="Doctors Seen" value={summary?.seen.length ?? null} icon={DoctorBadgeIcon} badge={DoctorsIcon} tone="indigo" />
            <StatCard label="Missed Visits" value={summary?.missed ?? null} icon={CalendarIcon} badge={HeartShieldIcon} tone="blue" />
          </div>

          <div className="mt-6 grid gap-6 xl:grid-cols-[minmax(0,1.7fr)_minmax(0,1fr)]">
            <section className={cardClass} aria-labelledby="visits-heading">
              <h2 id="visits-heading" className="text-lg font-bold text-heading">Visit history</h2>
              <div className="mt-5">
                {!summary ? (
                  <Skeleton rows={3} />
                ) : summary.visits.length === 0 ? (
                  <EmptyState
                    title="No completed visits yet"
                    description="Once a clinic marks your appointment as completed, it will appear here."
                  />
                ) : (
                  <div className="space-y-4">
                    {summary.visits.map((visit) => (
                      <AppointmentCard key={visit.id} appointment={visit} doctor={doctors[visit.doctorId]} detailed />
                    ))}
                  </div>
                )}
              </div>
            </section>

            <aside className="space-y-6">
              <section className={cardClass} aria-labelledby="next-heading">
                <h2 id="next-heading" className="text-lg font-bold text-heading">Next checkup</h2>
                {!summary ? (
                  <div className="mt-4"><Skeleton rows={1} className="h-16" /></div>
                ) : summary.next ? (
                  <p className="mt-3 text-base text-zinc-600">
                    {appointmentWhen(summary.next.start)}
                    {doctors[summary.next.doctorId] && ` with ${doctors[summary.next.doctorId].doctorName}`}
                  </p>
                ) : (
                  <>
                    <p className="mt-3 text-sm text-zinc-500">Nothing scheduled. Regular checkups help catch issues early.</p>
                    <Link href="/dashboard/doctors" className={`${primaryButtonClass} mt-4`}>Book a checkup</Link>
                  </>
                )}
              </section>

              <section className={cardClass} aria-labelledby="care-team-heading">
                <h2 id="care-team-heading" className="text-lg font-bold text-heading">Your care team</h2>
                {!summary ? (
                  <div className="mt-4"><Skeleton rows={2} className="h-12" /></div>
                ) : summary.seen.length === 0 ? (
                  <p className="mt-3 text-sm text-zinc-500">Doctors you visit will be listed here.</p>
                ) : (
                  <ul className="mt-4 space-y-3">
                    {summary.seen.map((id) => {
                      const doctor = doctors[id];
                      return (
                        <li key={id} className="flex items-center gap-3">
                          <Avatar name={doctor?.doctorName ?? "Doctor"} photoUrl={doctor?.photoUrl} size="sm" />
                          <div className="min-w-0 flex-1">
                            <p className="truncate text-sm font-semibold text-heading">{doctor?.doctorName ?? "Doctor"}</p>
                            <p className="truncate text-xs text-zinc-500">{doctor?.specialtyName}</p>
                          </div>
                          <Link href={`/dashboard/doctors/${id}`} className="text-sm font-semibold text-brand-600 hover:text-brand-700">
                            Book again
                          </Link>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </section>

              <p className="px-1 text-xs text-zinc-500">
                CarePoint keeps appointment history only. Diagnoses, prescriptions and clinical notes stay with your clinic.
              </p>
            </aside>
          </div>
        </>
      )}
    </>
  );
}
