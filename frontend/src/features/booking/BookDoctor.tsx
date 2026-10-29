"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";

import { ApiError } from "@/lib/api";
import { bookAppointment } from "@/features/appointments/api";
import { Avatar } from "@/features/dashboard/Avatar";
import { cardClass, EmptyState, ErrorPanel, Notice, primaryButtonClass, Skeleton } from "@/features/dashboard/ui";
import { getAvailability, getDoctor, type DoctorSearchResult, type Slot } from "@/features/discovery/api";
import { groupByDay, isoDate, SlotPicker, slotLabel } from "./SlotPicker";

const WINDOW_DAYS = 14;
const SLOT_CONFLICTS = new Set(["SLOT_TAKEN", "INVALID_SLOT"]);

function message(e: unknown, fallback: string) {
  return e instanceof ApiError ? e.message : fallback;
}

export function BookDoctor({ doctorId }: { doctorId: string }) {
  const router = useRouter();
  const [doctor, setDoctor] = useState<DoctorSearchResult | null>(null);
  const [slots, setSlots] = useState<Slot[] | null>(null);
  const [loadError, setLoadError] = useState("");
  const [day, setDay] = useState<string | null>(null);
  const [selected, setSelected] = useState<Slot | null>(null);
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [bookError, setBookError] = useState("");
  const idempotencyKey = useRef<string | null>(null);

  const loadSlots = useCallback(async (profile: DoctorSearchResult) => {
    const from = new Date();
    const to = new Date(from);
    to.setDate(to.getDate() + WINDOW_DAYS - 1);
    const available = await getAvailability(profile.clinicId, profile.doctorId, isoDate(from), isoDate(to));
    const upcoming = available.filter((slot) => new Date(slot.start).getTime() > Date.now());
    setSlots(upcoming);
    setDay((current) => current && upcoming.some((s) => isoDate(new Date(s.start)) === current)
      ? current
      : upcoming[0] ? isoDate(new Date(upcoming[0].start)) : null);
  }, []);

  const load = useCallback(async () => {
    setLoadError("");
    try {
      const profile = await getDoctor(doctorId);
      setDoctor(profile);
      await loadSlots(profile);
    } catch (e) {
      setLoadError(message(e, "Could not load this doctor"));
    }
  }, [doctorId, loadSlots]);

  useEffect(() => {
    void load();
  }, [load]);

  const days = useMemo(() => groupByDay(slots ?? []), [slots]);

  const select = (slot: Slot) => {
    setSelected(slot);
    setBookError("");
    idempotencyKey.current = crypto.randomUUID();
  };

  const book = async () => {
    if (!doctor || !selected || !idempotencyKey.current) return;
    setSubmitting(true);
    setBookError("");
    try {
      await bookAppointment({
        clinicId: doctor.clinicId,
        doctorId: doctor.doctorId,
        start: selected.start,
        end: selected.end,
        reason: reason.trim() || null,
      }, idempotencyKey.current);
      router.push("/dashboard/appointments?booked=1");
    } catch (e) {
      setBookError(message(e, "Could not book this slot"));
      if (e instanceof ApiError && SLOT_CONFLICTS.has(e.code)) {
        setSelected(null);
        void loadSlots(doctor).catch(() => undefined);
      }
      setSubmitting(false);
    }
  };

  if (loadError) return <ErrorPanel message={loadError} onRetry={() => void load()} />;
  if (!doctor) return <Skeleton rows={2} className="h-40" />;

  return (
    <>
      <Link href="/dashboard/doctors" className="text-sm font-semibold text-brand-600 hover:text-brand-700">
        ← Back to doctors
      </Link>

      <section className={`${cardClass} mt-4 flex flex-wrap items-start gap-5`}>
        <Avatar name={doctor.doctorName} photoUrl={doctor.photoUrl} size="lg" />
        <div className="min-w-0 flex-1">
          <h1 className="text-2xl font-bold text-heading">{doctor.doctorName}</h1>
          <p className="text-base font-medium text-brand-600">{doctor.specialtyName}</p>
          <p className="mt-3 text-sm text-zinc-600">{doctor.bio?.trim() || "Profile details coming soon."}</p>
          <div className="mt-4 flex flex-wrap gap-x-5 gap-y-1 text-sm text-zinc-500">
            <span>{doctor.clinicName}</span>
            {doctor.location && <span>{doctor.location}</span>}
            {doctor.consultMinutes && <span>{doctor.consultMinutes} min consultation</span>}
            <span>{doctor.rating > 0 ? `${doctor.rating.toFixed(1)} rating` : "No reviews yet"}</span>
          </div>
        </div>
      </section>

      <section className={`${cardClass} mt-6`} aria-labelledby="slots-heading">
        <h2 id="slots-heading" className="text-lg font-bold text-heading">Choose a time</h2>
        <p className="mt-1 text-sm text-zinc-500">Times are shown in your local time zone.</p>
        <div className="mt-5">
          {!slots ? (
            <Skeleton rows={1} className="h-20" />
          ) : slots.length === 0 ? (
            <EmptyState
              title="No open slots in the next two weeks"
              description="This doctor's schedule is full or not yet published. Try another doctor or check back later."
            />
          ) : (
            <SlotPicker days={days} day={day} onDay={setDay} selected={selected} onSelect={select} />
          )}
        </div>
      </section>

      {selected && (
        <section className={`${cardClass} mt-6`} aria-labelledby="confirm-heading">
          <h2 id="confirm-heading" className="text-lg font-bold text-heading">Confirm booking</h2>
          <p className="mt-1 text-base text-zinc-600">{slotLabel(selected)} with {doctor.doctorName}</p>
          <label className="mt-5 block">
            <span className="mb-1 block text-sm font-medium text-zinc-700">Reason for visit (optional)</span>
            <textarea
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              maxLength={500}
              rows={3}
              placeholder="e.g. Annual check-up"
              className="w-full rounded-xl border border-brand-100 px-4 py-3 text-sm text-zinc-900 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-500/20"
            />
            <span className="mt-1 block text-right text-xs text-zinc-400">{reason.length}/500</span>
          </label>
          <p className="text-xs text-zinc-500">Don&apos;t include medical details — CarePoint does not store clinical records.</p>
          {bookError && <div className="mt-4"><Notice tone="error">{bookError}</Notice></div>}
          <button type="button" onClick={() => void book()} disabled={submitting} className={`${primaryButtonClass} mt-5`}>
            {submitting ? "Booking…" : "Confirm booking"}
          </button>
        </section>
      )}
      {!selected && bookError && <div className="mt-4"><Notice tone="error">{bookError}</Notice></div>}
    </>
  );
}
