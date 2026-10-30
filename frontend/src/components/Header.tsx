"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";

import { AccountMenu } from "@/components/AccountMenu";
import { HeaderUtilityBar } from "@/components/HeaderUtilityBar";
import { Logo } from "@/components/Logo";
import { useSession } from "@/features/auth/session";

const navLinks = [
  ["Home", "/"],
  ["Find a Doctor", "/dashboard/doctors"],
  ["Specialties", "/#services"],
  ["How it works", "/#how-it-works"],
  ["Contact", "/#contact"],
] as const;

export function Header() {
  const pathname = usePathname();
  const { user, status, hydrate, logout } = useSession();
  const [open, setOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    void hydrate();
    const revalidate = () => document.visibilityState === "visible" && void hydrate();
    document.addEventListener("visibilitychange", revalidate);
    return () => document.removeEventListener("visibilitychange", revalidate);
  }, [hydrate]);

  useEffect(() => {
    const onScroll = () => setScrolled((was) => (was ? window.scrollY > 4 : window.scrollY > 48));
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => {
    document.body.style.overflow = open ? "hidden" : "";
    return () => {
      document.body.style.overflow = "";
    };
  }, [open]);

  useEffect(() => {
    setOpen(false);
  }, [pathname]);

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => event.key === "Escape" && setOpen(false);
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open]);

  return (
    <header
      className={`sticky top-0 z-50 text-white transition-shadow ${
        scrolled || open ? "shadow-[0_14px_32px_-18px_rgba(7,18,37,0.85)]" : ""
      }`}
    >
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-3 focus:z-50 focus:bg-white focus:px-3 focus:py-2 focus:text-sm focus:font-semibold focus:text-heading"
      >
        Skip to content
      </a>
      <HeaderUtilityBar hidden={scrolled} />
      <div className="border-b border-white/10 bg-navy">
        <div className="page-container flex h-[4.5rem] items-center justify-between gap-6">
          <div className="flex items-center">
            <Logo light size="lg" />
            <span aria-hidden="true" className="mx-8 hidden h-6 w-px bg-white/15 lg:block xl:mx-10" />
            <nav aria-label="Primary" className="hidden items-center gap-8 text-[1.02rem] font-bold text-white/75 lg:flex xl:gap-9">
              {navLinks.map(([label, href]) => {
                const active = pathname === href;
                return (
                  <Link
                    key={label}
                    href={href}
                    aria-current={active ? "page" : undefined}
                    className={`group relative py-6 transition-colors hover:text-white ${active ? "text-white" : ""}`}
                  >
                    {label}
                    <span
                      aria-hidden="true"
                      className={`absolute bottom-0 left-0 h-[3px] w-full origin-left bg-mint transition-transform duration-300 ${
                        active ? "scale-x-100" : "scale-x-0 group-hover:scale-x-100"
                      }`}
                    />
                  </Link>
                );
              })}
            </nav>
          </div>
          <div className="flex items-center gap-3">
            {status === "loading" && <span className="h-5 w-20 animate-pulse bg-white/15" />}
            {status === "anonymous" && (
              <nav aria-label="Account" className="hidden items-center gap-2 text-[1.02rem] font-bold sm:flex">
                <Link
                  href="/login"
                  className="inline-flex items-center gap-2 px-3 py-2 text-white/85 transition hover:text-white"
                >
                  <svg viewBox="0 0 24 24" className="h-[18px] w-[18px]" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                    <circle cx="12" cy="8" r="3.5" />
                    <path d="M5 20c.8-3.6 3.5-5.5 7-5.5s6.2 1.9 7 5.5" strokeLinecap="round" />
                  </svg>
                  Log in
                </Link>
                <Link
                  href="/register"
                  className="ml-1 inline-flex items-center gap-2 bg-white px-5 py-2.5 text-navy transition hover:bg-mint"
                >
                  Get started
                  <span aria-hidden="true">→</span>
                </Link>
              </nav>
            )}
            {status === "authenticated" && user && (
              <AccountMenu user={user} tone="dark" onSignOut={() => void logout()} />
            )}
            <button
              type="button"
              className="inline-flex h-11 w-11 items-center justify-center border border-white/15 bg-white/5 text-white lg:hidden"
              aria-expanded={open}
              aria-controls="mobile-nav"
              onClick={() => setOpen((v) => !v)}
            >
              <span className="sr-only">{open ? "Close menu" : "Open menu"}</span>
              <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
                {open ? <path d="M6 6l12 12M18 6 6 18" /> : <path d="M4 7h16M4 12h16M4 17h16" />}
              </svg>
            </button>
          </div>
        </div>
      </div>

      {open && (
        <div id="mobile-nav" className="border-b border-white/10 bg-navy px-[var(--page-gutter)] py-5 lg:hidden">
          <nav aria-label="Mobile" className="flex flex-col">
            {navLinks.map(([label, href]) => (
              <Link
                key={label}
                href={href}
                onClick={() => setOpen(false)}
                className="border-b border-white/10 py-3.5 text-lg font-bold text-white"
              >
                {label}
              </Link>
            ))}
            {status === "anonymous" && (
              <div className="mt-5 grid grid-cols-2 gap-3">
                <Link
                  href="/login"
                  className="min-h-12 border border-white/20 px-3 py-3 text-center text-base font-bold text-white"
                >
                  Log in
                </Link>
                <Link
                  href="/register"
                  className="min-h-12 bg-white px-3 py-3 text-center text-base font-bold text-navy"
                >
                  Get started
                </Link>
              </div>
            )}
          </nav>
        </div>
      )}
    </header>
  );
}
