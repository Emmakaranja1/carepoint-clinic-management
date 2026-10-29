import type { ReactNode } from "react";

export const cardClass =
  "rounded-2xl border border-brand-100/70 bg-white p-6 shadow-[0_2px_10px_rgba(1,25,77,0.04)]";

export const primaryButtonClass =
  "inline-flex items-center justify-center rounded-xl bg-brand-500 px-5 py-3 text-sm font-semibold text-white transition hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50";

export const secondaryButtonClass =
  "inline-flex items-center justify-center rounded-xl border border-brand-100 bg-white px-5 py-3 text-sm font-semibold text-heading transition hover:bg-brand-50 disabled:cursor-not-allowed disabled:opacity-50";

export function PageHeader({ title, description, actions }: {
  title: string;
  description?: string;
  actions?: ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-3xl font-bold text-heading">{title}</h1>
        {description && <p className="mt-2 text-base text-zinc-500">{description}</p>}
      </div>
      {actions}
    </div>
  );
}

export function Skeleton({ rows = 2, className = "h-28" }: { rows?: number; className?: string }) {
  return (
    <div className="space-y-3" aria-label="Loading">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className={`${className} animate-pulse rounded-2xl bg-zinc-100`} />
      ))}
    </div>
  );
}

export function ErrorPanel({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="rounded-xl border border-red-200 bg-red-50 p-5">
      <p className="text-base font-semibold text-red-800">{message}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-3 rounded-lg bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-700"
        >
          Try again
        </button>
      )}
    </div>
  );
}

export function EmptyState({ title, description, action }: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="rounded-xl border border-dashed border-brand-100 p-10 text-center">
      <p className="text-base font-semibold text-heading">{title}</p>
      {description && <p className="mx-auto mt-2 max-w-md text-sm text-zinc-500">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function Notice({ tone = "success", children }: { tone?: "success" | "error"; children: ReactNode }) {
  const styles = tone === "success" ? "bg-emerald-50 text-mint-foreground" : "bg-red-50 text-red-700";
  return (
    <p role={tone === "error" ? "alert" : "status"} className={`rounded-xl px-4 py-3 text-sm font-medium ${styles}`}>
      {children}
    </p>
  );
}
