import type { ReactNode } from "react";

import type { AccessState } from "./format";
import { accessLabels } from "./format";

export const panelClass = "rounded-2xl border border-zinc-200/80 bg-white";

export const primaryButton =
  "inline-flex items-center justify-center gap-2 rounded-lg bg-navy px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-heading disabled:cursor-not-allowed disabled:opacity-60";

export const secondaryButton =
  "inline-flex items-center justify-center gap-2 rounded-lg border border-zinc-300 bg-white px-4 py-2.5 text-sm font-semibold text-heading transition hover:bg-zinc-50 disabled:cursor-not-allowed disabled:opacity-60";

export const dangerButton =
  "inline-flex items-center justify-center gap-2 rounded-lg border border-red-200 bg-white px-4 py-2.5 text-sm font-semibold text-red-700 transition hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-60";

export const fieldClass =
  "w-full rounded-lg border border-zinc-300 bg-white px-3.5 py-2.5 text-base text-heading outline-none transition " +
  "placeholder:text-zinc-400 focus:border-brand-500 focus:ring-4 focus:ring-brand-500/15 aria-[invalid=true]:border-red-400 sm:text-sm";

export function PageTitle({ eyebrow, title, description, actions }: {
  eyebrow?: string;
  title: string;
  description?: string;
  actions?: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        {eyebrow && <p className="text-xs font-semibold uppercase tracking-[0.14em] text-brass">{eyebrow}</p>}
        <h1 className="mt-1 font-display text-3xl font-semibold tracking-tight text-heading sm:text-[2.1rem]">{title}</h1>
        {description && <p className="mt-1.5 max-w-2xl text-sm text-zinc-500 sm:text-base">{description}</p>}
      </div>
      {actions && <div className="flex shrink-0 gap-2">{actions}</div>}
    </div>
  );
}

const badgeStyles: Record<AccessState, string> = {
  active: "bg-emerald-50 text-emerald-700 ring-emerald-600/15",
  pending: "bg-amber-50 text-amber-800 ring-amber-600/20",
  expired: "bg-orange-50 text-orange-800 ring-orange-600/20",
  disabled: "bg-zinc-100 text-zinc-600 ring-zinc-500/15",
};

const dotStyles: Record<AccessState, string> = {
  active: "bg-emerald-500",
  pending: "bg-amber-500",
  expired: "bg-orange-500",
  disabled: "bg-zinc-400",
};

export function AccessBadge({ state }: { state: AccessState }) {
  return (
    <span className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${badgeStyles[state]}`}>
      <span className={`h-1.5 w-1.5 rounded-full ${dotStyles[state]}`} aria-hidden="true" />
      {accessLabels[state]}
    </span>
  );
}

export function Notice({ tone, children }: { tone: "success" | "error" | "info"; children: ReactNode }) {
  const styles = {
    success: "border-emerald-200 bg-emerald-50 text-emerald-800",
    error: "border-red-200 bg-red-50 text-red-800",
    info: "border-brand-100 bg-brand-50 text-brand-700",
  }[tone];
  return (
    <div role={tone === "error" ? "alert" : "status"} className={`rounded-lg border px-4 py-3 text-sm ${styles}`}>
      {children}
    </div>
  );
}

export function SectionHeader({ title, action }: { title: string; action?: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3 border-b border-zinc-100 px-5 py-4">
      <h2 className="text-sm font-semibold text-heading">{title}</h2>
      {action}
    </div>
  );
}
