"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";

import { Logo } from "@/components/Logo";
import type { SessionUser } from "@/features/auth/session";
import { Avatar } from "@/features/dashboard/Avatar";
import { CalendarIcon, LogoutIcon } from "@/features/dashboard/icons";
import type { ClinicMembership } from "@/features/clinics/api";

export function StaffShell({ user, clinic, onSignOut, children }: {
  user: SessionUser;
  clinic: ClinicMembership;
  onSignOut: () => void;
  children: ReactNode;
}) {
  const pathname = usePathname();
  const roleLabel = clinic.role === "CLINIC_ADMIN" ? "Clinic admin" : "Receptionist";

  return (
    <div className="flex min-h-screen bg-[#f4f6f9]">
      <aside className="sticky top-0 hidden h-screen w-64 shrink-0 bg-navy px-4 py-5 text-white lg:flex lg:flex-col">
        <div className="flex items-center gap-2 px-2">
          <Logo light href={`/staff/${clinic.clinicId}/agenda`} />
        </div>

        <div className="mt-6 rounded-xl border border-white/10 bg-white/5 px-3 py-2.5">
          <p className="text-[0.7rem] font-semibold uppercase tracking-[0.18em] text-white/40">Desk</p>
          <p className="truncate text-sm font-medium text-white">{clinic.clinicName}</p>
        </div>

        <nav aria-label="Front desk" className="mt-6 space-y-1">
          <Link
            href={`/staff/${clinic.clinicId}/agenda`}
            aria-current={pathname === `/staff/${clinic.clinicId}/agenda` ? "page" : undefined}
            className={`flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition ${
              pathname === `/staff/${clinic.clinicId}/agenda` ? "bg-white text-navy" : "text-white/70 hover:bg-white/10 hover:text-white"
            }`}
          >
            <CalendarIcon className="h-5 w-5" />
            Today&apos;s Agenda
          </Link>
        </nav>

        <div className="mt-auto border-t border-white/10 pt-4">
          <div className="flex items-center gap-3 px-2">
            <Avatar name={user.fullName} size="sm" />
            <div className="min-w-0">
              <p className="truncate text-sm font-medium">{user.fullName}</p>
              <p className="truncate text-xs text-white/50">{roleLabel}</p>
            </div>
          </div>

          <button
            type="button"
            onClick={onSignOut}
            className="mt-3 flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-white/70 hover:bg-white/10 hover:text-white"
          >
            <LogoutIcon className="h-5 w-5" />
            Sign out
          </button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-zinc-200/80 bg-white/90 px-4 backdrop-blur sm:px-6 lg:px-8">
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-heading">{clinic.clinicName}</p>
            <p className="hidden text-xs text-zinc-500 sm:block">Front desk workspace</p>
          </div>
          <span className="ml-auto inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-medium text-mint-foreground">
            <span className="h-1.5 w-1.5 rounded-full bg-mint-foreground" aria-hidden="true" />
            Active desk
          </span>
        </header>

        <main className="flex-1 px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
          <div className="mx-auto w-full max-w-6xl">{children}</div>
        </main>
      </div>
    </div>
  );
}
