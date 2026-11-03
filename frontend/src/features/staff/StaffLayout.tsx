"use client";

import { useEffect, useMemo, useState, type ReactNode } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";

import { Logo } from "@/components/Logo";
import { useSession, type SessionUser } from "@/features/auth/session";
import { useIdleTimeout } from "@/features/auth/useIdleTimeout";
import { myClinics, type ClinicMembership } from "@/features/clinics/api";
import { ErrorPanel, Skeleton } from "@/features/dashboard/ui";
import { StaffShell } from "./StaffShell";

export function StaffLayout({ clinicId, children }: { clinicId: string; children: ReactNode }) {
  const router = useRouter();
  const { user, status, hydrate } = useSession();

  useEffect(() => {
    void hydrate();
  }, [hydrate]);

  useEffect(() => {
    if (status === "anonymous") {
      router.replace(`/staff/login?next=${encodeURIComponent(`/staff/${clinicId}/agenda`)}`);
    }
  }, [clinicId, router, status]);

  if (status !== "authenticated" || !user) {
    return (
      <main className="flex flex-1 items-center justify-center bg-canvas px-6">
        <div className="w-full max-w-xl"><Skeleton /></div>
      </main>
    );
  }

  return <StaffWorkspaceLoader clinicId={clinicId} user={user}>{children}</StaffWorkspaceLoader>;
}

function StaffWorkspaceLoader({ clinicId, user, children }: { clinicId: string; user: SessionUser; children: ReactNode }) {
  const router = useRouter();
  const logout = useSession((s) => s.logout);
  const [clinics, setClinics] = useState<ClinicMembership[] | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    myClinics()
      .then((all) => setClinics(all.filter((m) => m.role === "RECEPTIONIST" || m.role === "CLINIC_ADMIN")))
      .catch(() => setError("We couldn't load your clinic desk."));
  }, []);

  const signOut = () => {
    void logout().finally(() => router.replace("/staff/login"));
  };

  const expire = () => {
    void logout().finally(() => router.replace("/staff/login?expired=1"));
  };
  useIdleTimeout(expire);

  const clinic = useMemo(
    () => clinics?.find((c) => c.clinicId === clinicId) ?? clinics?.[0] ?? null,
    [clinicId, clinics],
  );

  if (error) {
    return (
      <main className="flex flex-1 items-center justify-center bg-canvas px-6">
        <div className="w-full max-w-md"><ErrorPanel message={error} onRetry={() => window.location.reload()} /></div>
      </main>
    );
  }

  if (!clinics) {
    return (
      <main className="flex flex-1 items-center justify-center bg-canvas px-6">
        <div className="w-full max-w-xl"><Skeleton /></div>
      </main>
    );
  }

  if (!clinic) {
    return (
      <main className="flex flex-1 items-center justify-center bg-canvas px-6 py-16">
        <div className="w-full max-w-md rounded-2xl border border-zinc-200 bg-white p-8 text-center shadow-sm">
          <Logo />
          <h1 className="mt-6 text-xl font-semibold text-heading">No front desk access</h1>
          <p className="mt-2 text-sm text-zinc-500">
            This account is not active in a clinic reception role.
          </p>
          <div className="mt-6 flex flex-col gap-2 sm:flex-row sm:justify-center">
            <button type="button" onClick={signOut} className="rounded-lg bg-navy px-4 py-2.5 text-sm font-semibold text-white hover:bg-heading">
              Sign out
            </button>
            <Link href="/dashboard" className="rounded-lg border border-zinc-200 px-4 py-2.5 text-sm font-semibold text-heading hover:bg-zinc-50">
              Go to dashboard
            </Link>
          </div>
        </div>
      </main>
    );
  }

  return (
    <StaffShell user={user} clinic={clinic} onSignOut={signOut}>
      {children}
    </StaffShell>
  );
}
