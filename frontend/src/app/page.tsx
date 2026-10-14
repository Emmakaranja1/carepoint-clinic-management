export default function Home() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-6 bg-zinc-50 px-6 text-center dark:bg-zinc-950">
      <p className="text-sm font-semibold uppercase tracking-widest text-teal-600 dark:text-teal-400">
        CarePoint
      </p>
      <h1 className="max-w-2xl text-4xl font-bold tracking-tight text-zinc-900 sm:text-5xl dark:text-zinc-50">
        Clinic appointments, without the phone tag
      </h1>
      <p className="max-w-xl text-lg text-zinc-600 dark:text-zinc-400">
        Patients find a doctor, pick a real slot, and get on with their day.
        Reception keeps the diary full — booking, cancellations, no-shows and
        daily reports in one place.
      </p>
      <p className="text-sm text-zinc-400 dark:text-zinc-600">
        Demo data only — a portfolio build, not a live clinic system.
      </p>
    </main>
  );
}
