"use client";

import type { ReactNode } from "react";
import type { FieldValues, Path, UseFormSetError } from "react-hook-form";

import { ApiError } from "@/lib/api";

/** Maps the backend {code,message,fields?} envelope onto RHF field errors. */
export function applyApiErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  setFormError: (msg: string) => void,
) {
  if (error instanceof ApiError) {
    const entries = Object.entries(error.fields ?? {});
    let mapped = 0;
    for (const [field, message] of entries) {
      setError(field as Path<T>, { message });
      mapped++;
    }
    if (mapped === 0) {
      setFormError(error.message);
    }
  } else {
    setFormError("Something went wrong — try again");
  }
}

export function Field({
  label,
  error,
  children,
}: {
  label: string;
  error?: string;
  children: ReactNode;
}) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block font-medium text-zinc-700 dark:text-zinc-300">
        {label}
      </span>
      {children}
      {error && <span className="mt-1 block text-xs text-red-600">{error}</span>}
    </label>
  );
}

export const inputClass =
  "w-full rounded-lg border border-zinc-300 bg-white px-3 py-2.5 text-sm " +
  "text-zinc-900 outline-none focus:border-brand-500 focus:ring-2 " +
  "focus:ring-brand-500/20 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-50";

export const submitClass =
  "w-full rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-semibold text-white " +
  "hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50";
