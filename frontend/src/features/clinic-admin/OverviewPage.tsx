"use client";

import Link from "next/link";

import { ErrorPanel, Skeleton } from "@/features/dashboard/ui";
import { getOverview, getSummary, type ClinicOverview, type SummaryReport } from "./api";
import { ActivityFeed } from "./ActivityFeed";
import { clinicToday, formatDate, percent, roleLabel } from "./format";
import { PlusIcon } from "./icons";
import { PageTitle, SectionHeader, panelClass, primaryButton } from "./ui";
import { useLoad } from "./useLoad";
import { useAdminWorkspace } from "./workspace";

export function OverviewPage() {
  const { clinic } = useAdminWorkspace();
  const overview = useLoad(() => getOverview(clinic.clinicId), [clinic.clinicId]);

  if (overview.error) return <ErrorPanel message={overview.error} onRetry={overview.reload} />;
  if (!overview.data) return <Skeleton rows={3} />;

  const { clinic: info } = overview.data;
  const today = clinicToday(info.timezone);

  return (
    <div className="space-y-6">
      <PageTitle
        eyebrow={formatDate(`${today}T12:00:00Z`)}
        title={info.name}
        description={`Operational overview · times shown in ${info.timezone.replace("_", " ")}`}
        actions={
          <Link href="/clinic-admin/staff/new" className={primaryButton}>
            <PlusIcon className="h-4 w-4" />
            Add staff
          </Link>
        }
      />
      <TodayStrip clinicId={info.id} today={today} />
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <MonthPanel clinicId={info.id} />
        </div>
        <div className="space-y-6">
          <StaffPanel overview={overview.data} />
          <ClinicPanel overview={overview.data} />
        </div>
      </div>
      <ActivityFeed clinicId={info.id} timeZone={info.timezone} />
    </div>
  );
}

function TodayStrip({ clinicId, today }: { clinicId: string; today: string }) {
  const summary = useLoad(() => getSummary(clinicId, today, today), [clinicId, today]);
  const s = summary.data?.byStatus;
  const cells = [
    { label: "Booked today", value: s?.BOOKED, tone: "text-heading" },
    { label: "Completed", value: s?.COMPLETED, tone: "text-emerald-700" },
    { label: "No-shows", value: s?.NO_SHOW, tone: "text-orange-700" },
    { label: "Cancelled", value: s?.CANCELLED, tone: "text-zinc-500" },
  ];

  return (
    <section aria-labelledby="today-heading" className={panelClass}>
      <h2 id="today-heading" className="sr-only">Today</h2>
      {summary.error ? (
        <div className="p-4"><ErrorPanel message={summary.error} onRetry={summary.reload} /></div>
      ) : (
        <dl className="grid grid-cols-2 divide-zinc-100 sm:grid-cols-4 sm:divide-x [&>div:nth-child(-n+2)]:border-b [&>div:nth-child(-n+2)]:border-zinc-100 sm:[&>div]:border-b-0">
          {cells.map((c) => (
            <div key={c.label} className="px-5 py-4">
              <dt className="text-xs font-medium uppercase tracking-wide text-zinc-500">{c.label}</dt>
              <dd className={`mt-1 font-display text-3xl font-semibold tabular-nums ${c.tone}`}>
                {summary.loading && c.value === undefined ? <span className="inline-block h-8 w-10 animate-pulse rounded bg-zinc-100" /> : c.value ?? 0}
              </dd>
            </div>
          ))}
        </dl>
      )}
    </section>
  );
}

function MonthPanel({ clinicId }: { clinicId: string }) {
  const summary = useLoad(() => getSummary(clinicId), [clinicId]);

  return (
    <section className={panelClass}>
      <SectionHeader
        title="Last 30 days"
        action={summary.data && (
          <span className="text-xs text-zinc-500">
            {formatDate(`${summary.data.from}T12:00:00Z`)} – {formatDate(`${summary.data.to}T12:00:00Z`)}
          </span>
        )}
      />
      {summary.error ? (
        <div className="p-5"><ErrorPanel message={summary.error} onRetry={summary.reload} /></div>
      ) : !summary.data ? (
        <div className="p-5"><Skeleton rows={2} className="h-16" /></div>
      ) : (
        <MonthBody report={summary.data} />
      )}
    </section>
  );
}

