import Link from "next/link";

import { CalendarIcon, DocumentIcon, SupportIcon, UserIcon } from "./icons";

const actions = [
  { label: "Book Appointment", href: "/dashboard/doctors", icon: CalendarIcon },
  { label: "Find a Doctor", href: "/dashboard/doctors", icon: UserIcon },
  { label: "View Visit History", href: "/dashboard/health", icon: DocumentIcon },
  { label: "Contact Support", href: "/#contact", icon: SupportIcon },
];

const actionClass =
  "flex w-full items-center gap-3 rounded-xl bg-brand-50 px-5 py-4 text-left text-base font-semibold text-heading transition hover:bg-brand-100";

function HealthIllustration() {
  return (
    <svg viewBox="0 0 120 110" className="absolute -bottom-1 -right-1 h-44 w-48" aria-hidden="true">
      <path
        d="M70 34s-12-7.8-15.5-14.4c-2.6-4.8-.8-10.6 4.3-12.4 3-1.1 6.4 0 8.3 2.3L70 11.6l2.9-2.1c1.9-2.3 5.3-3.4 8.3-2.3 5.1 1.8 6.9 7.6 4.3 12.4C82 26.2 70 34 70 34Z"
        fill="none"
        stroke="#0a6efc"
        strokeWidth="2.4"
        strokeLinejoin="round"
      />
      <circle cx="100" cy="10" r="2" fill="#8ab9fa" />
      <circle cx="44" cy="22" r="1.5" fill="#8ab9fa" />
      <path d="M96 110c-2-20 2-40 14-58" fill="none" stroke="#17866d" strokeWidth="2" />
      <path d="M100 82c-12-4-20-14-20-26 12 2 20 12 20 26Z" fill="#67cebb" />
      <path d="M104 68c4-12 12-18 22-18-2 12-10 18-22 18Z" fill="#17866d" />
      <path d="M98 98c-14 0-24-8-26-18 12-1 22 6 26 18Z" fill="#17866d" opacity=".85" />
      <path d="M108 52c-2-10 2-20 10-24 2 10-2 18-10 24Z" fill="#67cebb" />
    </svg>
  );
}

export function QuickActions() {
  return (
    <aside className="space-y-4">
      <section aria-labelledby="quick-actions-heading" className="rounded-2xl border border-brand-100/70 bg-white p-6 shadow-[0_2px_10px_rgba(1,25,77,0.04)]">
        <h2 id="quick-actions-heading" className="text-lg font-bold text-heading">Quick Actions</h2>
        <ul className="mt-5 space-y-3">
          {actions.map(({ label, href, icon: Icon }) => (
            <li key={label}>
              <Link href={href} className={actionClass}>
                <Icon className="h-5 w-5 text-brand-500" />
                {label}
              </Link>
            </li>
          ))}
        </ul>
      </section>

      <section className="relative min-h-48 overflow-hidden rounded-2xl bg-gradient-to-br from-brand-50 to-brand-100 p-6">
        <h2 className="max-w-40 text-lg font-bold leading-snug text-heading">Your Health Matters</h2>
        <p className="mt-2 max-w-48 text-sm leading-relaxed text-zinc-500">
          Stay consistent with your checkups and live healthier.
        </p>
        <HealthIllustration />
      </section>
    </aside>
  );
}
