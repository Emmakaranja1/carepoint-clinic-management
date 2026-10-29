"use client";

import Link from "next/link";
import type { ReactNode } from "react";

import { Logo } from "@/components/Logo";
import type { SessionUser } from "@/features/auth/session";
import { Avatar } from "./Avatar";
import { focusHeaderSearch, HeaderSearch } from "./HeaderSearch";
import {
  BellIcon,
  CalendarIcon,
  DashboardIcon,
  DoctorsIcon,
  HeartShieldIcon,
  LogoutIcon,
  MessageIcon,
  SettingsIcon,
} from "./icons";

const navItems = [
  { label: "Dashboard", href: "#dashboard-heading", icon: DashboardIcon, active: true },
  { label: "Appointments", href: "#appointments-heading", icon: CalendarIcon },
  { label: "Doctors", icon: DoctorsIcon, onClick: focusHeaderSearch },
  { label: "My Health", icon: HeartShieldIcon },
  { label: "Messages", icon: MessageIcon },
  { label: "Settings", icon: SettingsIcon },
];

const itemClass = "flex w-full items-center gap-3 rounded-xl px-4 py-3 text-base font-medium transition";

function roleLabel(user: SessionUser | null) {
  const role = user && Object.values(user.clinicRoles)[0];
  return role ? role.replace("_", " ").toLowerCase().replace(/^\w/, (c) => c.toUpperCase()) : "Patient";
}

export function DashboardShell({ user, onSignOut, children }: {
  user: SessionUser | null;
  onSignOut: () => void;
  children: ReactNode;
}) {
  const name = user?.fullName ?? "";

  return (
    <div className="flex h-screen flex-col bg-gradient-to-br from-[#f7fbff] via-canvas to-[#e6f0fb]">
      <header className="z-20 flex shrink-0 items-center gap-4 bg-white/70 px-5 py-3 backdrop-blur lg:px-6">
        <div className="lg:w-[17rem]">
          <Logo />
        </div>
        <HeaderSearch />
        <div className="ml-auto flex items-center gap-5 md:ml-0">
          <button type="button" aria-label="Notifications" className="text-heading hover:text-brand-600">
            <BellIcon />
          </button>
          <Avatar name={name} size="sm" />
        </div>
      </header>

      <div className="flex min-h-0 flex-1">
        <aside className="hidden w-72 shrink-0 overflow-y-auto flex-col border-r border-brand-100/70 bg-white px-4 py-6 lg:flex">
          <div className="flex items-center gap-3 px-2">
            <Avatar name={name} />
            <div className="min-w-0">
              <p className="truncate text-base font-semibold text-heading">{name}</p>
              <p className="text-sm text-zinc-500">{roleLabel(user)}</p>
            </div>
          </div>

          <nav aria-label="Dashboard" className="mt-8 space-y-1">
            {navItems.map(({ label, href, icon: NavIcon, active, onClick }) => href ? (
              <Link
                key={label}
                href={href}
                aria-current={active ? "page" : undefined}
                className={`${itemClass} ${active ? "bg-brand-50 text-brand-600" : "text-zinc-600 hover:bg-zinc-50 hover:text-heading"}`}
              >
                <NavIcon className={`h-6 w-6 ${active ? "text-brand-500" : "text-zinc-500"}`} />
                {label}
              </Link>
            ) : (
              <button
                key={label}
                type="button"
                onClick={onClick}
                aria-disabled={onClick ? undefined : "true"}
                title={onClick ? undefined : "Coming soon"}
                className={`${itemClass} text-zinc-600 ${onClick ? "hover:bg-zinc-50 hover:text-heading" : "cursor-default"}`}
              >
                <NavIcon className="h-6 w-6 text-zinc-500" />
                {label}
              </button>
            ))}
          </nav>

          <button
            type="button"
            onClick={onSignOut}
            className={`${itemClass} mt-auto text-zinc-600 hover:bg-zinc-50 hover:text-heading`}
          >
            <LogoutIcon className="h-6 w-6 text-zinc-500" />
            Log Out
          </button>
        </aside>

        <main className="min-w-0 flex-1 overflow-y-auto scroll-smooth px-5 py-6 lg:px-8">
          <div className="mx-auto w-full max-w-6xl">
            <div className="mb-4 flex justify-end lg:hidden">
              <button
                type="button"
                onClick={onSignOut}
                className="rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm font-semibold text-zinc-700"
              >
                Log Out
              </button>
            </div>
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
