"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";

import { ApiError } from "@/lib/api";
import { searchDoctors, type DoctorSearchResult } from "@/features/discovery/api";
import { Avatar } from "./Avatar";
import { SearchIcon } from "./icons";

const HEADER_SEARCH_ID = "dashboard-search";
const MIN_CHARS = 2;
const DEBOUNCE_MS = 300;

type State =
  | { kind: "idle" }
  | { kind: "loading" }
  | { kind: "error"; message: string }
  | { kind: "done"; results: DoctorSearchResult[]; total: number };

export function HeaderSearch() {
  const router = useRouter();
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [state, setState] = useState<State>({ kind: "idle" });
  const requestId = useRef(0);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const term = query.trim();
    if (term.length < MIN_CHARS) {
      setState({ kind: "idle" });
      return;
    }
    const id = ++requestId.current;
    setState({ kind: "loading" });
    const timer = setTimeout(() => {
      searchDoctors({ query: term, size: 6 })
        .then((page) => id === requestId.current && setState({ kind: "done", results: page.results, total: page.total }))
        .catch((e) => id === requestId.current && setState({
          kind: "error",
          message: e instanceof ApiError ? e.message : "Search is unavailable",
        }));
    }, DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    const close = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, []);

  const showPanel = open && state.kind !== "idle";

  return (
    <div ref={rootRef} className="relative mx-auto hidden w-full max-w-md md:block">
      <form
        role="search"
        onSubmit={(event) => {
          event.preventDefault();
          setOpen(false);
          const term = query.trim();
          router.push(term ? `/dashboard/doctors?q=${encodeURIComponent(term)}` : "/dashboard/doctors");
        }}
      >
        <label className="relative block">
          <span className="sr-only">Search doctors and specialties</span>
          <SearchIcon className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" />
          <input
            id={HEADER_SEARCH_ID}
            type="search"
            autoComplete="off"
            value={query}
            maxLength={200}
            onChange={(event) => {
              setQuery(event.target.value);
              setOpen(true);
            }}
            onFocus={() => setOpen(true)}
            onKeyDown={(event) => event.key === "Escape" && setOpen(false)}
            placeholder="Search doctors, specialties..."
            className="w-full rounded-xl border border-brand-100 bg-canvas py-2.5 pl-11 pr-4 text-sm text-zinc-900 outline-none placeholder:text-zinc-400 focus:border-brand-500 focus:ring-2 focus:ring-brand-500/20"
          />
        </label>
      </form>

      {showPanel && (
        <div
          id="dashboard-search-results"
          role="region"
          aria-live="polite"
          className="absolute inset-x-0 top-full z-30 mt-2 overflow-hidden rounded-2xl border border-brand-100 bg-white shadow-lg"
        >
          {state.kind === "loading" && <p className="px-4 py-3 text-sm text-zinc-500">Searching…</p>}
          {state.kind === "error" && <p className="px-4 py-3 text-sm text-red-700">{state.message}</p>}
          {state.kind === "done" && state.results.length === 0 && (
            <p className="px-4 py-3 text-sm text-zinc-500">No doctors match “{query.trim()}”.</p>
          )}
          {state.kind === "done" && state.results.length > 0 && (
            <ul className="max-h-80 divide-y divide-brand-50 overflow-y-auto">
              {state.results.map((doctor) => (
                <li key={doctor.doctorId}>
                  <Link
                    href={`/dashboard/doctors/${doctor.doctorId}`}
                    onClick={() => setOpen(false)}
                    className="flex items-center gap-3 px-4 py-3 hover:bg-brand-50 focus:bg-brand-50 focus:outline-none"
                  >
                    <Avatar name={doctor.doctorName} photoUrl={doctor.photoUrl} size="sm" />
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold text-heading">{doctor.doctorName}</p>
                      <p className="truncate text-xs text-zinc-500">
                        {doctor.specialtyName} · {doctor.clinicName}
                      </p>
                    </div>
                  </Link>
                </li>
              ))}
            </ul>
          )}
          {state.kind === "done" && state.total > state.results.length && (
            <p className="border-t border-brand-50 px-4 py-2 text-xs text-zinc-500">
              Showing {state.results.length} of {state.total} — press Enter to see all
            </p>
          )}
        </div>
      )}
    </div>
  );
}
