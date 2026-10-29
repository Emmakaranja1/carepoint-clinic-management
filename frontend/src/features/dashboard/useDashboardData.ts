"use client";

import { useCallback, useState } from "react";

import { ApiError } from "@/lib/api";
import { cancelAppointment, myAppointments, type Appointment } from "@/features/appointments/api";
import { myClinics, type ClinicMembership } from "@/features/clinics/api";
import type { DoctorSearchResult } from "@/features/discovery/api";
import { loadDoctors } from "./doctorLookup";

function message(reason: unknown, fallback: string) {
  return reason instanceof ApiError ? reason.message : fallback;
}

function distinctDoctors(appointments: Appointment[]) {
  return new Set(appointments.filter((a) => a.status !== "CANCELLED").map((a) => a.doctorId)).size;
}

export function useDashboardData() {
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [doctors, setDoctors] = useState<Record<string, DoctorSearchResult>>({});
  const [memberships, setMemberships] = useState<ClinicMembership[]>([]);
  const [doctorTotal, setDoctorTotal] = useState<number | null>(null);
  const [recordCount, setRecordCount] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [workspaceError, setWorkspaceError] = useState("");
  const [cancelError, setCancelError] = useState("");
  const [cancellingId, setCancellingId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    setWorkspaceError("");
    const [upcoming, past, clinics] = await Promise.allSettled([
      myAppointments("upcoming"),
      myAppointments("past"),
      myClinics(),
    ]);
    if (upcoming.status === "fulfilled") {
      setAppointments(upcoming.value);
      setDoctors(await loadDoctors(upcoming.value));
    } else {
      setError(message(upcoming.reason, "Could not load your appointments"));
    }
    if (past.status === "fulfilled") {
      setRecordCount(past.value.filter((a) => a.status === "COMPLETED").length);
    }
    if (upcoming.status === "fulfilled" && past.status === "fulfilled") {
      setDoctorTotal(distinctDoctors([...upcoming.value, ...past.value]));
    }
    if (clinics.status === "fulfilled") {
      setMemberships(clinics.value);
    } else {
      setWorkspaceError(message(clinics.reason, "Could not load clinic workspaces"));
    }
    setLoading(false);
  }, []);

  const cancel = useCallback(async (id: string) => {
    setCancellingId(id);
    setCancelError("");
    try {
      const updated = await cancelAppointment(id);
      setAppointments((current) => current.map((item) => item.id === id ? updated : item));
    } catch (e) {
      setCancelError(message(e, "Could not cancel this appointment"));
    } finally {
      setCancellingId(null);
    }
  }, []);

  return {
    appointments, doctors, memberships, doctorTotal, recordCount,
    loading, error, workspaceError, cancelError, cancellingId, load, cancel,
  };
}
