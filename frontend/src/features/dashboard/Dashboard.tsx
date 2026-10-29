"use client";

import { useEffect, useState } from "react";
import Link from "next/link";

import { useSession } from "@/features/auth/session";
import { AppointmentCard } from "./AppointmentCard";
import { QuickActions } from "./QuickActions";
import { StatCard } from "./StatCard";
import { cardClass, Skeleton } from "./ui";
import { useDashboardData } from "./useDashboardData";
import { CalendarIcon, ChevronRightIcon, ClipboardIcon, DoctorBadgeIcon, DocumentIcon, DoctorsIcon } from "./icons";

const PREVIEW_COUNT = 2;

function greeting(hour: number) {
  if (hour < 12) return "Good morning";
  return hour < 18 ? "Good afternoon" : "Good evening";
}

export function Dashboard() {
  const user = useSession((state) => state.user);
  const data = useDashboardData();
  const { load } = data;
  const [showAll, setShowAll] = useState(false);
  const [hour, setHour] = useState<number | null>(null);

  useEffect(() => {
    setHour(new Date().getHours());
    void load();
  }, [load]);

  const upcomingCount = data.appointments.filter((a) => a.status === "BOOKED").length;
  const visible = showAll ? data.appointments : data.appointments.slice(0, PREVIEW_COUNT);
  const firstName = user?.fullName.split(" ")[0] ?? "";

  return (
    <>
      <h1 id="dashboard-heading" className="text-3xl font-bold text-heading">Dashboard</h1>
      <p className="mt-6 text-2xl font-bold text-heading">
        {hour === null ? "Hello" : greeting(hour)}, {firstName}! <span aria-hidden="true">👋</span>
      </p>
      <p className="mt-1 text-base text-zinc-500">Here&apos;s your health overview for today.</p>

      <div className="mt-7 grid gap-5 sm:grid-cols-3">
        <StatCard label="Upcoming Appointments" value={data.loading ? null : upcomingCount} icon={CalendarIcon} badge={ClipboardIcon} tone="blue" />
        <StatCard label="Total Doctors" value={data.doctorTotal} icon={DoctorBadgeIcon} badge={DoctorsIcon} tone="indigo" />
        <StatCard label="Completed Visits" value={data.recordCount} icon={ClipboardIcon} badge={DocumentIcon} tone="teal" />
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-[minmax(0,1.7fr)_minmax(0,1fr)]">
        <section aria-labelledby="appointments-heading" className={cardClass}>
          <div className="mb-5 flex items-center justify-between">
            <h2 id="appointments-heading" className="text-lg font-bold text-heading">Upcoming Appointments</h2>
            {data.appointments.length > PREVIEW_COUNT ? (
              <button
                type="button"
                onClick={() => setShowAll((v) => !v)}
                className="flex items-center gap-0.5 text-sm font-semibold text-brand-600 hover:text-brand-700"
              >
                {showAll ? "Show less" : "View all"}
                <ChevronRightIcon className="h-4 w-4" />
              </button>
            ) : (
              <Link href="/dashboard/appointments" className="flex items-center gap-0.5 text-sm font-semibold text-brand-600 hover:text-brand-700">
                View all
                <ChevronRightIcon className="h-4 w-4" />
              </Link>
            )}
          </div>
          {data.cancelError && (
            <p role="alert" className="mb-3 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{data.cancelError}</p>
          )}
          {data.loading ? (
            <Skeleton />
          ) : data.error ? (
            <div className="rounded-xl border border-red-200 bg-red-50 p-5">
              <p className="text-base font-semibold text-red-800">{data.error}</p>
              <button
                type="button"
                onClick={() => void load()}
                className="mt-3 rounded-lg bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-700"
              >
                Try again
              </button>
            </div>
          ) : data.appointments.length === 0 ? (
            <div className="rounded-xl border border-dashed border-brand-100 p-8 text-center">
              <p className="text-base font-semibold text-heading">No upcoming appointments</p>
              <Link href="/dashboard/doctors" className="mt-2 inline-block text-sm font-semibold text-brand-600 hover:text-brand-700">
                Find a doctor to book a visit
              </Link>
            </div>
          ) : (
            <div className="space-y-4">
              {visible.map((appointment) => (
                <AppointmentCard
                  key={appointment.id}
                  appointment={appointment}
                  doctor={data.doctors[appointment.doctorId]}
                  onCancel={(id) => void data.cancel(id)}
                  cancelling={data.cancellingId === appointment.id}
                />
              ))}
            </div>
          )}
        </section>

        <QuickActions />
      </div>

      {(data.memberships.length > 0 || data.workspaceError) && (
        <section aria-labelledby="workspace-heading" className={`${cardClass} mt-5`}>
          <h2 id="workspace-heading" className="text-lg font-bold text-heading">Clinic Workspaces</h2>
          {data.workspaceError && <p role="alert" className="mt-3 text-sm text-red-700">{data.workspaceError}</p>}
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            {data.memberships.map((membership) => (
              <Link
                key={membership.membershipId}
                href={`/staff/${membership.clinicId}/agenda`}
                className="rounded-xl bg-brand-50 px-5 py-4 hover:bg-brand-100"
              >
                <p className="text-sm font-medium text-brand-600">{membership.role.replace("_", " ")}</p>
                <p className="text-base font-semibold text-heading">{membership.clinicName}</p>
              </Link>
            ))}
          </div>
        </section>
      )}
    </>
  );
}
