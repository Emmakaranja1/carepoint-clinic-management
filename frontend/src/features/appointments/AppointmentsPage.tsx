"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";

import { ApiError } from "@/lib/api";
import { AppointmentCard } from "@/features/dashboard/AppointmentCard";
import { loadDoctors, type DoctorMap } from "@/features/dashboard/doctorLookup";
import { cardClass, EmptyState, ErrorPanel, Notice, PageHeader, primaryButtonClass, Skeleton } from "@/features/dashboard/ui";
import { cancelAppointment, myAppointments, type Appointment } from "./api";

type Tab = "upcoming" | "past";

const tabs: { id: Tab; label: string }[] = [
  { id: "upcoming", label: "Upcoming" },
  { id: "past", label: "Past & cancelled" },
];

export function AppointmentsPage({ justBooked = false }: { justBooked?: boolean }) {
  const [tab, setTab] = useState<Tab>("upcoming");
  const [lists, setLists] = useState<Partial<Record<Tab, Appointment[]>>>({});
  const [doctors, setDoctors] = useState<DoctorMap>({});
  const doctorsRef = useRef<DoctorMap>({});
  const [error, setError] = useState("");
  const [cancelError, setCancelError] = useState("");
  const [cancellingId, setCancellingId] = useState<string | null>(null);

  const load = useCallback(async (which: Tab) => {
    setError("");
    try {
      const items = await myAppointments(which);
      setLists((current) => ({ ...current, [which]: items }));
      doctorsRef.current = await loadDoctors(items, doctorsRef.current);
      setDoctors(doctorsRef.current);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load your appointments");
    }
  }, []);

  useEffect(() => {
    void load(tab);
  }, [load, tab]);

  const cancel = async (id: string) => {
    setCancellingId(id);
    setCancelError("");
    try {
      await cancelAppointment(id);
      setLists((current) => ({ upcoming: current.upcoming?.filter((a) => a.id !== id) }));
    } catch (e) {
      setCancelError(e instanceof ApiError ? e.message : "Could not cancel this appointment");
    } finally {
      setCancellingId(null);
    }
  };

  const items = lists[tab];

  return (
    <>
      <PageHeader
        title="Appointments"
        description="Your upcoming visits and appointment history."
        actions={<Link href="/dashboard/doctors" className={primaryButtonClass}>Book appointment</Link>}
      />

      <div className="mt-6 space-y-3">
        {justBooked && <Notice>Your appointment is booked. You&apos;ll find it under Upcoming.</Notice>}
        {cancelError && <Notice tone="error">{cancelError}</Notice>}
      </div>

      <section className={`${cardClass} mt-6`} aria-label="Appointments">
        <div role="tablist" aria-label="Appointment lists" className="mb-6 inline-flex rounded-xl bg-brand-50 p-1">
          {tabs.map(({ id, label }) => (
            <button
              key={id}
              type="button"
              role="tab"
              aria-selected={tab === id}
              onClick={() => setTab(id)}
              className={`rounded-lg px-4 py-2 text-sm font-semibold transition ${tab === id ? "bg-white text-brand-600 shadow-sm" : "text-zinc-600 hover:text-heading"}`}
            >
              {label}
            </button>
          ))}
        </div>

        <div role="tabpanel">
          {error ? (
            <ErrorPanel message={error} onRetry={() => void load(tab)} />
          ) : !items ? (
            <Skeleton rows={3} />
          ) : items.length === 0 ? (
            <EmptyState
              title={tab === "upcoming" ? "No upcoming appointments" : "No past appointments yet"}
              description={tab === "upcoming" ? "Find a doctor and pick an available slot to book your next visit." : "Completed, missed and cancelled visits will appear here."}
              action={tab === "upcoming" && <Link href="/dashboard/doctors" className={primaryButtonClass}>Find a doctor</Link>}
            />
          ) : (
            <div className="space-y-4">
              {items.map((appointment) => (
                <AppointmentCard
                  key={appointment.id}
                  appointment={appointment}
                  doctor={doctors[appointment.doctorId]}
                  onCancel={tab === "upcoming" ? (id) => void cancel(id) : undefined}
                  cancelling={cancellingId === appointment.id}
                  detailed
                />
              ))}
            </div>
          )}
        </div>
      </section>
    </>
  );
}
