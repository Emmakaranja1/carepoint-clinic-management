"use client";

import { useEffect, useRef, useState } from "react";

import { BellIcon } from "./icons";

export function NotificationsButton() {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

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
        aria-label="Notifications"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        className="text-heading hover:text-brand-600"
      >
        <BellIcon />
      </button>
      {open && (
        <div role="dialog" aria-label="Notifications" className="absolute right-0 top-full z-30 mt-3 w-72 rounded-2xl border border-brand-100 bg-white p-5 shadow-lg">
          <p className="text-sm font-semibold text-heading">No notifications</p>
          <p className="mt-1 text-sm text-zinc-500">Appointment updates will show up here once notifications are available.</p>
        </div>
      )}
    </div>
  );
}
