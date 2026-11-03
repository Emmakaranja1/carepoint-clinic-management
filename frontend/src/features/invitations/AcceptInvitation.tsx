"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { standardSchemaResolver } from "@hookform/resolvers/standard-schema";
import { z } from "zod";

import { Logo } from "@/components/Logo";
import { passwordSchema } from "@/features/auth/schemas";
import { applyApiErrors } from "@/features/auth/ui";
import { roleLabel } from "@/features/clinic-admin/format";
import { EyeIcon, ShieldIcon } from "@/features/clinic-admin/icons";
import { Notice, fieldClass, primaryButton } from "@/features/clinic-admin/ui";
import { ApiError } from "@/lib/api";
import { acceptInvitation, previewInvitation, type InvitationPreview } from "./api";

const schema = z
  .object({ password: passwordSchema, confirmPassword: z.string().min(1, "Confirm your password") })
  .refine((v) => v.password === v.confirmPassword, { path: ["confirmPassword"], message: "Passwords do not match" });
type Values = z.infer<typeof schema>;

const rules = [
  { label: "At least 8 characters", test: (p: string) => p.length >= 8 },
  { label: "An uppercase letter", test: (p: string) => /[A-Z]/.test(p) },
  { label: "A lowercase letter", test: (p: string) => /[a-z]/.test(p) },
  { label: "A number", test: (p: string) => /\d/.test(p) },
];

type Phase = { kind: "loading" } | { kind: "invalid"; message: string } | { kind: "ready"; invite: InvitationPreview } | { kind: "done"; invite: InvitationPreview };

export function AcceptInvitation() {
  const token = useRef("");
  const [phase, setPhase] = useState<Phase>({ kind: "loading" });

  useEffect(() => {
    const readToken = () => {
      const fromHash = new URLSearchParams(window.location.hash.slice(1)).get("token") ?? "";
      if (window.location.hash) window.history.replaceState(null, "", window.location.pathname);
      if (!fromHash && token.current) return;
      token.current = fromHash;
      if (!fromHash) {
        setPhase({ kind: "invalid", message: "This page needs the link from your invitation email." });
        return;
      }
      setPhase({ kind: "loading" });
      previewInvitation(fromHash)
        .then((invite) => token.current === fromHash && setPhase({ kind: "ready", invite }))
        .catch((e: unknown) =>
          token.current === fromHash &&
          setPhase({
            kind: "invalid",
            message: e instanceof ApiError ? e.message : "We couldn't check this invitation. Try the link again.",
          }),
        );
    };
    readToken();
    window.addEventListener("hashchange", readToken);
    return () => window.removeEventListener("hashchange", readToken);
  }, []);

  return (
    <main className="flex min-h-screen flex-1 items-start justify-center bg-[#f4f6f9] px-4 py-10 sm:items-center sm:py-16">
      <div className="w-full max-w-md">
        <div className="mb-6 flex justify-center"><Logo /></div>
        <div className="rounded-2xl border border-zinc-200/80 bg-white p-6 shadow-sm sm:p-8">
          {phase.kind === "loading" && <div className="h-40 animate-pulse rounded-xl bg-zinc-100" aria-label="Checking invitation" />}
          {phase.kind === "invalid" && <Invalid message={phase.message} />}
          {phase.kind === "ready" && (
            <SetPassword invite={phase.invite} token={token.current} onDone={() => setPhase({ kind: "done", invite: phase.invite })} />
          )}
          {phase.kind === "done" && <Activated invite={phase.invite} />}
        </div>
      </div>
    </main>
  );
}

