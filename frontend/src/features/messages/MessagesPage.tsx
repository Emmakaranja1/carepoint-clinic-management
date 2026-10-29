import Link from "next/link";

import { MessageIcon, SearchIcon } from "@/features/dashboard/icons";
import { cardClass, PageHeader, primaryButtonClass, secondaryButtonClass } from "@/features/dashboard/ui";

export function MessagesPage() {
  return (
    <>
      <PageHeader title="Messages" description="Conversations with your clinics will appear here." />

      <section className={`${cardClass} mt-6 grid min-h-[28rem] gap-0 overflow-hidden p-0 md:grid-cols-[18rem_minmax(0,1fr)]`}>
        <div className="border-b border-brand-100/70 p-5 md:border-b-0 md:border-r">
          <label className="relative block">
            <span className="sr-only">Search conversations</span>
            <SearchIcon className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" />
            <input
              type="search"
              disabled
              placeholder="Search conversations"
              className="w-full rounded-xl border border-brand-100 bg-canvas py-2.5 pl-9 pr-3 text-sm text-zinc-900 placeholder:text-zinc-400 disabled:cursor-not-allowed"
            />
          </label>
          <p className="mt-6 text-center text-sm text-zinc-400">No conversations</p>
        </div>

        <div className="flex flex-col items-center justify-center p-10 text-center">
          <span className="flex h-16 w-16 items-center justify-center rounded-2xl bg-brand-50 text-brand-500">
            <MessageIcon className="h-8 w-8" />
          </span>
          <h2 className="mt-5 text-lg font-bold text-heading">No messages yet</h2>
          <p className="mt-2 max-w-sm text-sm text-zinc-500">
            In-app messaging with clinics is coming soon. Until then, manage visits from Appointments or reach our team directly.
          </p>
          <div className="mt-6 flex flex-wrap justify-center gap-3">
            <Link href="/dashboard/appointments" className={primaryButtonClass}>View appointments</Link>
            <Link href="/#contact" className={secondaryButtonClass}>Contact support</Link>
          </div>
        </div>
      </section>
    </>
  );
}
