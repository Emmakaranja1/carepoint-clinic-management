import Link from "next/link";

export default function Home() {
  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-6 px-6 text-center">
      <p className="text-sm font-semibold uppercase tracking-widest text-brand-500 dark:text-brand-300">
        CarePoint
      </p>
      <h1 className="max-w-2xl text-4xl font-bold tracking-tight text-heading sm:text-5xl">
        Clinic appointments, without the phone tag
      </h1>
      <p className="max-w-xl text-lg text-zinc-600 dark:text-zinc-400">
        Patients find a doctor, pick a real slot, and get on with their day.
        Reception keeps the diary full — booking, cancellations, no-shows and
        daily reports in one place.
      </p>
      <div className="flex gap-3">
        <Link
          href="/register"
          className="rounded-md bg-brand-500 px-5 py-2.5 text-sm font-semibold text-white hover:bg-brand-600"
        >
          Create account
        </Link>
        <Link
          href="/login"
          className="rounded-md border border-zinc-300 px-5 py-2.5 text-sm font-semibold text-zinc-700 hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-900"
        >
          Sign in
        </Link>
      </div>
      <p className="text-sm text-zinc-400 dark:text-zinc-600">
        Demo data only — a portfolio build, not a live clinic system.
      </p>
    </main>
  );
}
