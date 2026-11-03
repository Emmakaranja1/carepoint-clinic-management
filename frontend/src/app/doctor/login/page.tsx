import type { Metadata } from "next";
import Link from "next/link";

import { Logo } from "@/components/Logo";
import { DoctorLoginForm } from "@/features/doctor/DoctorLoginForm";
import { ArrowLeftIcon } from "@/features/clinic-admin/icons";

export const metadata: Metadata = { title: "Doctor Sign In · CarePoint" };

const notices: Record<string, string> = {
  expired: "Your session ended after a period of inactivity. Sign in again to continue.",
  signedOut: "You've been signed out of the doctor workspace.",
};

export default async function DoctorLoginPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string; expired?: string; signedOut?: string }>;
}) {
  const params = await searchParams;
  const next = params.next?.startsWith("/doctor/") && !params.next.startsWith("//")
    ? params.next
    : undefined;
  const notice = params.expired ? notices.expired : params.signedOut ? notices.signedOut : null;

  return (
    <main className="flex min-h-screen flex-1 bg-white">
      <aside className="relative hidden w-[44%] max-w-xl flex-col justify-between overflow-hidden bg-navy p-12 text-white lg:flex">
        <div className="hero-grid absolute inset-0 opacity-[0.35] invert" aria-hidden="true" />
        <div className="relative">
          <Logo light size="lg" />
          <p className="mt-2 text-xs font-semibold uppercase tracking-[0.2em] text-white/45">Doctor portal</p>
        </div>

        <div className="relative">
          <h2 className="font-display text-4xl font-semibold leading-tight tracking-tight">
            View your patient list and planned day.
          </h2>
          <ul className="mt-8 space-y-4 text-sm text-white/75">
            <li className="flex gap-3">• Review today&apos;s appointments and patient details</li>
            <li className="flex gap-3">• Keep your clinic schedule current and visible</li>
            <li className="flex gap-3">• Access only the workspace linked to your doctor role</li>
          </ul>
        </div>

        <p className="relative text-xs text-white/40">Access is limited to invited clinic doctors.</p>
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
          <p className="mt-8 text-xs font-semibold uppercase tracking-[0.16em] text-brass lg:mt-0">Doctor access</p>
          <h1 className="mt-2 font-display text-3xl font-semibold tracking-tight text-heading sm:text-4xl">
            Doctor Login
          </h1>
          <p className="mt-2 mb-8 text-sm text-zinc-500 sm:text-base">
            Sign in with the email assigned to your doctor membership.
          </p>

          {notice && (
            <p className="mb-5 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800" role="status">
              {notice}
            </p>
          )}

          <DoctorLoginForm next={next} />
        </div>
      </section>
    </main>
  );
}
