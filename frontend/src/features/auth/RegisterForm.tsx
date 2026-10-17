"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { standardSchemaResolver } from "@hookform/resolvers/standard-schema";

import { register as registerAccount } from "./api";
import { registerSchema, type RegisterValues } from "./schemas";
import { Field, applyApiErrors, inputClass, submitClass } from "./ui";

export function RegisterForm() {
  const router = useRouter();
  const [formError, setFormError] = useState("");
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({
    resolver: standardSchemaResolver(registerSchema),
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError("");
    try {
      await registerAccount({
        email: values.email,
        password: values.password,
        confirmPassword: values.confirmPassword,
        fullName: values.fullName,
        phone: values.phone || undefined,
      });
      router.push(`/verify-email?email=${encodeURIComponent(values.email)}`);
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
      <Field label="Full name" error={errors.fullName?.message}>
        <input className={inputClass} autoComplete="name" {...register("fullName")} />
      </Field>
      <Field label="Email" error={errors.email?.message}>
        <input className={inputClass} type="email" autoComplete="email" {...register("email")} />
      </Field>
      <Field label="Phone (optional)" error={errors.phone?.message}>
        <input className={inputClass} type="tel" autoComplete="tel" {...register("phone")} />
      </Field>
      <Field label="Password" error={errors.password?.message}>
        <input className={inputClass} type="password" autoComplete="new-password" {...register("password")} />
      </Field>
      <Field label="Confirm password" error={errors.confirmPassword?.message}>
        <input className={inputClass} type="password" autoComplete="new-password" {...register("confirmPassword")} />
      </Field>
      <button type="submit" className={submitClass} disabled={isSubmitting}>
        {isSubmitting ? "Creating account…" : "Create account"}
      </button>
    </form>
  );
}
