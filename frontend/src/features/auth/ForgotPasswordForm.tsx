"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { standardSchemaResolver } from "@hookform/resolvers/standard-schema";

import { forgotPassword } from "./api";
import { forgotPasswordSchema, type ForgotPasswordValues } from "./schemas";
import { Field, applyApiErrors, inputClass, submitClass } from "./ui";

export function ForgotPasswordForm() {
  const router = useRouter();
  const [formError, setFormError] = useState("");
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ForgotPasswordValues>({
    resolver: standardSchemaResolver(forgotPasswordSchema),
  });

  // The backend always returns 204 — whether or not the account exists.
  const onSubmit = handleSubmit(async (values) => {
    setFormError("");
    try {
      await forgotPassword(values.email);
      router.push(`/reset-password?email=${encodeURIComponent(values.email)}`);
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
      <button type="submit" className={submitClass} disabled={isSubmitting}>
        {isSubmitting ? "Sending…" : "Send reset code"}
      </button>
    </form>
  );
}
