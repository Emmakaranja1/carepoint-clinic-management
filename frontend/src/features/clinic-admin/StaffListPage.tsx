"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";

import { Avatar } from "@/features/dashboard/Avatar";
import { ChevronRightIcon, SearchIcon } from "@/features/dashboard/icons";
import { ErrorPanel, Skeleton } from "@/features/dashboard/ui";
import { listStaff, type MembershipStatus, type StaffMember, type StaffRole } from "./api";
import { accessState, formatDate, roleLabel, roleLabels } from "./format";
import { PlusIcon } from "./icons";
import { AccessBadge, PageTitle, fieldClass, panelClass, primaryButton, secondaryButton } from "./ui";
import { useLoad } from "./useLoad";
import { useAdminWorkspace } from "./workspace";

const PAGE_SIZE = 20;
const ROLES = Object.keys(roleLabels) as StaffRole[];

export function StaffListPage() {
  return (
    <Suspense fallback={<Skeleton rows={3} />}>
      <StaffList />
    </Suspense>
  );
}

function StaffList() {
  const { clinic } = useAdminWorkspace();
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();

  const role = (ROLES as string[]).includes(params.get("role") ?? "") ? (params.get("role") as StaffRole) : "";
  const status = ["ACTIVE", "DISABLED"].includes(params.get("status") ?? "") ? (params.get("status") as MembershipStatus) : "";
  const q = params.get("q") ?? "";
  const page = Math.max(0, Number(params.get("page")) || 0);
  const [search, setSearch] = useState(q);

  const update = (next: Record<string, string | number>) => {
    const merged = new URLSearchParams(params);
    for (const [k, v] of Object.entries(next)) {
      if (v === "" || v === 0) merged.delete(k);
      else merged.set(k, String(v));
    }
    router.replace(`${pathname}${merged.size ? `?${merged}` : ""}`, { scroll: false });
  };

  useEffect(() => {
    if (search === q) return;
    const t = setTimeout(() => update({ q: search.trim(), page: 0 }), 300);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- debounce on input only
  }, [search]);

  const staff = useLoad(
    () => listStaff(clinic.clinicId, { q, role, status, page, size: PAGE_SIZE }),
    [clinic.clinicId, q, role, status, page],
  );
  const data = staff.data;
  const filtered = Boolean(q || role || status);
  const lastPage = data ? Math.max(0, Math.ceil(data.totalElements / PAGE_SIZE) - 1) : 0;

  return (
    <div className="space-y-6">
      <PageTitle
        eyebrow="Team"
        title="Staff"
        description="Everyone with access to this clinic's workspace. Invite receptionists and control who can sign in."
        actions={
          <Link href="/clinic-admin/staff/new" className={primaryButton}>
            <PlusIcon className="h-4 w-4" />
            Add staff
          </Link>
        }
      />

      <div className={panelClass}>
        <div className="flex flex-col gap-3 border-b border-zinc-100 p-4 md:flex-row md:items-center">
          <label className="relative block flex-1">
            <span className="sr-only">Search staff</span>
            <SearchIcon className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" />
            <input
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search by name or email"
              className={`${fieldClass} pl-9`}
              maxLength={100}
            />
          </label>
          <div className="grid grid-cols-2 gap-3 md:flex">
            <select aria-label="Filter by role" value={role} onChange={(e) => update({ role: e.target.value, page: 0 })} className={`${fieldClass} md:w-48`}>
              <option value="">All roles</option>
              {ROLES.map((r) => <option key={r} value={r}>{roleLabel(r)}</option>)}
            </select>
            <select aria-label="Filter by status" value={status} onChange={(e) => update({ status: e.target.value, page: 0 })} className={`${fieldClass} md:w-40`}>
              <option value="">Any status</option>
              <option value="ACTIVE">Active</option>
              <option value="DISABLED">Deactivated</option>
            </select>
          </div>
        </div>

        {staff.error ? (
          <div className="p-5"><ErrorPanel message={staff.error} onRetry={staff.reload} /></div>
        ) : !data ? (
          <div className="p-5"><Skeleton rows={4} className="h-12" /></div>
        ) : data.content.length === 0 ? (
          <div className="px-5 py-14 text-center">
            <p className="font-medium text-heading">{filtered ? "No staff match these filters" : "No staff yet"}</p>
            <p className="mx-auto mt-1 max-w-sm text-sm text-zinc-500">
              {filtered ? "Try a different name, role or status." : "Invite your first receptionist to start running the front desk."}
            </p>
            {filtered ? (
              <button type="button" className={`${secondaryButton} mt-4`} onClick={() => { setSearch(""); update({ q: "", role: "", status: "", page: 0 }); }}>
                Clear filters
              </button>
            ) : (
              <Link href="/clinic-admin/staff/new" className={`${primaryButton} mt-4`}>Add staff</Link>
            )}
          </div>
        ) : (
          <div className={staff.loading ? "opacity-60 transition-opacity" : undefined}>
            <StaffTable rows={data.content} />
            <StaffCards rows={data.content} />
          </div>
        )}

        {data && data.totalElements > 0 && (
          <div className="flex items-center justify-between gap-3 border-t border-zinc-100 px-4 py-3 text-sm text-zinc-500">
            <span>
              {data.page * PAGE_SIZE + 1}–{data.page * PAGE_SIZE + data.content.length} of {data.totalElements}
            </span>
            <div className="flex gap-2">
              <button type="button" className={secondaryButton} disabled={page === 0} onClick={() => update({ page: page - 1 })}>Previous</button>
              <button type="button" className={secondaryButton} disabled={page >= lastPage} onClick={() => update({ page: page + 1 })}>Next</button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function StaffTable({ rows }: { rows: StaffMember[] }) {
  return (
    <table className="hidden w-full text-left text-sm md:table">
      <thead>
        <tr className="border-b border-zinc-100 text-xs uppercase tracking-wide text-zinc-500">
          <th scope="col" className="px-5 py-3 font-medium">Name</th>
          <th scope="col" className="px-4 py-3 font-medium">Role</th>
          <th scope="col" className="px-4 py-3 font-medium">Access</th>
          <th scope="col" className="hidden px-4 py-3 font-medium lg:table-cell">Phone</th>
          <th scope="col" className="hidden px-4 py-3 font-medium lg:table-cell">Added</th>
          <th scope="col" className="w-10 px-4 py-3"><span className="sr-only">Open</span></th>
        </tr>
      </thead>
      <tbody className="divide-y divide-zinc-100">
        {rows.map((m) => (
          <tr key={m.membershipId} className="group relative hover:bg-zinc-50/80">
            <td className="px-5 py-3">
              <div className="flex items-center gap-3">
                <Avatar name={m.fullName} size="sm" />
                <div className="min-w-0">
                  <Link href={`/clinic-admin/staff/${m.membershipId}`} className="font-medium text-heading after:absolute after:inset-0 focus:outline-none">
                    {m.fullName}
                  </Link>
                  <p className="truncate text-zinc-500">{m.email}</p>
                </div>
              </div>
            </td>
            <td className="px-4 py-3 text-zinc-700">{roleLabel(m.role)}</td>
            <td className="px-4 py-3"><AccessBadge state={accessState(m.status, m.invitation)} /></td>
            <td className="hidden px-4 py-3 tabular-nums text-zinc-600 lg:table-cell">{m.phone ?? "—"}</td>
            <td className="hidden px-4 py-3 text-zinc-600 lg:table-cell">{formatDate(m.joinedAt)}</td>
            <td className="px-4 py-3 text-zinc-400 group-hover:text-heading"><ChevronRightIcon className="h-4 w-4" /></td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function StaffCards({ rows }: { rows: StaffMember[] }) {
  return (
    <ul className="divide-y divide-zinc-100 md:hidden">
      {rows.map((m) => (
        <li key={m.membershipId}>
          <Link href={`/clinic-admin/staff/${m.membershipId}`} className="flex items-center gap-3 px-4 py-4 active:bg-zinc-50">
            <Avatar name={m.fullName} size="sm" />
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium text-heading">{m.fullName}</p>
              <p className="truncate text-sm text-zinc-500">{m.email}</p>
              <div className="mt-1.5 flex flex-wrap items-center gap-2">
                <span className="text-xs font-medium text-zinc-600">{roleLabel(m.role)}</span>
                <AccessBadge state={accessState(m.status, m.invitation)} />
              </div>
            </div>
            <ChevronRightIcon className="h-4 w-4 shrink-0 text-zinc-400" />
          </Link>
        </li>
      ))}
    </ul>
  );
}
