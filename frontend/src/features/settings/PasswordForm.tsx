"use client";

import { useState } from "react";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { standardSchemaResolver } from "@hookform/resolvers/standard-schema";

import type { SessionUser } from "@/features/auth/session";
import { Field, applyApiErrors, inputClass } from "@/features/auth/ui";
import { cardClass, Notice, primaryButtonClass } from "@/features/dashboard/ui";
import { changePassword } from "./api";
import { changePasswordSchema, type ChangePasswordValues } from "./schemas";

export function PasswordForm({ user }: { user: SessionUser }) {
  const [formError, setFormError] = useState("");
  const [saved, setSaved] = useState(false);
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ChangePasswordValues>({ resolver: standardSchemaResolver(changePasswordSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError("");
    setSaved(false);
    try {
      await changePassword(values);
      reset();
      setSaved(true);
    } catch (e) {
      applyApiErrors(e, setError, setFormError);
    }
  });

  return (
    <section className={cardClass} aria-labelledby="password-heading">
      <h2 id="password-heading" className="text-lg font-bold text-heading">Password</h2>
      {!user.passwordSet ? (
        <p className="mt-2 text-sm text-zinc-500">
          You sign in with Google. To also sign in with a password,{" "}
          <Link href="/forgot-password" className="font-semibold text-brand-600 hover:text-brand-700">set one via Forgot password</Link>.
        </p>
      ) : (
        <>
          <p className="mt-1 text-sm text-zinc-500">Changing your password signs you out on every other device.</p>
          <form onSubmit={onSubmit} className="mt-5 space-y-4" noValidate>
            {formError && <Notice tone="error">{formError}</Notice>}
            {saved && <Notice>Password changed. Other devices have been signed out.</Notice>}
            <Field label="Current password" error={errors.currentPassword?.message}>
              <input className={inputClass} type="password" autoComplete="current-password" {...register("currentPassword")} />
            </Field>
            <Field label="New password" error={errors.newPassword?.message}>
              <input className={inputClass} type="password" autoComplete="new-password" {...register("newPassword")} />
            </Field>
            <Field label="Confirm new password" error={errors.confirmPassword?.message}>
              <input className={inputClass} type="password" autoComplete="new-password" {...register("confirmPassword")} />
            </Field>
            <button type="submit" className={primaryButtonClass} disabled={isSubmitting}>
              {isSubmitting ? "Updating…" : "Update password"}
            </button>
          </form>
        </>
      )}
    </section>
  );
}
