"use client";

import { useCallback, useEffect, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";

import { useSession, type SessionUser } from "@/features/auth/session";
import { useIdleTimeout } from "@/features/auth/useIdleTimeout";
import { DashboardShell } from "./DashboardShell";
import { Skeleton } from "./ui";

export function DashboardLayout({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { user, status, hydrate } = useSession();

  useEffect(() => {
    void hydrate();
  }, [hydrate]);

  useEffect(() => {
    if (status === "anonymous") router.replace(`/login?next=${encodeURIComponent(pathname)}`);
  }, [pathname, router, status]);

  if (status !== "authenticated" || !user) {
    return (
      <main className="flex flex-1 items-center justify-center bg-canvas px-6">
        <div className="w-full max-w-xl"><Skeleton /></div>
      </main>
    );
  }

  return <SignedInDashboard user={user}>{children}</SignedInDashboard>;
}

function SignedInDashboard({ user, children }: { user: SessionUser; children: ReactNode }) {
  const router = useRouter();
  const logout = useSession((s) => s.logout);

  const expire = useCallback(() => void logout(), [logout]);
  useIdleTimeout(expire);

  const signOut = async () => {
    await logout();
    router.replace("/");
  };

  return (
    <DashboardShell user={user} onSignOut={() => void signOut()}>
      {children}
    </DashboardShell>
  );
}
