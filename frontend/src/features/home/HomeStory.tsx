import Image from "next/image";
import Link from "next/link";

import { Reveal } from "./Reveal";
import { SectionHeading } from "./SectionHeading";

const steps = [
  {
    n: "01",
    title: "Search like you would ask a receptionist",
    body: "Name, specialty, or neighbourhood. You see who actually practises at each clinic — not a generic directory.",
  },
  {
    n: "02",
    title: "Pick a slot that already exists",
    body: "Availability is the clinic’s calendar, not a request form. If the time is shown, it can be booked.",
  },
  {
    n: "03",
    title: "Walk in with the details settled",
    body: "You get a confirmation, the clinic sees the visit, and you can reschedule from the same place.",
  },
] as const;

const stepIcons = [
  <>
    <circle cx="11" cy="11" r="6.5" />
    <path d="m16 16 4.5 4.5" />
  </>,
  <>
    <rect x="3.5" y="5" width="17" height="15" rx="2" />
    <path d="M3.5 10h17M8 3v4M16 3v4M9 14.5l2 2 4-4" />
  </>,
  <>
    <path d="M12 21s7-5.2 7-11a7 7 0 1 0-14 0c0 5.8 7 11 7 11Z" />
    <path d="m9.5 10 1.8 1.8 3.4-3.4" />
  </>,
];

export function HowItWorks() {
  return (
    <section id="how-it-works" className="scroll-mt-24 bg-white">
      <div className="page-container section-y">
        <Reveal>
          <SectionHeading
            id="about"
            eyebrow="How it works"
            title="Three quiet steps. No phone tag."
            description="CarePoint is the front door for independent clinics — the same booking path whether you are new or returning."
          />
        </Reveal>
        <ol className="mt-14 grid gap-5 md:grid-cols-3 xl:gap-6">
          {steps.map((step, index) => (
            <li key={step.n}>
              <Reveal delayMs={index * 80} className="h-full">
                <article className="group relative flex h-full flex-col overflow-hidden rounded-2xl border border-navy/10 bg-canvas/60 p-7 transition hover:border-brand-300 hover:bg-white xl:p-8">
                  <div className="flex items-center justify-between">
                    <span className="flex h-12 w-12 items-center justify-center rounded-xl bg-brand-50 text-brand-600 ring-1 ring-brand-100">
                      <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                        {stepIcons[index]}
                      </svg>
                    </span>
                    <span className="font-display text-5xl font-semibold tracking-tight text-navy/[0.08] transition group-hover:text-brass/40">
                      {step.n}
                    </span>
                  </div>
                  <h3 className="mt-8 max-w-xs font-display text-2xl font-semibold leading-snug text-heading">
                    {step.title}
                  </h3>
                  <p className="mt-3 max-w-md leading-relaxed text-ink/75">{step.body}</p>
                </article>
              </Reveal>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}

export function Proof() {
  return (
    <section className="page-grid bg-navy text-white [--split:0.5] lg:[--split:0.55]">
      <div className="relative col-[bleed] min-h-[20rem] sm:min-h-[26rem] md:col-[bleed-start/split] md:min-h-[34rem] xl:min-h-[38rem]">
        <Image
          src="/Modern Glass Medical Office Entrance.png"
          alt="A modern clinic building with a glass entrance"
          fill
          className="object-cover object-[center_70%]"
          sizes="(min-width: 768px) 55vw, 100vw"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-navy via-navy/20 to-transparent md:bg-gradient-to-r md:from-transparent md:via-navy/10 md:to-navy/80" />
      </div>
      <div className="col-[content] flex flex-col justify-center py-14 md:col-[split/content-end] md:py-16 md:pl-10 lg:py-20 lg:pl-16 xl:pl-20">
        <Reveal>
          <p className="text-[0.7rem] font-semibold uppercase tracking-[0.22em] text-brand-300">
            For clinics, too
          </p>
          <h2 className="mt-4 max-w-md font-display text-3xl font-semibold tracking-tight sm:text-4xl xl:max-w-lg xl:text-[2.75rem] xl:leading-[1.12]">
            Built for practices that still pick up the phone too often.
          </h2>
          <p className="mt-5 max-w-md leading-relaxed text-white/70">
            Staff keep the calendar. Patients stop calling to ask if Thursday is free.
            Role grants stay with clinic admins — nobody assigns themselves a privileged seat.
          </p>
          <ul className="mt-8 space-y-3 text-sm text-white/80">
            <li className="border-l-2 border-brass pl-4">Reception keeps the diary; patients book the open slot.</li>
            <li className="border-l-2 border-brass pl-4">Clinical records never move onto CarePoint.</li>
            <li className="border-l-2 border-brass pl-4">Clinic isolation is enforced on every tenant-scoped query.</li>
          </ul>
          <Link
            href="/register"
            className="mt-10 inline-flex min-h-12 w-fit items-center bg-white px-5 text-sm font-semibold text-navy transition hover:bg-brand-50"
          >
            Join as a patient
          </Link>
        </Reveal>
      </div>
    </section>
  );
}

export function ClosingCta() {
  return (
    <section className="bg-paper">
      <div className="page-container section-y">
        <Reveal className="relative isolate overflow-hidden rounded-[1.75rem] bg-gradient-to-br from-brand-600 to-brand-700 px-7 pb-28 pt-14 text-white sm:px-12 lg:px-16 lg:pb-32 lg:pt-20">
          <svg
            viewBox="0 0 1200 120"
            preserveAspectRatio="none"
            className="absolute inset-x-0 bottom-2 -z-10 h-20 w-full text-white/20"
            fill="none"
            aria-hidden="true"
          >
            <path
              d="M0 70h420l18-14 18 14h60l22-62 26 108 22-62 14 16h90l16-28 16 28h478"
              stroke="currentColor"
              strokeWidth="2.5"
              strokeLinejoin="round"
            />
          </svg>
          <div
            aria-hidden="true"
            className="absolute -right-24 -top-24 -z-10 h-72 w-72 rounded-full bg-white/10 blur-2xl"
          />
          <div className="grid items-end gap-9 lg:grid-cols-[minmax(0,1fr)_auto] lg:gap-16">
            <div>
              <p className="flex items-center gap-3 text-[0.7rem] font-semibold uppercase tracking-[0.22em] text-brand-100">
                <span aria-hidden="true" className="h-px w-8 bg-brand-100/70" />
                Begin
              </p>
              <h2 className="mt-4 max-w-3xl font-display text-3xl font-semibold tracking-tight sm:text-5xl sm:leading-[1.12] 2xl:text-[3.5rem]">
                The next open slot is already on a clinic calendar.
              </h2>
              <p className="mt-5 max-w-lg text-lg text-white/80">
                Create an account, find a doctor, and take it — without holding for the front desk.
              </p>
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <Link
                href="/register"
                className="inline-flex min-h-12 items-center gap-2 rounded-full bg-white px-6 text-sm font-semibold text-brand-700 transition hover:bg-mint hover:text-navy"
              >
                Get started <span aria-hidden="true">→</span>
              </Link>
              <Link
                href="/dashboard/doctors"
                className="inline-flex min-h-12 items-center rounded-full border border-white/40 px-6 text-sm font-semibold text-white transition hover:border-white hover:bg-white/10"
              >
                Find a doctor
              </Link>
            </div>
          </div>
        </Reveal>
      </div>
    </section>
  );
}
