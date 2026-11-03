"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";

import { Logo } from "@/components/Logo";
import type { SessionUser } from "@/features/auth/session";
import { Avatar } from "@/features/dashboard/Avatar";
import { CalendarIcon, LogoutIcon } from "@/features/dashboard/icons";
import type { ClinicMembership } from "@/features/clinics/api";

export function DoctorShell({ user, clinic, onSignOut, children }: {
  user: SessionUser;
  clinic: ClinicMembership;
  onSignOut: () => void;
  children: ReactNode;
}) {
  const pathname = usePathname();
  const basePath = `/doctor/${clinic.clinicId}/agenda`;
  const navItems = [
    { label: "Today", href: `${basePath}#today`, key: "today" },
    { label: "Patients", href: `${basePath}#patients`, key: "patients" },
    { label: "Schedule", href: `${basePath}#schedule`, key: "schedule" },
    { label: "Profile", href: `${basePath}#profile`, key: "profile" },
  ];

  return (
    <div className="flex min-h-screen bg-[#f4f6f9]">
      <aside className="sticky top-0 hidden h-screen w-64 shrink-0 bg-navy px-4 py-5 text-white lg:flex lg:flex-col">
        <div className="flex items-center gap-2 px-2">
          <Logo light href={basePath} />
        </div>

        <div className="mt-6 rounded-xl border border-white/10 bg-white/5 px-3 py-2.5">
          <p className="text-[0.7rem] font-semibold uppercase tracking-[0.18em] text-white/40">Doctor</p>
          <p className="truncate text-sm font-medium text-white">{clinic.clinicName}</p>
        </div>

        <nav aria-label="Doctor workspace" className="mt-6 space-y-1">
          {navItems.map((item) => (
            <Link
              key={item.key}
              href={item.href}
              aria-current={pathname === basePath ? "page" : undefined}
              className={`flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition ${
                pathname === basePath ? "bg-white text-navy" : "text-white/70 hover:bg-white/10 hover:text-white"
              }`}
            >
              <CalendarIcon className="h-5 w-5" />
              {item.label}
            </Link>
          ))}
        </nav>

        <div className="mt-auto border-t border-white/10 pt-4">
          <div className="flex items-center gap-3 px-2">
            <Avatar name={user.fullName} size="sm" />
            <div className="min-w-0">
              <p className="truncate text-sm font-medium">{user.fullName}</p>
              <p className="truncate text-xs text-white/50">Doctor</p>
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
            <p className="hidden text-xs text-zinc-500 sm:block">Doctor workspace</p>
          </div>
          <span className="ml-auto inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-medium text-mint-foreground">
            <span className="h-1.5 w-1.5 rounded-full bg-mint-foreground" aria-hidden="true" />
            Active
          </span>
        </header>

        <main className="flex-1 px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
          <div className="mx-auto w-full max-w-6xl">{children}</div>
        </main>
      </div>
    </div>
  );
}
