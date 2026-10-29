"use client";

import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { standardSchemaResolver } from "@hookform/resolvers/standard-schema";

import { resendCode, resetPassword, verifyResetCode } from "./api";
import { resetPasswordSchema, type ResetPasswordValues } from "./schemas";
import { Field, applyApiErrors, inputClass, submitClass } from "./ui";

export function ResetPasswordForm() {
  const router = useRouter();
  const params = useSearchParams();
  const [formError, setFormError] = useState("");
  const [step, setStep] = useState<"code" | "password">("code");
  const [verifying, setVerifying] = useState(false);
  const [resending, setResending] = useState(false);
  const [resent, setResent] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const {
    register,
    handleSubmit,
    setError,
    getValues,
    trigger,
    formState: { errors, isSubmitting },
  } = useForm<ResetPasswordValues>({
    resolver: standardSchemaResolver(resetPasswordSchema),
    defaultValues: { email: params.get("email") ?? "", code: "", password: "", confirmPassword: "" },
  });

  const verifyCode = async () => {
    setFormError("");
    setResent(false);
    if (!(await trigger("code"))) {
      return;
    }
    setVerifying(true);
    try {
      await verifyResetCode(getValues("email"), getValues("code"));
      setStep("password");
    } catch (e) {
      applyApiErrors(e, setError, setFormError);
    } finally {
      setVerifying(false);
    }
  };

  const resend = async () => {
    setFormError("");
    setResending(true);
    try {
      await resendCode(getValues("email"), "PASSWORD_RESET");
      setResent(true);
    } catch (e) {
      applyApiErrors(e, setError, setFormError);
    } finally {
      setResending(false);
    }
  };

  const onSubmit = handleSubmit(async (values) => {
    setFormError("");
    try {
      await resetPassword(values);
      router.push("/login?reset=1");
    } catch (e) {
      applyApiErrors(e, setError, setFormError);
    }
  });

  if (step === "code") {
    return (
      <form onSubmit={(e) => e.preventDefault()} className="space-y-4" noValidate>
        {formError && (
          <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
            {formError}
          </p>
        )}
        {resent && (
          <p className="rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300">
            A new code was sent to your email.
          </p>
        )}
        <p className="text-sm text-zinc-500">
          Enter the 6-digit code sent to{" "}
          <span className="font-medium text-heading">{getValues("email")}</span>.
        </p>
        <input type="hidden" {...register("email")} />
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
        <button type="button" className={submitClass} disabled={verifying} onClick={verifyCode}>
          {verifying ? "Verifying…" : "Verify code"}
        </button>
        <p className="text-center text-sm text-zinc-500">
          Didn&apos;t get a code?{" "}
          <button
            type="button"
            onClick={resend}
            disabled={resending}
            className="font-semibold text-brand-500 hover:underline disabled:opacity-50 dark:text-brand-300"
          >
            {resending ? "Sending…" : "Resend"}
          </button>
        </p>
      </form>
    );
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4" noValidate>
      {formError && (
        <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
          {formError}
        </p>
      )}
      <input type="hidden" {...register("email")} />
      <input type="hidden" {...register("code")} />
      <Field label="New password" error={errors.password?.message}>
        <div className="relative">
          <input
            className={inputClass}
            type={showPassword ? "text" : "password"}
            autoComplete="new-password"
            {...register("password")}
          />
          <button
            type="button"
            onClick={() => setShowPassword(!showPassword)}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-zinc-400 hover:text-zinc-600 dark:text-zinc-500 dark:hover:text-zinc-300"
          >
            {showPassword ? (
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="h-5 w-5">
                <path strokeLinecap="round" strokeLinejoin="round" d="M3.98 8.223A10.477 10.477 0 001.934 12C3.226 16.338 7.244 19.5 12 19.5c.993 0 1.953-.138 2.863-.395M6.228 6.228A10.45 10.45 0 0112 4.5c4.756 0 8.773 3.162 10.065 7.498a10.523 10.523 0 01-4.293 5.774M6.228 6.228L3 3m3.228 3.228l3.65 3.65m7.894 7.894L21 21m-3.228-3.228l-3.65-3.65m0 0a3 3 0 10-4.243-4.243m4.242 4.242L9.88 9.88" />
              </svg>
            ) : (
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="h-5 w-5">
                <path strokeLinecap="round" strokeLinejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" />
                <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
            )}
          </button>
        </div>
      </Field>
      <Field label="Confirm new password" error={errors.confirmPassword?.message}>
        <div className="relative">
          <input
            className={inputClass}
            type={showConfirmPassword ? "text" : "password"}
            autoComplete="new-password"
            {...register("confirmPassword")}
          />
          <button
            type="button"
            onClick={() => setShowConfirmPassword(!showConfirmPassword)}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-zinc-400 hover:text-zinc-600 dark:text-zinc-500 dark:hover:text-zinc-300"
          >
            {showConfirmPassword ? (
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="h-5 w-5">
                <path strokeLinecap="round" strokeLinejoin="round" d="M3.98 8.223A10.477 10.477 0 001.934 12C3.226 16.338 7.244 19.5 12 19.5c.993 0 1.953-.138 2.863-.395M6.228 6.228A10.45 10.45 0 0112 4.5c4.756 0 8.773 3.162 10.065 7.498a10.523 10.523 0 01-4.293 5.774M6.228 6.228L3 3m3.228 3.228l3.65 3.65m7.894 7.894L21 21m-3.228-3.228l-3.65-3.65m0 0a3 3 0 10-4.243-4.243m4.242 4.242L9.88 9.88" />
              </svg>
            ) : (
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="h-5 w-5">
                <path strokeLinecap="round" strokeLinejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" />
                <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
            )}
          </button>
        </div>
      </Field>
      <button type="submit" className={submitClass} disabled={isSubmitting}>
        {isSubmitting ? "Resetting…" : "Reset password"}
      </button>
    </form>
  );
}
