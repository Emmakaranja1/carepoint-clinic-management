"use client";

import { useCallback, useEffect, useState } from "react";

import { ErrorPanel } from "@/features/dashboard/ui";
import { getActivity, type ActivityItem } from "./api";
import { describeActivity, formatDateTime, relativeTime } from "./format";
import { SectionHeader, panelClass, secondaryButton } from "./ui";

const PAGE_SIZE = 8;

export function ActivityFeed({ clinicId, timeZone }: { clinicId: string; timeZone: string }) {
  const [items, setItems] = useState<ActivityItem[]>([]);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(
    (p: number) => {
      setLoading(true);
      setError("");
      getActivity(clinicId, p, PAGE_SIZE)
        .then((res) => {
          setItems((prev) => (p === 0 ? res.content : [...prev, ...res.content]));
          setTotal(res.totalElements);
          setPage(p);
        })
        .catch(() => setError("Activity couldn't be loaded."))
        .finally(() => setLoading(false));
    },
    [clinicId],
  );

  useEffect(() => load(0), [load]);

  return (
    <section className={panelClass}>
      <SectionHeader title="Recent staff activity" action={<span className="text-xs text-zinc-500">Audit trail</span>} />
      {error ? (
        <div className="p-5"><ErrorPanel message={error} onRetry={() => load(page)} /></div>
      ) : !loading && items.length === 0 ? (
        <p className="px-5 py-8 text-center text-sm text-zinc-500">
          No staff changes recorded yet. Invitations, activations and access changes will appear here.
        </p>
      ) : (
        <ol className="divide-y divide-zinc-100">
          {items.map((item) => (
            <li key={item.id} className="flex flex-col gap-0.5 px-5 py-3 sm:flex-row sm:items-center sm:justify-between sm:gap-6">
              <p className="text-sm text-heading">{describeActivity(item)}</p>
              <time
                dateTime={item.occurredAt}
                title={formatDateTime(item.occurredAt, timeZone)}
                className="shrink-0 text-xs text-zinc-500"
              >
                {relativeTime(item.occurredAt)}
              </time>
            </li>
          ))}
          {loading && <li className="px-5 py-3"><div className="h-5 w-2/3 animate-pulse rounded bg-zinc-100" /></li>}
        </ol>
      )}
      {items.length < total && !loading && (
        <div className="border-t border-zinc-100 px-5 py-3">
          <button type="button" className={secondaryButton} onClick={() => load(page + 1)}>Show older activity</button>
        </div>
      )}
    </section>
  );
}
