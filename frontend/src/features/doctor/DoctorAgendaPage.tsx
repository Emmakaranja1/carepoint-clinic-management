"use client";

import { useCallback, useEffect, useMemo, useState } from "react";

import { cardClass, EmptyState } from "@/features/dashboard/ui";
import { listDoctorAgenda, type DoctorAgendaEntry } from "./api";

const statusPalette: Record<string, string> = {
  BOOKED: "bg-brand-50 text-brand-600",
  COMPLETED: "bg-emerald-50 text-emerald-700",
  NO_SHOW: "bg-amber-50 text-amber-700",
  CANCELLED: "bg-red-50 text-red-700",
};

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("en-KE", {
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat("en-KE", {
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

export function DoctorAgendaPage({ clinicId }: { clinicId: string }) {
  const [entries, setEntries] = useState<DoctorAgendaEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const today = new Date();
      const date = today.toISOString().slice(0, 10);
      const data = await listDoctorAgenda(clinicId, { date });
      setEntries(data ?? []);
    } catch {
      setError("Could not load your agenda.");
    } finally {
      setLoading(false);
    }
  }, [clinicId]);

  useEffect(() => {
    void load();
  }, [load]);

  const summary = useMemo(() => ({
    total: entries.length,
    booked: entries.filter((e) => e.status === "BOOKED").length,
    completed: entries.filter((e) => e.status === "COMPLETED").length,
    noShow: entries.filter((e) => e.status === "NO_SHOW").length,
    cancelled: entries.filter((e) => e.status === "CANCELLED").length,
  }), [entries]);

  const upcoming = useMemo(
    () => [...entries]
      .filter((entry) => entry.status === "BOOKED")
      .sort((a, b) => new Date(a.start).getTime() - new Date(b.start).getTime()),
    [entries],
  );

  const nextPatient = upcoming[0];
  const totalMinutes = entries.reduce((sum, entry) => {
    const durationMinutes = Math.max(0, (new Date(entry.end).getTime() - new Date(entry.start).getTime()) / 60000);
    return sum + durationMinutes;
  }, 0);

  const weeklySlots = useMemo(() => {
    const days = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
    return days.map((day, index) => ({
      day,
      appointments: entries.filter((entry) => {
        const date = new Date(entry.start);
        return date.getDay() === ((index + 1) % 7 || 7);
      }).length,
      peak: index % 2 === 0,
    }));
  }, [entries]);

  return (
    <div className="space-y-6">
      <header id="today" className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <p className="text-sm font-semibold uppercase tracking-[0.16em] text-brand-600">Doctor</p>
          <h1 className="mt-2 text-3xl font-bold text-heading">My agenda</h1>
        </div>
        <div className="rounded-full bg-brand-50 px-3 py-1.5 text-sm font-medium text-brand-700">
          {new Date().toLocaleDateString("en-KE", { weekday: "long", month: "short", day: "numeric" })}
        </div>
      </header>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {[
          { label: "Booked today", value: summary.booked, tone: "blue" },
          { label: "Completed", value: summary.completed, tone: "green" },
          { label: "No-shows", value: summary.noShow, tone: "amber" },
          { label: "Total slots", value: summary.total, tone: "brand" },
        ].map((item) => (
          <div key={item.label} className={cardClass}>
            <p className="text-sm text-zinc-500">{item.label}</p>
            <p className="mt-3 text-3xl font-bold text-heading">{item.value}</p>
          </div>
        ))}
      </div>

      <div className="grid gap-6 xl:grid-cols-[1.25fr_0.75fr]">
        <section className={cardClass}>
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-sm font-semibold uppercase tracking-[0.14em] text-zinc-500">Next patient</p>
              <h2 className="mt-2 text-xl font-semibold text-heading">{nextPatient ? nextPatient.patientName : "No upcoming patient"}</h2>
            </div>
            {nextPatient && (
              <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${statusPalette[nextPatient.status] ?? "bg-zinc-100 text-zinc-700"}`}>
                {nextPatient.status.replace("_", " ")}
              </span>
            )}
          </div>

          {nextPatient ? (
            <div className="mt-5 space-y-3 rounded-xl border border-zinc-200 bg-zinc-50 p-4">
              <p className="text-sm text-zinc-600">{formatDateTime(nextPatient.start)} → {formatDateTime(nextPatient.end)}</p>
              {nextPatient.reason && <p className="text-sm text-zinc-500">Reason: {nextPatient.reason}</p>}
              <div className="flex items-center justify-between rounded-lg bg-white px-3 py-2 text-sm">
                <span className="text-zinc-500">Appointment length</span>
                <span className="font-semibold text-heading">{Math.round((new Date(nextPatient.end).getTime() - new Date(nextPatient.start).getTime()) / 60000)} min</span>
              </div>
            </div>
          ) : (
            <p className="mt-5 text-sm text-zinc-500">Your clinic is clear for the next opening in the schedule.</p>
          )}
        </section>

        <section id="schedule" className={cardClass}>
          <p className="text-sm font-semibold uppercase tracking-[0.14em] text-zinc-500">Schedule overview</p>
          <div className="mt-4 space-y-3">
            <div className="flex items-center justify-between rounded-xl bg-zinc-50 px-3 py-2">
              <span className="text-sm text-zinc-500">Booked visits</span>
              <span className="text-lg font-semibold text-heading">{summary.booked}</span>
            </div>
            <div className="flex items-center justify-between rounded-xl bg-zinc-50 px-3 py-2">
              <span className="text-sm text-zinc-500">Clinic load</span>
              <span className="text-lg font-semibold text-heading">{Math.round((summary.booked / Math.max(summary.total, 1)) * 100)}%</span>
            </div>
            <div className="flex items-center justify-between rounded-xl bg-zinc-50 px-3 py-2">
              <span className="text-sm text-zinc-500">Total time booked</span>
              <span className="text-lg font-semibold text-heading">{Math.round(totalMinutes / 60)}h</span>
            </div>
          </div>

          <div className="mt-5 grid grid-cols-6 gap-2">
            {weeklySlots.map((slot) => (
              <div key={slot.day} className="rounded-xl border border-zinc-200 bg-zinc-50 p-2 text-center">
                <p className="text-[0.65rem] font-semibold uppercase tracking-[0.14em] text-zinc-500">{slot.day}</p>
                <p className="mt-2 text-lg font-bold text-heading">{slot.appointments}</p>
                <div className={`mt-2 h-2 rounded-full ${slot.peak ? "bg-brand-500" : "bg-zinc-300"}`} />
              </div>
            ))}
          </div>
        </section>
      </div>

      {error && <p className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}

      {loading ? (
        <div className="space-y-3">
          <div className="h-16 animate-pulse rounded-xl bg-zinc-100" />
          <div className="h-16 animate-pulse rounded-xl bg-zinc-100" />
          <div className="h-16 animate-pulse rounded-xl bg-zinc-100" />
        </div>
      ) : entries.length === 0 ? (
        <EmptyState title="No appointments today" description="Your schedule is clear. The clinic admin can still add patients or invite more staff if needed." />
      ) : (
        <div className="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]">
          <section id="patients" className={cardClass}>
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-lg font-bold text-heading">Patient list</h2>
              <span className="text-sm text-zinc-500">{entries.length} visits</span>
            </div>

            <div className="mt-5 space-y-3">
              {entries.map((entry) => (
                <article key={entry.id} className="rounded-xl border border-zinc-200 bg-white p-4">
                  <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
                    <div className="space-y-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <p className="text-base font-semibold text-heading">{entry.patientName}</p>
                        <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${statusPalette[entry.status] ?? "bg-zinc-100 text-zinc-700"}`}>
                          {entry.status.replace("_", " ")}
                        </span>
                      </div>
                      <p className="text-sm text-zinc-600">{formatDateTime(entry.start)} — {formatTime(entry.end)}</p>
                      {entry.reason && <p className="text-sm text-zinc-500">Reason: {entry.reason}</p>}
                    </div>

                    <div className="rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 text-sm text-zinc-600">
                      {Math.round((new Date(entry.end).getTime() - new Date(entry.start).getTime()) / 60000)} min consultation
                    </div>
                  </div>
                </article>
              ))}
            </div>
          </section>

          <aside id="profile" className={cardClass}>
            <p className="text-sm font-semibold uppercase tracking-[0.14em] text-zinc-500">Clinic profile</p>
            <div className="mt-4 rounded-2xl border border-zinc-200 bg-zinc-50 p-4">
              <div className="flex items-center gap-3">
                <div className="flex h-12 w-12 items-center justify-center rounded-full bg-brand-100 text-lg font-bold text-brand-700">
                  Dr
                </div>
                <div>
                  <p className="font-semibold text-heading">Dr. Emma Nakato</p>
                  <p className="text-sm text-zinc-500">General Medicine</p>
                </div>
              </div>
              <ul className="mt-4 space-y-2 text-sm text-zinc-600">
                <li>• 14 patients booked this week</li>
                <li>• 5 follow-up reviews planned</li>
                <li>• Next opening: tomorrow, 10:00 AM</li>
              </ul>
            </div>
          </aside>
        </div>
      )}
    </div>
  );
}
