"use client";

import { useEffect } from "react";
import Link from "next/link";

import { Logo } from "@/components/Logo";
import { useSession } from "@/features/auth/session";

export function Header() {
  const { user, status, hydrate, logout } = useSession();

  useEffect(() => {
    void hydrate();
  }, [hydrate]);

  return (
    <header className="flex w-full items-center justify-between px-6 py-4 lg:px-10">
      <Logo />
      <nav className="flex items-center gap-4 text-sm">
        {status === "loading" && <span className="h-5 w-20 animate-pulse rounded bg-zinc-200 dark:bg-zinc-800" />}
        {status === "anonymous" && (
          <>
            <Link href="/login" className="text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-50">
              Sign in
            </Link>
            <Link
              href="/register"
              className="rounded-lg bg-brand-500 px-4 py-2 font-semibold text-white hover:bg-brand-600"
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
    </header>
  );
}
