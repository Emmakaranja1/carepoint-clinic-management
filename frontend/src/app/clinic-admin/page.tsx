import type { Metadata } from "next";
import Link from "next/link";

import { Logo } from "@/components/Logo";
import { AdminLoginForm } from "@/features/clinic-admin/AdminLoginForm";
import { ArrowLeftIcon, OverviewIcon, ShieldIcon, StaffIcon } from "@/features/clinic-admin/icons";

export const metadata: Metadata = { title: "Clinic Administrator Login · CarePoint" };

const notices: Record<string, string> = {
  expired: "Your session ended after a period of inactivity. Sign in again to continue.",
  signedOut: "You've been signed out of the clinic workspace.",
};

export default async function ClinicAdminLoginPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string; expired?: string; signedOut?: string }>;
}) {
  const params = await searchParams;
  const next =
    params.next?.startsWith("/clinic-admin/") && !params.next.startsWith("//")
      ? params.next
      : "/clinic-admin/overview";
  const notice = params.expired ? notices.expired : params.signedOut ? notices.signedOut : null;

  return (
    <main className="flex min-h-screen flex-1 bg-white">
      <aside className="relative hidden w-[44%] max-w-xl flex-col justify-between overflow-hidden bg-navy p-12 text-white lg:flex">
        <div className="hero-grid absolute inset-0 opacity-[0.35] invert" aria-hidden="true" />
        <div className="relative">
          <Logo light size="lg" />
          <p className="mt-2 text-xs font-semibold uppercase tracking-[0.2em] text-white/45">Clinic workspace</p>
        </div>
        <div className="relative">
          <h2 className="font-display text-4xl font-semibold leading-tight tracking-tight">
            Run your practice with a clear view of the day.
          </h2>
          <ul className="mt-8 space-y-4 text-sm text-white/75">
            <li className="flex gap-3"><OverviewIcon className="h-5 w-5 shrink-0 text-brand-300" />Live appointment and utilisation figures for your clinic</li>
            <li className="flex gap-3"><StaffIcon className="h-5 w-5 shrink-0 text-brand-300" />Invite receptionists and control who can access the desk</li>
            <li className="flex gap-3"><ShieldIcon className="h-5 w-5 shrink-0 text-brand-300" />Every staff change is recorded in your clinic&apos;s audit trail</li>
          </ul>
        </div>
        <p className="relative text-xs text-white/40">Access is limited to invited clinic administrators.</p>
      </aside>

      <section className="flex flex-1 flex-col px-5 py-8 sm:px-10">
        <Link href="/" className="inline-flex items-center gap-2 self-start rounded-md text-sm font-medium text-zinc-500 hover:text-heading">
          <ArrowLeftIcon className="h-4 w-4" />
          Back to CarePoint
        </Link>
        <div className="mx-auto flex w-full max-w-[26rem] flex-1 flex-col justify-center py-10">
          <div className="lg:hidden">
            <Logo />
          </div>
          <p className="mt-8 text-xs font-semibold uppercase tracking-[0.16em] text-brass lg:mt-0">Staff only</p>
          <h1 className="mt-2 font-display text-3xl font-semibold tracking-tight text-heading sm:text-4xl">
            Clinic Administrator Login
          </h1>
          <p className="mt-2 mb-8 text-sm text-zinc-500 sm:text-base">
            Sign in with the work email your clinic invitation was sent to.
          </p>
          <AdminLoginForm next={next} notice={notice} />
          <p className="mt-8 border-t border-zinc-100 pt-6 text-sm text-zinc-500">
            Booking an appointment?{" "}
            <Link href="/login" className="font-medium text-brand-600 hover:underline">Patient sign in</Link>
          </p>
        </div>
      </section>
    </main>
  );
}
