"use client";

import { useEffect, useId, useRef, useState } from "react";
import Link from "next/link";

import type { SessionUser } from "@/features/auth/session";

const links = [
  { label: "My Dashboard", href: "/dashboard" },
  { label: "My Appointments", href: "/dashboard#appointments-heading" },
];

const itemClass = "block w-full px-4 py-2.5 text-left text-sm text-zinc-700 hover:bg-brand-50 hover:text-brand-700 focus:bg-brand-50 focus:outline-none";

function firstName(fullName: string) {
  return fullName.trim().split(/[\s.]+/)[0] || "there";
}

export function AccountMenu({ user, onSignOut }: { user: SessionUser; onSignOut: () => void }) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const menuId = useId();

  useEffect(() => {
    if (!open) return;
    const onPointer = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const onKey = (event: KeyboardEvent) => event.key === "Escape" && setOpen(false);
    document.addEventListener("mousedown", onPointer);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onPointer);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={menuId}
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-2 rounded-lg px-2 py-2 font-medium text-zinc-700 hover:text-brand-600"
      >
        <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <circle cx="10" cy="8" r="4" />
          <path d="M3 20c.6-3.8 3.4-6 7-6 1.3 0 2.5.3 3.5.8M15 18l2 2 4-4" />
        </svg>
        <span>Hi, {firstName(user.fullName)}</span>
        <svg viewBox="0 0 24 24" className={`h-4 w-4 transition ${open ? "rotate-180" : ""}`} fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
          <path d="m6 9 6 6 6-6" />
        </svg>
      </button>

      {open && (
        <div
          id={menuId}
          role="menu"
          className="absolute right-0 top-full z-30 mt-2 w-60 overflow-hidden rounded-xl border border-zinc-200 bg-white py-1 shadow-lg"
        >
          <div className="border-b border-zinc-100 px-4 py-3">
            <p className="truncate text-sm font-semibold text-heading">{user.fullName}</p>
            <p className="truncate text-xs text-zinc-500">{user.email}</p>
          </div>
          {links.map(({ label, href }) => (
            <Link key={label} href={href} role="menuitem" className={itemClass} onClick={() => setOpen(false)}>
              {label}
            </Link>
          ))}
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              setOpen(false);
              onSignOut();
            }}
            className={`${itemClass} border-t border-zinc-100 text-red-600 hover:text-red-700`}
          >
            Sign out
          </button>
        </div>
      )}
    </div>
  );
}
