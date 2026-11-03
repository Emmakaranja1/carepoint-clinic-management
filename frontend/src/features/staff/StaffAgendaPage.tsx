"use client";

import { useCallback, useEffect, useMemo, useState } from "react";

import { cardClass, EmptyState, primaryButtonClass, secondaryButtonClass } from "@/features/dashboard/ui";
import { bookOnBehalf, listStaffAgenda, transitionAppointment, type AgendaEntry } from "./api";

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

function dateString(date: Date) {
  return date.toISOString().slice(0, 10);
}

export function StaffAgendaPage({ clinicId }: { clinicId: string }) {
  const [entries, setEntries] = useState<AgendaEntry[]>([]);
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [doctorFilter, setDoctorFilter] = useState("ALL");
  const [bookingError, setBookingError] = useState("");
  const [bookingState, setBookingState] = useState({
    doctorId: "",
    patientName: "",
    patientEmail: "",
    reason: "",
    start: "",
    end: "",
  });

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const today = new Date();
      const from = dateString(new Date(today.getFullYear(), today.getMonth(), today.getDate()));
      const to = dateString(new Date(today.getFullYear(), today.getMonth(), today.getDate() + 1));
      const page = await listStaffAgenda(clinicId, { from, to, size: 100 });
      setEntries(page.content ?? []);
    } catch {
      setError("Could not load today's agenda");
    } finally {
      setLoading(false);
    }
  }, [clinicId]);

  useEffect(() => {
    void load();
  }, [load]);

  const doctors = useMemo(() => {
    return Array.from(new Map(entries.map((entry) => [entry.doctorId, entry.doctorName])).entries())
      .map(([doctorId, doctorName]) => ({ doctorId, doctorName }));
  }, [entries]);

  const filteredEntries = useMemo(() => {
    return entries.filter((entry) => {
      const doctorOk = doctorFilter === "ALL" || entry.doctorId === doctorFilter;
      const statusOk = statusFilter === "ALL" || entry.status === statusFilter;
      return doctorOk && statusOk;
    });
  }, [doctorFilter, entries, statusFilter]);

  const summary = useMemo(() => ({
    booked: entries.filter((e) => e.status === "BOOKED").length,
    completed: entries.filter((e) => e.status === "COMPLETED").length,
    noShow: entries.filter((e) => e.status === "NO_SHOW").length,
    cancelled: entries.filter((e) => e.status === "CANCELLED").length,
  }), [entries]);

  const updateStatus = async (id: string, status: "CANCELLED" | "COMPLETED" | "NO_SHOW") => {
    try {
      await transitionAppointment(clinicId, id, status);
      await load();
    } catch {
      setError("Could not update appointment status");
    }
  };

  const bookGuest = async () => {
    setBookingError("");
    if (!bookingState.doctorId || !bookingState.patientName || !bookingState.patientEmail || !bookingState.start || !bookingState.end) {
      setBookingError("Doctor, patient, and both times are required.");
      return;
    }

    try {
      await bookOnBehalf(clinicId, {
        doctorId: bookingState.doctorId,
        start: new Date(bookingState.start).toISOString(),
        end: new Date(bookingState.end).toISOString(),
        reason: bookingState.reason || null,
        patient: {
          email: bookingState.patientEmail,
          fullName: bookingState.patientName,
        },
      });
      setBookingState({
        doctorId: "",
        patientName: "",
        patientEmail: "",
        reason: "",
        start: "",
        end: "",
      });
      await load();
    } catch (e) {
      setBookingError(e instanceof Error ? e.message : "Could not create the booking");
    }
  };

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <p className="text-sm font-semibold uppercase tracking-[0.16em] text-brand-600">Front desk</p>
          <h1 className="mt-2 text-3xl font-bold text-heading">Today&apos;s agenda</h1>
        </div>
      </header>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {[{ label: "Booked", value: summary.booked, tone: "blue" }, { label: "Completed", value: summary.completed, tone: "green" }, { label: "No-shows", value: summary.noShow, tone: "amber" }, { label: "Cancelled", value: summary.cancelled, tone: "red" }].map((item) => (
          <div key={item.label} className={cardClass}>
            <p className="text-sm text-zinc-500">{item.label}</p>
            <p className="mt-3 text-3xl font-bold text-heading">{item.value}</p>
          </div>
        ))}
      </div>

      <section className={`${cardClass} space-y-4`}>
        <div className="flex items-center justify-between gap-4">
          <h2 className="text-lg font-bold text-heading">Quick booking</h2>
        </div>

        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
          <label className="block text-sm text-zinc-600">
            Doctor
            <select
              value={bookingState.doctorId}
              onChange={(event) => setBookingState((state) => ({ ...state, doctorId: event.target.value }))}
              className="mt-1 w-full rounded-lg border border-zinc-200 bg-white px-3 py-2.5 text-sm text-heading focus:border-brand-500"
            >
              <option value="">Select doctor</option>
              {doctors.map((doctor) => (
                <option key={doctor.doctorId} value={doctor.doctorId}>{doctor.doctorName}</option>
              ))}
            </select>
          </label>

          <label className="block text-sm text-zinc-600">
            Patient name
            <input
              value={bookingState.patientName}
              onChange={(event) => setBookingState((state) => ({ ...state, patientName: event.target.value }))}
              className="mt-1 w-full rounded-lg border border-zinc-200 bg-white px-3 py-2.5 text-sm text-heading focus:border-brand-500"
              placeholder="Jane Doe"
            />
          </label>

          <label className="block text-sm text-zinc-600">
            Patient email
            <input
              value={bookingState.patientEmail}
              onChange={(event) => setBookingState((state) => ({ ...state, patientEmail: event.target.value }))}
              className="mt-1 w-full rounded-lg border border-zinc-200 bg-white px-3 py-2.5 text-sm text-heading focus:border-brand-500"
              placeholder="jane@example.com"
            />
          </label>

          <label className="block text-sm text-zinc-600">
            Start
            <input
              type="datetime-local"
              value={bookingState.start}
              onChange={(event) => setBookingState((state) => ({ ...state, start: event.target.value }))}
              className="mt-1 w-full rounded-lg border border-zinc-200 bg-white px-3 py-2.5 text-sm text-heading focus:border-brand-500"
            />
          </label>

          <label className="block text-sm text-zinc-600">
            End
            <input
              type="datetime-local"
              value={bookingState.end}
              onChange={(event) => setBookingState((state) => ({ ...state, end: event.target.value }))}
              className="mt-1 w-full rounded-lg border border-zinc-200 bg-white px-3 py-2.5 text-sm text-heading focus:border-brand-500"
            />
          </label>
        </div>

        <div className="flex flex-col gap-3 md:flex-row md:items-end">
          <label className="block w-full text-sm text-zinc-600">
            Visit reason
            <input
              value={bookingState.reason}
              onChange={(event) => setBookingState((state) => ({ ...state, reason: event.target.value }))}
              className="mt-1 w-full rounded-lg border border-zinc-200 bg-white px-3 py-2.5 text-sm text-heading focus:border-brand-500"
              placeholder="Follow-up consultation"
            />
          </label>

          <button type="button" onClick={() => void bookGuest()} className={`${primaryButtonClass} min-w-[180px]`}>
            Book on behalf
          </button>
        </div>

        {bookingError && <p className="text-sm text-red-600">{bookingError}</p>}
      </section>

      <section className={`${cardClass} space-y-4`}>
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <h2 className="text-lg font-bold text-heading">Agenda</h2>

          <div className="flex flex-col gap-2 sm:flex-row">
            <select
              value={doctorFilter}
              onChange={(event) => setDoctorFilter(event.target.value)}
              className="rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm text-heading focus:border-brand-500"
            >
              <option value="ALL">All doctors</option>
              {doctors.map((doctor) => (
                <option key={doctor.doctorId} value={doctor.doctorId}>{doctor.doctorName}</option>
              ))}
            </select>

            <select
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value)}
              className="rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm text-heading focus:border-brand-500"
            >
              <option value="ALL">All statuses</option>
              <option value="BOOKED">Booked</option>
              <option value="COMPLETED">Completed</option>
              <option value="NO_SHOW">No show</option>
              <option value="CANCELLED">Cancelled</option>
            </select>
          </div>
        </div>

        {error && <p className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}

        {loading ? (
          <div className="space-y-3">
            <div className="h-16 animate-pulse rounded-xl bg-zinc-100" />
            <div className="h-16 animate-pulse rounded-xl bg-zinc-100" />
            <div className="h-16 animate-pulse rounded-xl bg-zinc-100" />
          </div>
        ) : filteredEntries.length === 0 ? (
          <EmptyState title="No appointments in this view" description="Try another doctor or status, or create a walk-in booking above." />
        ) : (
          <div className="space-y-3">
            {filteredEntries.map((entry) => (
              <article key={entry.id} className="rounded-xl border border-zinc-200 bg-white p-4">
                <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
                  <div className="space-y-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="text-base font-semibold text-heading">{entry.patientName}</p>
                      <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${statusPalette[entry.status] ?? "bg-zinc-100 text-zinc-700"}`}>
                        {entry.status.replace("_", " ")}
                      </span>
                    </div>
                    <p className="text-sm text-zinc-500">{entry.doctorName}</p>
                    <p className="text-sm text-zinc-600">{formatDateTime(entry.start)} → {formatDateTime(entry.end)}</p>
                    {entry.reason && <p className="text-sm text-zinc-500">Reason: {entry.reason}</p>}
                  </div>

                  <div className="flex flex-wrap gap-2">
                    {entry.status === "BOOKED" && (
                      <>
                        <button type="button" onClick={() => void updateStatus(entry.id, "COMPLETED")} className={primaryButtonClass}>Mark complete</button>
                        <button type="button" onClick={() => void updateStatus(entry.id, "NO_SHOW")} className={secondaryButtonClass}>No-show</button>
                        <button type="button" onClick={() => void updateStatus(entry.id, "CANCELLED")} className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700 hover:bg-red-100">
                          Cancel
                        </button>
                      </>
                    )}
                  </div>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
