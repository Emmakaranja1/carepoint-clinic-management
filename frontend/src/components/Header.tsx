"use client";

import { useEffect } from "react";
import Link from "next/link";

import { AccountMenu } from "@/components/AccountMenu";
import { Logo } from "@/components/Logo";
import { useSession } from "@/features/auth/session";

const navLinks = [
  ["Home", "/"],
  ["Find a Doctor", "/dashboard"],
  ["Services", "/#services"],
  ["About", "/#about"],
  ["Contact", "/#contact"],
] as const;

export function Header() {
  const { user, status, hydrate, logout } = useSession();

  useEffect(() => {
    void hydrate();
    const revalidate = () => document.visibilityState === "visible" && void hydrate();
    document.addEventListener("visibilitychange", revalidate);
    return () => document.removeEventListener("visibilitychange", revalidate);
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
            <AccountMenu user={user} onSignOut={() => void logout()} />
          )}
        </nav>
      </div>


    </header>
  );
}
