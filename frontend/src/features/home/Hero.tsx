import Link from "next/link";

import { HeroSearch } from "./HeroSearch";
import { HeroVisual } from "./HeroVisual";

const assurances = ["Live slots from clinic diaries", "Free patient account", "No phone queues"] as const;

function CheckIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-3 w-3" fill="none" stroke="currentColor" strokeWidth="3" aria-hidden="true">
      <path d="m5 12.5 4.5 4.5L19 7.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function Hero() {
  return (
    <section
      aria-labelledby="hero-heading"
      className="relative isolate overflow-hidden bg-gradient-to-b from-brand-50 via-[#f4f7fb] to-canvas"
    >
      <div aria-hidden="true" className="hero-grid pointer-events-none absolute inset-0 -z-10" />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -right-40 -top-40 -z-10 h-[34rem] w-[34rem] rounded-full bg-brand-300/30 blur-3xl"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -left-32 top-1/2 -z-10 h-[26rem] w-[26rem] rounded-full bg-mint/20 blur-3xl"
      />

      <div className="page-container grid items-center gap-14 pb-10 pt-12 sm:pt-16 lg:grid-cols-[1.08fr_0.92fr] lg:gap-12 lg:pb-14 lg:pt-20 xl:gap-20 xl:pt-24">
        <div className="hero-rise max-w-2xl">
          <p className="inline-flex items-center gap-2.5 rounded-full border border-brand-100 bg-white/80 py-1.5 pl-2.5 pr-4 text-[0.8rem] font-semibold text-heading shadow-[0_6px_18px_-12px_rgba(7,18,37,0.4)] backdrop-blur">
            <span className="relative flex h-2.5 w-2.5">
              <span className="hero-ping absolute inline-flex h-full w-full rounded-full bg-mint" />
              <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-mint-foreground" />
            </span>
            <span>
              Live clinic calendars<span className="hidden sm:inline"> · Independent clinics in Kenya</span>
            </span>
          </p>

          <h1
            id="hero-heading"
            className="mt-6 font-display text-[2.6rem] font-semibold leading-[1.04] tracking-[-0.02em] text-heading sm:text-[3.4rem] lg:text-[3.7rem] xl:text-[4.4rem] 2xl:text-[4.8rem]"
          >
            See a specialist{" "}
            <span className="relative inline-block whitespace-nowrap text-brand-600">
              <em className="italic">this week</em>
              <svg
                viewBox="0 0 300 18"
                preserveAspectRatio="none"
                className="hero-underline absolute -bottom-1.5 left-0 h-3 w-full text-mint sm:-bottom-2 sm:h-4"
                fill="none"
                aria-hidden="true"
              >
                <path d="M3 13C60 5 160 2 297 8" stroke="currentColor" strokeWidth="6" strokeLinecap="round" />
              </svg>
            </span>
            , not next month.
          </h1>

          <p className="mt-7 max-w-xl text-lg leading-relaxed text-ink/85 xl:text-xl">
            Search by doctor, specialty or neighbourhood, take a slot that already exists on a clinic
            calendar, and walk in prepared.
          </p>

          <div className="mt-9 flex flex-wrap items-center gap-3">
            <a
              href="#find"
              className="group inline-flex min-h-13 items-center gap-2.5 bg-brand-500 px-6 text-[0.95rem] font-semibold text-white shadow-[0_16px_32px_-14px_rgba(10,110,252,0.75)] transition hover:-translate-y-0.5 hover:bg-brand-600"
            >
              Find a doctor
              <svg viewBox="0 0 24 24" className="h-4 w-4 transition group-hover:translate-x-0.5" fill="none" stroke="currentColor" strokeWidth="2.2" aria-hidden="true">
                <path d="M5 12h14M13 6l6 6-6 6" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </a>
            <Link
              href="/register"
              className="inline-flex min-h-13 items-center border border-navy/15 bg-white/90 px-6 text-[0.95rem] font-semibold text-heading transition hover:-translate-y-0.5 hover:border-navy/35"
            >
              Create a free account
            </Link>
          </div>

          <ul className="mt-9 flex flex-wrap gap-x-6 gap-y-3 text-sm font-medium text-heading/80">
            {assurances.map((item) => (
              <li key={item} className="flex items-center gap-2">
                <span className="flex h-5 w-5 items-center justify-center rounded-full bg-mint/25 text-mint-foreground">
                  <CheckIcon />
                </span>
                {item}
              </li>
            ))}
          </ul>
        </div>

        <HeroVisual />
      </div>

      <div id="find" className="page-container relative z-10 scroll-mt-28 pb-16 pt-6 lg:pb-24">
        <div className="hero-rise [animation-delay:240ms]">
          <p className="mb-3 text-[0.7rem] font-semibold uppercase tracking-[0.18em] text-brass">
            Start with a name, specialty, or place
          </p>
          <HeroSearch />
        </div>
      </div>
    </section>
  );
}
