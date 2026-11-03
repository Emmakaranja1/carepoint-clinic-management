"use client";

import { useEffect, useState, type ReactNode } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";

import { Logo } from "@/components/Logo";
import { Avatar } from "@/features/dashboard/Avatar";
import { LogoutIcon } from "@/features/dashboard/icons";
import { CloseIcon, MenuIcon, OverviewIcon, StaffIcon } from "./icons";
import { useAdminWorkspace } from "./workspace";

const navItems = [
  { label: "Overview", href: "/clinic-admin/overview", icon: OverviewIcon },
  { label: "Staff", href: "/clinic-admin/staff", icon: StaffIcon },
];

function isActive(pathname: string, href: string) {
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function AdminShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const { clinic } = useAdminWorkspace();

  useEffect(() => setOpen(false), [pathname]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
    };
  }, [open]);

  return (
    <div className="flex min-h-screen bg-[#f4f6f9]">
      <aside className="sticky top-0 hidden h-screen w-64 shrink-0 lg:block">
        <Sidebar pathname={pathname} />
      </aside>

      {open && (
        <div className="fixed inset-0 z-40 lg:hidden" role="dialog" aria-modal="true" aria-label="Navigation">
          <button
            type="button"
            aria-label="Close navigation"
            className="absolute inset-0 bg-navy/50"
            onClick={() => setOpen(false)}
          />
          <div className="relative h-full w-72 max-w-[85vw] shadow-2xl">
            <Sidebar pathname={pathname} onClose={() => setOpen(false)} />
          </div>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-zinc-200/80 bg-white/90 px-4 backdrop-blur sm:px-6 lg:px-8">
          <button
            type="button"
            onClick={() => setOpen(true)}
            aria-label="Open navigation"
            className="-ml-1 rounded-lg p-2 text-zinc-600 hover:bg-zinc-100 lg:hidden"
          >
            <MenuIcon className="h-6 w-6" />
          </button>
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-heading">{clinic.clinicName}</p>
            <p className="hidden text-xs text-zinc-500 sm:block">Clinic administration</p>
          </div>
          <span className="ml-auto inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-medium text-mint-foreground">
            <span className="h-1.5 w-1.5 rounded-full bg-mint-foreground" aria-hidden="true" />
            Secure session
          </span>
        </header>
        <main className="flex-1 px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
          <div className="mx-auto w-full max-w-6xl">{children}</div>
        </main>
      </div>
    </div>
  );
}

function Sidebar({ pathname, onClose }: { pathname: string; onClose?: () => void }) {
  const { user, clinics, clinic, selectClinic, signOut } = useAdminWorkspace();

  return (
    <div className="flex h-full flex-col bg-navy px-4 py-5 text-white">
      <div className="flex items-center justify-between px-2">
        <Logo light href="/clinic-admin/overview" />
        {onClose && (
          <button type="button" onClick={onClose} aria-label="Close navigation" className="rounded-lg p-1.5 text-white/70 hover:bg-white/10">
            <CloseIcon className="h-5 w-5" />
          </button>
        )}
      </div>
      <p className="mt-1 px-2 text-[0.7rem] font-semibold uppercase tracking-[0.18em] text-white/40">Admin workspace</p>

      {clinics.length > 1 ? (
        <label className="mt-6 block px-2">
          <span className="text-xs text-white/50">Clinic</span>
          <select
            value={clinic.clinicId}
            onChange={(e) => selectClinic(e.target.value)}
            className="mt-1 w-full rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-sm text-white outline-none focus:border-brand-400"
          >
            {clinics.map((c) => (
              <option key={c.clinicId} value={c.clinicId} className="text-heading">
                {c.clinicName}
              </option>
            ))}
          </select>
        </label>
      ) : (
        <div className="mt-6 rounded-xl border border-white/10 bg-white/5 px-3 py-2.5">
          <p className="text-xs text-white/50">Clinic</p>
          <p className="truncate text-sm font-medium">{clinic.clinicName}</p>
        </div>
      )}

      <nav aria-label="Clinic administration" className="mt-6 space-y-1">
        {navItems.map(({ label, href, icon: NavIcon }) => {
          const active = isActive(pathname, href);
          return (
            <Link
              key={href}
              href={href}
              aria-current={active ? "page" : undefined}
              className={`flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition ${
                active ? "bg-white text-navy" : "text-white/70 hover:bg-white/10 hover:text-white"
              }`}
            >
              <NavIcon className={`h-5 w-5 ${active ? "text-brand-500" : ""}`} />
              {label}
            </Link>
          );
        })}
      </nav>

      <div className="mt-auto border-t border-white/10 pt-4">
        <div className="flex items-center gap-3 px-2">
          <Avatar name={user.fullName} size="sm" />
          <div className="min-w-0">
            <p className="truncate text-sm font-medium">{user.fullName}</p>
            <p className="truncate text-xs text-white/50">{user.email}</p>
          </div>
        </div>
        <button
          type="button"
          onClick={signOut}
          className="mt-3 flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-white/70 hover:bg-white/10 hover:text-white"
        >
          <LogoutIcon className="h-5 w-5" />
          Sign out
        </button>
      </div>
    </div>
  );
}
