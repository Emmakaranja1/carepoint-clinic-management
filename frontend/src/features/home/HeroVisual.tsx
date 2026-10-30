import Image from "next/image";

const slots = [
  { time: "09:30", picked: false },
  { time: "11:00", picked: true },
  { time: "14:30", picked: false },
] as const;

export function HeroVisual() {
  return (
    <div className="hero-rise relative mx-auto w-full max-w-[25rem] [animation-delay:120ms] sm:max-w-[30rem] lg:mr-0 xl:max-w-[33rem]">
      <div aria-hidden="true" className="hero-dots absolute -left-8 -top-6 h-36 w-36 sm:-left-12" />
      <div
        aria-hidden="true"
        className="absolute inset-0 translate-x-4 translate-y-4 rounded-t-full bg-gradient-to-br from-brand-500 to-brand-700 sm:translate-x-6 sm:translate-y-6"
      />

      <div className="relative aspect-[4/5] overflow-hidden rounded-t-full bg-brand-100 shadow-[0_40px_80px_-40px_rgba(7,18,37,0.6)] ring-[6px] ring-white">
        <Image
          src="/hero-doctor.png"
          alt="A smiling clinician in a white coat with a stethoscope, ready to see patients"
          fill
          priority
          sizes="(min-width: 1280px) 33rem, (min-width: 640px) 30rem, 25rem"
          className="object-cover object-[center_18%]"
        />
        <div className="absolute inset-x-0 bottom-0 h-1/3 bg-gradient-to-t from-navy/35 to-transparent" />
      </div>

      <div
        aria-hidden="true"
        className="hero-float absolute -left-3 bottom-8 w-[15.5rem] border border-navy/5 bg-white/95 p-4 shadow-[0_24px_48px_-24px_rgba(7,18,37,0.55)] backdrop-blur sm:-left-14 sm:bottom-12 sm:w-[17rem]"
      >
        <div className="flex items-center justify-between">
          <p className="text-[0.65rem] font-semibold uppercase tracking-[0.16em] text-brass">Next available</p>
          <span className="rounded-full bg-mint/20 px-2 py-0.5 text-[0.65rem] font-semibold text-mint-foreground">
            Today
          </span>
        </div>
        <p className="mt-2 font-semibold text-heading">Cardiology consult</p>
        <p className="text-xs text-zinc-500">30 min · Kilimani</p>
        <div className="mt-3 grid grid-cols-3 gap-1.5">
          {slots.map(({ time, picked }) => (
            <span
              key={time}
              className={`py-1.5 text-center text-xs font-semibold ${
                picked ? "bg-brand-500 text-white" : "border border-navy/10 text-heading"
              }`}
            >
              {time}
            </span>
          ))}
        </div>
      </div>

      <div
        aria-hidden="true"
        className="hero-float absolute -right-8 top-[16%] hidden items-center sm:flex gap-3 border border-navy/5 bg-white/95 py-3 pl-3 pr-4 shadow-[0_24px_48px_-24px_rgba(7,18,37,0.55)] backdrop-blur [animation-delay:-3s] sm:-right-8"
      >
        <span className="flex h-9 w-9 items-center justify-center rounded-full bg-mint/20 text-mint-foreground">
          <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2.6">
            <path d="m5 12.5 4.5 4.5L19 7.5" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </span>
        <span>
          <span className="block text-sm font-semibold text-heading">Booking confirmed</span>
          <span className="block text-xs text-zinc-500">Today, 11:00 · in your appointments</span>
        </span>
      </div>
    </div>
  );
}
