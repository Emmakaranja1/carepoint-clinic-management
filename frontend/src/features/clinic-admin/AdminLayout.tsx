"use client";

import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";

import { Logo } from "@/components/Logo";
import { useSession, type SessionUser } from "@/features/auth/session";
import { useIdleTimeout } from "@/features/auth/useIdleTimeout";
import { myClinics, type ClinicMembership } from "@/features/clinics/api";
import { ErrorPanel, Skeleton } from "@/features/dashboard/ui";
import { AdminShell } from "./AdminShell";
import { ShieldIcon } from "./icons";
import { AdminWorkspaceContext } from "./workspace";

const CLINIC_KEY = "cp:adminClinic";

export function AdminLayout({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { user, status, hydrate } = useSession();

  useEffect(() => {
    void hydrate();
  }, [hydrate]);

  useEffect(() => {
    if (status === "anonymous") router.replace(`/clinic-admin?next=${encodeURIComponent(pathname)}`);
  }, [pathname, router, status]);

  if (status !== "authenticated" || !user) return <FullPageLoading />;
  return <AdminWorkspaceLoader user={user}>{children}</AdminWorkspaceLoader>;
}

function AdminWorkspaceLoader({ user, children }: { user: SessionUser; children: ReactNode }) {
  const router = useRouter();
  const logout = useSession((s) => s.logout);
  const [clinics, setClinics] = useState<ClinicMembership[] | null>(null);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState<string | null>(null);

  const load = useCallback(() => {
    setError("");
    myClinics()
      .then((all) => setClinics(all.filter((m) => m.role === "CLINIC_ADMIN")))
      .catch(() => setError("We couldn't load your clinic workspace."));
  }, []);

  useEffect(() => {
    load();
    setSelected(sessionStorage.getItem(CLINIC_KEY));
  }, [load]);

  const signOut = useCallback(() => {
    void logout().finally(() => router.replace("/clinic-admin?signedOut=1"));
  }, [logout, router]);

  const expire = useCallback(() => {
    void logout().finally(() => router.replace("/clinic-admin?expired=1"));
  }, [logout, router]);
  useIdleTimeout(expire);

  const clinic = useMemo(
    () => clinics?.find((c) => c.clinicId === selected) ?? clinics?.[0] ?? null,
    [clinics, selected],
  );

  const selectClinic = useCallback(
    (clinicId: string) => {
      sessionStorage.setItem(CLINIC_KEY, clinicId);
      setSelected(clinicId);
      router.push("/clinic-admin/overview");
    },
    [router],
  );

  if (error) {
    return (
      <main className="flex flex-1 items-center justify-center bg-canvas px-6">
        <div className="w-full max-w-md"><ErrorPanel message={error} onRetry={load} /></div>
      </main>
    );
  }
  if (!clinics) return <FullPageLoading />;
  if (!clinic) return <NoAdminAccess onSignOut={signOut} />;

  return (
    <AdminWorkspaceContext.Provider value={{ user, clinics, clinic, selectClinic, signOut }}>
      <AdminShell key={clinic.clinicId}>{children}</AdminShell>
    </AdminWorkspaceContext.Provider>
  );
}

function FullPageLoading() {
  return (
    <main className="flex flex-1 items-center justify-center bg-canvas px-6">
      <div className="w-full max-w-xl"><Skeleton /></div>
    </main>
  );
}

function NoAdminAccess({ onSignOut }: { onSignOut: () => void }) {
  return (
    <main className="flex flex-1 items-center justify-center bg-canvas px-6 py-16">
      <div className="w-full max-w-md rounded-2xl border border-zinc-200 bg-white p-8 text-center shadow-sm">
        <Logo />
        <span className="mx-auto mt-8 flex h-12 w-12 items-center justify-center rounded-full bg-brass-soft text-brass">
          <ShieldIcon className="h-6 w-6" />
        </span>
        <h1 className="mt-4 text-xl font-semibold text-heading">No clinic administrator access</h1>
        <p className="mt-2 text-sm text-zinc-500">
          This account isn&apos;t an active administrator of any clinic. If you believe this is a mistake,
          contact your clinic&apos;s administrator.
        </p>
        <div className="mt-6 flex flex-col gap-2 sm:flex-row sm:justify-center">
          <button
            type="button"
            onClick={onSignOut}
            className="rounded-lg bg-navy px-4 py-2.5 text-sm font-semibold text-white hover:bg-heading"
          >
            Sign out
          </button>
          <Link href="/" className="rounded-lg border border-zinc-200 px-4 py-2.5 text-sm font-semibold text-heading hover:bg-zinc-50">
            Go to CarePoint
          </Link>
        </div>
      </div>
    </main>
  );
}