function MonthBody({ report }: { report: SummaryReport }) {
  const attended = report.byStatus.COMPLETED + report.byStatus.NO_SHOW;
  return (
    <div>
      <dl className="grid grid-cols-3 gap-4 px-5 py-5">
        <Metric label="Appointments" value={String(report.total)} />
        <Metric label="Completed" value={String(report.byStatus.COMPLETED)} />
        <Metric label="No-show rate" value={attended ? percent(report.noShowRate) : "—"} hint={attended ? undefined : "No finished visits"} />
      </dl>
      <div className="border-t border-zinc-100">
        <h3 className="px-5 pt-4 text-xs font-semibold uppercase tracking-wide text-zinc-500">Doctor utilisation</h3>
        {report.perDoctor.length === 0 ? (
          <p className="px-5 py-6 text-sm text-zinc-500">No doctors have schedules at this clinic yet.</p>
        ) : (
          <ul className="divide-y divide-zinc-100 px-5 pb-2">
            {report.perDoctor.map((d) => (
              <li key={d.doctorId} className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 gap-y-1.5 py-3 sm:grid-cols-[minmax(0,12rem)_1fr_auto]">
                <p className="truncate text-sm font-medium text-heading">{d.doctorName}</p>
                <p className="text-right text-sm tabular-nums text-zinc-600 sm:order-last">
                  {d.capacity ? percent(d.utilization) : "No schedule"}
                </p>
                <div className="col-span-2 h-2 overflow-hidden rounded-full bg-zinc-100 sm:col-span-1" aria-hidden="true">
                  <div className="h-full rounded-full bg-brand-500" style={{ width: `${Math.min(100, Math.round(d.utilization * 100))}%` }} />
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

function Metric({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="min-w-0">
      <dt className="truncate text-xs text-zinc-500">{label}</dt>
      <dd className="mt-1 font-display text-2xl font-semibold tabular-nums text-heading">{value}</dd>
      {hint && <p className="text-xs text-zinc-400">{hint}</p>}
    </div>
  );
}

function StaffPanel({ overview }: { overview: ClinicOverview }) {
  const { staff } = overview;
  const roles = ["CLINIC_ADMIN", "DOCTOR", "RECEPTIONIST"] as const;
  return (
    <section className={panelClass}>
      <SectionHeader
        title="Staff"
        action={<Link href="/clinic-admin/staff" className="text-sm font-medium text-brand-600 hover:underline">Manage</Link>}
      />
      <ul className="divide-y divide-zinc-100 px-5">
        {roles.map((role) => (
          <li key={role} className="flex items-center justify-between py-3 text-sm">
            <span className="text-zinc-600">{roleLabel(role)}s</span>
            <span className="font-semibold tabular-nums text-heading">{staff.activeByRole[role] ?? 0}</span>
          </li>
        ))}
      </ul>
      <div className="grid grid-cols-2 gap-px border-t border-zinc-100 bg-zinc-100 text-sm">
        <Link href="/clinic-admin/staff" className="bg-white px-5 py-3 hover:bg-zinc-50">
          <span className="block text-xs text-zinc-500">Pending invitations</span>
          <span className="font-semibold tabular-nums text-amber-700">{staff.pendingInvitations}</span>
        </Link>
        <Link href="/clinic-admin/staff?status=DISABLED" className="rounded-br-2xl bg-white px-5 py-3 hover:bg-zinc-50">
          <span className="block text-xs text-zinc-500">Deactivated</span>
          <span className="font-semibold tabular-nums text-zinc-600">{staff.disabled}</span>
        </Link>
      </div>
    </section>
  );
}

function ClinicPanel({ overview }: { overview: ClinicOverview }) {
  const { clinic } = overview;
  const rows = [
    ["Timezone", clinic.timezone.replace("_", " ")],
    ["Patient cancellation cutoff", `${clinic.cancellationCutoffHours} hours before`],
    ["Booking window", `${clinic.slotLookaheadDays} days ahead`],
    ["Public page", `/${clinic.slug}`],
  ];
  return (
    <section className={panelClass}>
      <SectionHeader title="Clinic settings" />
      <dl className="divide-y divide-zinc-100 px-5">
        {rows.map(([label, value]) => (
          <div key={label} className="flex items-baseline justify-between gap-4 py-3 text-sm">
            <dt className="text-zinc-500">{label}</dt>
            <dd className="truncate text-right font-medium text-heading">{value}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}
