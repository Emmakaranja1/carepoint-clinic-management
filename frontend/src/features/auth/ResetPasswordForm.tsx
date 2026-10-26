"use client";

import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { standardSchemaResolver } from "@hookform/resolvers/standard-schema";

import { resetPassword } from "./api";
import { resetPasswordSchema, type ResetPasswordValues } from "./schemas";
import { Field, applyApiErrors, inputClass, submitClass } from "./ui";

export function ResetPasswordForm() {
  const router = useRouter();
  const params = useSearchParams();
  const [formError, setFormError] = useState("");
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ResetPasswordValues>({
    resolver: standardSchemaResolver(resetPasswordSchema),
    defaultValues: { email: params.get("email") ?? "", code: "", password: "", confirmPassword: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError("");
    try {
      await resetPassword(values);
      router.push("/login?reset=1");
    } catch (e) {
      applyApiErrors(e, setError, setFormError);
    }
  });

  return (
    <form onSubmit={onSubmit} className="space-y-4" noValidate>
      {formError && (
        <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
          {formError}
        </p>
      )}
      <Field label="Email" error={errors.email?.message}>
        <input className={inputClass} type="email" autoComplete="email" {...register("email")} />
      </Field>
      <Field label="Reset code" error={errors.code?.message}>
        <input
          className={inputClass}
          inputMode="numeric"
          maxLength={6}
          autoComplete="one-time-code"
          placeholder="123456"
          {...register("code")}
        />
      </Field>
      <Field label="New password" error={errors.password?.message}>
        <input className={inputClass} type="password" autoComplete="new-password" {...register("password")} />
      </Field>
      <Field label="Confirm new password" error={errors.confirmPassword?.message}>
        <input className={inputClass} type="password" autoComplete="new-password" {...register("confirmPassword")} />
      </Field>
      <button type="submit" className={submitClass} disabled={isSubmitting}>
        {isSubmitting ? "Resetting…" : "Reset password"}
      </button>
    </form>
  );
}
