import Link from "next/link";

import { commitments, footerGroups, supportHours } from "./footerLinks";
import { Logo } from "./Logo";

const currentYear = new Date().getFullYear();

const headingClass = "text-[0.72rem] font-semibold uppercase tracking-[0.18em] text-white";

function HelpCard({ kicker, title, href, children }: { kicker: string; title: string; href: string; children: React.ReactNode }) {
  return (
    <a
      href={href}
      className="group flex items-start gap-4 border-white/10 py-6 transition sm:px-6 md:border-l md:first:border-l-0 md:first:pl-0"
    >
      <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-white/[0.06] text-brand-300 ring-1 ring-white/10 transition group-hover:bg-brand-500 group-hover:text-white">
        {children}
      </span>
      <span>
        <span className="block text-xs font-semibold uppercase tracking-[0.16em] text-zinc-500">{kicker}</span>
        <span className="mt-1 block text-lg font-semibold text-white">{title}</span>
      </span>
    </a>
  );
}

export function Footer() {
  return (
    <footer className="bg-navy text-zinc-400">
      <div className="border-b border-white/10">
        <div className="page-container grid md:grid-cols-3">
          <HelpCard kicker="Book online" title="Find a doctor near you" href="/dashboard/doctors">
            <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
              <rect x="3.5" y="5" width="17" height="15" rx="2" />
              <path d="M3.5 10h17M8 3v4M16 3v4" strokeLinecap="round" />
            </svg>
          </HelpCard>
          <HelpCard kicker="Patient support" title="+254 700 123 456" href="tel:+254700123456">
            <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
              <path d="M5 4h3.5l1.5 4-2 1.5a11 11 0 0 0 6.5 6.5l1.5-2 4 1.5V19a1.5 1.5 0 0 1-1.6 1.5C10.6 20 4 13.4 3.5 5.6A1.5 1.5 0 0 1 5 4Z" strokeLinejoin="round" />
            </svg>
          </HelpCard>
          <HelpCard kicker="Write to us" title="info@carepoint.co.ke" href="mailto:info@carepoint.co.ke">
            <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
              <rect x="3" y="5" width="18" height="14" rx="2" />
              <path d="m4 7 8 6 8-6" strokeLinejoin="round" />
            </svg>
          </HelpCard>
        </div>
      </div>

      <div className="page-container grid grid-cols-2 gap-x-6 gap-y-12 py-16 sm:gap-x-10 lg:grid-cols-[1.5fr_1fr_1fr_1fr_1.2fr] lg:py-20">
        <div className="col-span-2 lg:col-span-1">
          <Logo light size="lg" />
          <p className="mt-6 max-w-xs font-display text-2xl leading-snug text-white">
            The front door for independent clinics.
          </p>
          <p className="mt-3 max-w-sm text-sm leading-relaxed">
            Discovery, real availability and booking for specialist practices across Kenya, without
            holding for the front desk.
          </p>
          <Link
            href="/register"
            className="mt-7 inline-flex items-center gap-2 bg-white px-5 py-3 text-sm font-semibold text-navy transition hover:bg-mint"
          >
            Create a free account <span aria-hidden="true">→</span>
          </Link>
        </div>

        {footerGroups.map((group) => (
          <nav key={group.id} aria-labelledby={group.id}>
            <h3 id={group.id} className={headingClass}>{group.title}</h3>
            <ul className="mt-5 space-y-3 text-sm">
              {group.links.map(([label, href]) => (
                <li key={label}>
                  <Link href={href} className="transition hover:text-white">{label}</Link>
                </li>
              ))}
            </ul>
          </nav>
        ))}

        <div id="contact" className="col-span-2 scroll-mt-28 sm:col-span-1">
          <h3 className={headingClass}>Contact & hours</h3>
          <address className="mt-5 space-y-1 text-sm not-italic leading-relaxed">
            <p className="text-white">CarePoint patient desk</p>
            <p>Nairobi, Kenya</p>
          </address>
          <dl className="mt-5 space-y-2 text-sm">
            {supportHours.map(([day, time]) => (
              <div key={day} className="flex justify-between gap-4 border-b border-white/[0.06] pb-2">
                <dt>{day}</dt>
                <dd className="text-white/85">{time}</dd>
              </div>
            ))}
          </dl>
          <p className="mt-5 text-sm leading-relaxed">
            In an emergency, call{" "}
            <a href="tel:999" className="font-semibold text-white hover:underline">999</a> or{" "}
            <a href="tel:112" className="font-semibold text-white hover:underline">112</a>. Don’t wait for a booking.
          </p>
        </div>
      </div>

      <div className="border-t border-white/10">
        <ul className="page-container grid gap-8 py-10 md:grid-cols-3 md:gap-10">
          {commitments.map((item) => (
            <li key={item.title} className="flex gap-3">
              <svg viewBox="0 0 24 24" className="mt-0.5 h-5 w-5 shrink-0 text-mint" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
                <path d="M12 3 5 6v5c0 4.4 3 8.3 7 9.5 4-1.2 7-5.1 7-9.5V6l-7-3Z" strokeLinejoin="round" />
                <path d="m9 12 2 2 4-4" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
              <div>
                <p className="text-sm font-semibold text-white">{item.title}</p>
                <p className="mt-1 text-sm leading-relaxed">{item.body}</p>
              </div>
            </li>
          ))}
        </ul>
      </div>

      <div className="border-t border-white/10 bg-[#060d18]">
        <div className="page-container flex flex-col items-start justify-between gap-3 py-5 text-xs sm:flex-row sm:items-center">
          <p>© {currentYear} CarePoint. All rights reserved.</p>
          <a href="#main" className="inline-flex items-center gap-1.5 transition hover:text-white">
            Back to top <span aria-hidden="true">↑</span>
          </a>
        </div>
      </div>
    </footer>
  );
}
