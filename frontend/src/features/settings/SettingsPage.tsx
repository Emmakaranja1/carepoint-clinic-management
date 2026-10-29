"use client";

import { useRouter } from "next/navigation";

import { useSession } from "@/features/auth/session";
import { cardClass, PageHeader, secondaryButtonClass } from "@/features/dashboard/ui";
import { PasswordForm } from "./PasswordForm";
import { ProfileForm } from "./ProfileForm";

export function SettingsPage() {
  const router = useRouter();
  const { user, logout } = useSession();
  if (!user) return null;

  return (
    <>
      <PageHeader title="Settings" description="Manage your profile and sign-in security." />
      <div className="mt-6 grid gap-6 xl:grid-cols-2">
        <ProfileForm user={user} />
        <div className="space-y-6">
          <PasswordForm user={user} />
          <section className={cardClass} aria-labelledby="session-heading">
            <h2 id="session-heading" className="text-lg font-bold text-heading">Session</h2>
            <p className="mt-1 text-sm text-zinc-500">Sign out of CarePoint on this device.</p>
            <button
              type="button"
              onClick={() => void logout().then(() => router.replace("/"))}
              className={`${secondaryButtonClass} mt-4 text-red-600`}
            >
              Sign out
            </button>
          </section>
        </div>
      </div>
    </>
  );
}
