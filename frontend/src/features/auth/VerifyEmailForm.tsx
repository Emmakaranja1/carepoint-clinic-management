"use client";

import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { standardSchemaResolver } from "@hookform/resolvers/standard-schema";

import { ApiError } from "@/lib/api";

import { resendCode, verifyEmail } from "./api";
import { verifyEmailSchema, type VerifyEmailValues } from "./schemas";
import { useSession } from "./session";
import { Field, applyApiErrors, inputClass, submitClass } from "./ui";

export function VerifyEmailForm() {
  const router = useRouter();
  const params = useSearchParams();
  const setUser = useSession((s) => s.setUser);
  const [formError, setFormError] = useState("");
  const [resent, setResent] = useState(false);
  const {
    register,
    handleSubmit,
    setError,
    getValues,
    formState: { errors, isSubmitting },
  } = useForm<VerifyEmailValues>({
    resolver: standardSchemaResolver(verifyEmailSchema),
    defaultValues: { email: params.get("email") ?? "", code: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError("");
    try {
      const user = await verifyEmail(values.email, values.code);
      setUser(user);
      router.push("/");
    } catch (e) {
      applyApiErrors(e, setError, setFormError);
    }
  });

  const resend = async () => {
    setResent(false);
    try {
      await resendCode(getValues("email"), "VERIFY_EMAIL");
      setResent(true);
    } catch (e) {
      setFormError(e instanceof ApiError ? e.message : "Could not resend the code");
    }
  };

  return (
    <form onSubmit={onSubmit} className="space-y-4" noValidate>
      {formError && (
        <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
          {formError}
        </p>
      )}
      {resent && (
        <p className="rounded-md bg-mint/15 px-3 py-2 text-sm text-mint-foreground">
          A new code is on its way.
        </p>
      )}
      <Field label="Email" error={errors.email?.message}>
        <input className={inputClass} type="email" autoComplete="email" {...register("email")} />
      </Field>
      <Field label="Verification code" error={errors.code?.message}>
        <input
          className={inputClass}
          inputMode="numeric"
          maxLength={6}
          autoComplete="one-time-code"
          placeholder="123456"
          {...register("code")}
        />
      </Field>
      <button type="submit" className={submitClass} disabled={isSubmitting}>
        {isSubmitting ? "Verifying…" : "Verify email"}
      </button>
      <button
        type="button"
        onClick={resend}
        className="w-full text-sm text-brand-600 hover:underline dark:text-brand-300"
      >
        Resend code
      </button>
    </form>
  );
}
