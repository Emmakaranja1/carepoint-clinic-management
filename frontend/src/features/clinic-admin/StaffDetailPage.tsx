"use client";

import { useState } from "react";
import Link from "next/link";

import { ApiError } from "@/lib/api";
import { Avatar } from "@/features/dashboard/Avatar";
import { ErrorPanel, Skeleton } from "@/features/dashboard/ui";
import { getStaffMember, resendInvitation, setStaffStatus, type StaffMember } from "./api";
import { accessState, formatDate, formatDateTime, roleLabel } from "./format";
import { ArrowLeftIcon, MailIcon } from "./icons";
import { AccessBadge, Notice, SectionHeader, dangerButton, panelClass, primaryButton, secondaryButton } from "./ui";
import { useLoad } from "./useLoad";
import { useAdminWorkspace } from "./workspace";

export function StaffDetailPage({ membershipId }: { membershipId: string }) {
  const { clinic } = useAdminWorkspace();
  const member = useLoad(() => getStaffMember(clinic.clinicId, membershipId), [clinic.clinicId, membershipId]);

  return (
    <div className="space-y-6">
      <Link href="/clinic-admin/staff" className="inline-flex items-center gap-1.5 text-sm font-medium text-zinc-500 hover:text-heading">
        <ArrowLeftIcon className="h-4 w-4" />
        Staff
      </Link>
      {member.status === 404 || member.status === 400 ? (
        <div className={`${panelClass} px-6 py-14 text-center`}>
          <p className="font-medium text-heading">Staff member not found</p>
          <p className="mt-1 text-sm text-zinc-500">They may belong to a different clinic, or the link is incorrect.</p>
        </div>
      ) : member.error ? (
        <ErrorPanel message={member.error} onRetry={member.reload} />
      ) : !member.data ? (
        <Skeleton rows={2} />
      ) : (
        <StaffDetail member={member.data} onChange={member.reload} />
      )}
    </div>
  );
}

function StaffDetail({ member, onChange }: { member: StaffMember; onChange: () => void }) {
  const { clinic, user } = useAdminWorkspace();
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ tone: "success" | "error"; text: string } | null>(null);
  const isSelf = member.userId === user.id;
  const state = accessState(member.status, member.invitation);
  const canResend = member.status === "ACTIVE" && (member.invitation === "PENDING" || member.invitation === "EXPIRED");

  const run = async (action: () => Promise<unknown>, success: string) => {
    setBusy(true);
    setNotice(null);
    try {
      await action();
      setNotice({ tone: "success", text: success });
      setConfirming(false);
      onChange();
    } catch (e) {
      const fields = e instanceof ApiError ? Object.values(e.fields ?? {}) : [];
      setNotice({ tone: "error", text: fields[0] ?? (e instanceof ApiError ? e.message : "Something went wrong") });
    } finally {
      setBusy(false);
    }
  };

  const details: [string, string][] = [
    ["Work email", member.email],
    ["Phone", member.phone ?? "Not provided"],
    ["Role", roleLabel(member.role)],
    ["Clinic", clinic.clinicName],
    ["Added", formatDate(member.joinedAt)],
  ];
  if (state === "pending" && member.invitationExpiresAt) {
    details.push(["Invitation expires", formatDateTime(member.invitationExpiresAt)]);
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
      <section className={panelClass}>
        <div className="flex flex-col gap-4 border-b border-zinc-100 p-5 sm:flex-row sm:items-center sm:p-6">
          <Avatar name={member.fullName} size="lg" />
          <div className="min-w-0 flex-1">
            <h1 className="truncate font-display text-2xl font-semibold text-heading sm:text-3xl">{member.fullName}</h1>
            <div className="mt-1.5 flex flex-wrap items-center gap-2 text-sm text-zinc-600">
              {roleLabel(member.role)}
              <AccessBadge state={state} />
              {isSelf && <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-600">You</span>}
            </div>
          </div>
        </div>
        <dl className="divide-y divide-zinc-100 px-5 sm:px-6">
          {details.map(([label, value]) => (
            <div key={label} className="grid gap-1 py-3.5 text-sm sm:grid-cols-[11rem_minmax(0,1fr)]">
              <dt className="text-zinc-500">{label}</dt>
              <dd className="break-words font-medium text-heading">{value}</dd>
            </div>
          ))}
        </dl>
      </section>

      <section className={`${panelClass} h-fit`}>
        <SectionHeader title="Access" />
        <div className="space-y-4 p-5">
          {notice && <Notice tone={notice.tone}>{notice.text}</Notice>}
          {isSelf ? (
            <p className="text-sm text-zinc-500">You can&apos;t change your own access. Another administrator must do this.</p>
          ) : (
            <>
              <p className="text-sm text-zinc-600">
                {state === "pending" && "Waiting for them to set a password using their invitation link."}
                {state === "expired" && "Their invitation link expired before it was used. Send a new one to let them in."}
                {state === "active" && "They can sign in and use this clinic's workspace for their role."}
                {state === "disabled" && "Their access to this clinic is switched off. They can't use any staff features here."}
              </p>
              {canResend && (
                <button
                  type="button"
                  className={`${secondaryButton} w-full`}
                  disabled={busy}
                  onClick={() => run(() => resendInvitation(clinic.clinicId, member.membershipId), `A new invitation link was sent to ${member.email}. Earlier links no longer work.`)}
                >
                  <MailIcon className="h-4 w-4" />
                  {state === "expired" ? "Send new invitation" : "Resend invitation"}
                </button>
              )}
              {member.status === "DISABLED" ? (
                <button
                  type="button"
                  className={`${primaryButton} w-full`}
                  disabled={busy}
                  onClick={() => run(() => setStaffStatus(clinic.clinicId, member.membershipId, "ACTIVE"), `${member.fullName} can access the clinic again.`)}
                >
                  Reactivate access
                </button>
              ) : confirming ? (
                <div className="rounded-xl border border-red-200 bg-red-50/60 p-4">
                  <p className="text-sm font-medium text-red-900">Deactivate {member.fullName}?</p>
                  <p className="mt-1 text-sm text-red-800/80">They lose access to this clinic immediately. You can reactivate them later.</p>
                  <div className="mt-3 flex gap-2">
                    <button
                      type="button"
                      className="inline-flex flex-1 items-center justify-center rounded-lg bg-red-600 px-3 py-2 text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-60"
                      disabled={busy}
                      onClick={() => run(() => setStaffStatus(clinic.clinicId, member.membershipId, "DISABLED"), `${member.fullName} has been deactivated.`)}
                    >
                      {busy ? "Deactivating…" : "Deactivate"}
                    </button>
                    <button type="button" className={`${secondaryButton} flex-1`} disabled={busy} onClick={() => setConfirming(false)}>
                      Cancel
                    </button>
                  </div>
                </div>
              ) : (
                <button type="button" className={`${dangerButton} w-full`} onClick={() => setConfirming(true)}>
                  Deactivate access
                </button>
              )}
            </>
          )}
        </div>
      </section>
    </div>
  );
}