function SetPassword({ invite, token, onDone }: { invite: InvitationPreview; token: string; onDone: () => void }) {
  const [formError, setFormError] = useState("");
  const [show, setShow] = useState(false);
  const {
    register,
    handleSubmit,
    setError,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: standardSchemaResolver(schema), defaultValues: { password: "", confirmPassword: "" } });
  const password = watch("password");

  const onSubmit = handleSubmit(async (values) => {
    setFormError("");
    try {
      await acceptInvitation(token, values.password, values.confirmPassword);
      onDone();
    } catch (e) {
      applyApiErrors(e, setError, setFormError);
    }
  });

  return (
    <>
      <p className="text-xs font-semibold uppercase tracking-[0.14em] text-brass">Staff invitation</p>
      <h1 className="mt-2 font-display text-2xl font-semibold text-heading">Welcome to {invite.clinicName}</h1>
      <p className="mt-2 text-sm text-zinc-600">
        Hi {invite.fullName}, you&apos;ve been added as <span className="font-medium text-heading">{roleLabel(invite.role).toLowerCase()}</span>.
        Choose a password to activate your account.
      </p>
      <p className="mt-4 rounded-lg bg-zinc-50 px-3.5 py-2.5 text-sm text-zinc-600">
        Signing in as <span className="font-medium text-heading">{invite.email}</span>
      </p>

      <form onSubmit={onSubmit} noValidate className="mt-6 space-y-4">
        {formError && <Notice tone="error">{formError}</Notice>}
        <input type="email" autoComplete="username" value={invite.email} readOnly hidden />
        <div>
          <label htmlFor="new-password" className="mb-1.5 block text-sm font-medium text-heading">New password</label>
          <div className="relative">
            <input
              id="new-password"
              type={show ? "text" : "password"}
              autoComplete="new-password"
              className={`${fieldClass} pr-12`}
              aria-invalid={Boolean(errors.password)}
              aria-describedby="password-rules"
              {...register("password")}
            />
            <button
              type="button"
              onClick={() => setShow((v) => !v)}
              aria-label={show ? "Hide password" : "Show password"}
              aria-pressed={show}
              className="absolute inset-y-0 right-0 flex w-12 items-center justify-center text-zinc-500 hover:text-heading"
            >
              <EyeIcon open={!show} className="h-5 w-5" />
            </button>
          </div>
          <ul id="password-rules" className="mt-2 grid grid-cols-2 gap-x-3 gap-y-1 text-xs">
            {rules.map((r) => {
              const ok = r.test(password);
              return (
                <li key={r.label} className={ok ? "text-emerald-700" : "text-zinc-500"}>
                  <span aria-hidden="true">{ok ? "✓" : "○"}</span> {r.label}
                </li>
              );
            })}
          </ul>
          {errors.password && <p className="mt-1.5 text-sm text-red-600">{errors.password.message}</p>}
        </div>
        <div>
          <label htmlFor="confirm-password" className="mb-1.5 block text-sm font-medium text-heading">Confirm password</label>
          <input
            id="confirm-password"
            type={show ? "text" : "password"}
            autoComplete="new-password"
            className={fieldClass}
            aria-invalid={Boolean(errors.confirmPassword)}
            {...register("confirmPassword")}
          />
          {errors.confirmPassword && <p className="mt-1.5 text-sm text-red-600">{errors.confirmPassword.message}</p>}
        </div>
        <button type="submit" disabled={isSubmitting} className={`${primaryButton} w-full py-3`}>
          {isSubmitting ? "Activating…" : "Activate account"}
        </button>
      </form>
    </>
  );
}

function Activated({ invite }: { invite: InvitationPreview }) {
  const admin = invite.role === "CLINIC_ADMIN";
  return (
    <div className="text-center">
      <span className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50 text-emerald-700">
        <ShieldIcon className="h-6 w-6" />
      </span>
      <h1 className="mt-4 font-display text-2xl font-semibold text-heading">Your account is active</h1>
      <p className="mt-2 text-sm text-zinc-600">
        You can now sign in to {invite.clinicName} with {invite.email} and your new password.
      </p>
      <Link href={admin ? "/clinic-admin" : "/login"} className={`${primaryButton} mt-6 w-full py-3`}>
        Continue to sign in
      </Link>
    </div>
  );
}

function Invalid({ message }: { message: string }) {
  return (
    <div className="text-center">
      <h1 className="font-display text-2xl font-semibold text-heading">Invitation unavailable</h1>
      <p className="mt-2 text-sm text-zinc-600">{message}</p>
      <p className="mt-4 text-sm text-zinc-500">
        Invitation links work once and expire after 72 hours. Your clinic administrator can send you a new one.
      </p>
      <Link href="/" className="mt-6 inline-block text-sm font-medium text-brand-600 hover:underline">Go to CarePoint</Link>
    </div>
  );
}
