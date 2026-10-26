"use client";

import { useEffect } from "react";
import Link from "next/link";

import { Logo } from "@/components/Logo";
import { useSession } from "@/features/auth/session";

const navLinks = [
  ["Home", "/"],
  ["Find a Doctor", "/doctors"],
  ["Services", "/#services"],
  ["About", "/#about"],
  ["Contact", "/#contact"],
] as const;

export function Header() {
  const { user, status, hydrate, logout } = useSession();

  useEffect(() => {
    void hydrate();
  }, [hydrate]);

  return (
    <header className="bg-gradient-to-b from-zinc-50 to-white border-b border-zinc-200">
      <div className="flex w-full items-center justify-between gap-6 px-6 py-5 lg:px-10">
        <Logo />
        <nav className="hidden items-center gap-8 text-base font-medium text-zinc-600 md:flex">
          {navLinks.map(([label, href]) => (
            <Link key={label} href={href} className="hover:text-brand-500">
              {label}
            </Link>
          ))}
        </nav>
        <nav className="flex items-center gap-4 text-base">
          {status === "loading" && <span className="h-5 w-20 animate-pulse rounded bg-zinc-200 dark:bg-zinc-800" />}
          {status === "anonymous" && (
            <>
              <Link href="/login" className="font-medium text-zinc-600 hover:text-brand-500 dark:text-zinc-400">
                Login
              </Link>
              <Link
                href="/register"
                className="rounded-lg bg-brand-500 px-5 py-2.5 font-semibold text-white hover:bg-brand-600"
              >
                Get Started
              </Link>
            </>
          )}
          {status === "authenticated" && user && (
            <>
              <span className="text-zinc-600 dark:text-zinc-400">{user.fullName}</span>
              <button
                onClick={() => void logout()}
                className="rounded-md border border-zinc-300 px-3 py-1.5 text-zinc-700 hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-900"
              >
                Sign out
              </button>
            </>
          )}
        </nav>
      </div>

      <div className="border-t border-zinc-100">
        <div className="mx-auto max-w-7xl px-6 py-4 lg:px-10">
          <form
            action="/doctors"
            className="flex flex-wrap items-center gap-3 sm:flex-nowrap"
          >
            <div className="flex flex-1 items-stretch gap-0 rounded-full border-2 border-zinc-200 bg-white p-1 shadow-md">
              <div className="flex-1 px-4">
                <input
                  name="q"
                  placeholder="Search doctor, specialty or condition"
                  className="w-full border-0 bg-transparent py-3 text-base text-zinc-900 outline-none placeholder:text-zinc-500"
                />
              </div>
              <button
                type="submit"
                className="rounded-full bg-brand-500 px-8 py-3 text-base font-semibold text-white transition-all hover:bg-brand-600"
              >
                Search
              </button>
            </div>
            <select
              name="specialty"
              className="rounded-lg border-2 border-zinc-200 bg-white px-4 py-3 text-base text-zinc-900 outline-none"
            >
              <option value="">Specialty</option>
            </select>
            <span className="hidden rounded-lg border-2 border-zinc-200 bg-white px-4 py-3 text-base text-zinc-900 md:flex">
              Nairobi, Kenya
            </span>
          </form>
        </div>
      </div>
    </header>
  );
}
