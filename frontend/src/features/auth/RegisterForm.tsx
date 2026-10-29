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
  const [showPassword, setShowPassword] = useState(false);
  const [phoneDigits, setPhoneDigits] = useState("");
  const {
    register,
    handleSubmit,
    setError,
    setValue,
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
      router.push("/login?registered=1");
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
      <Field label="Phone" error={errors.phone?.message}>
        <div className="flex">
          <span className="inline-flex items-center rounded-l-lg border border-r-0 border-zinc-300 bg-zinc-50 px-3 text-base text-zinc-600 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-300">
            +254
          </span>
          <input
            className={`${inputClass} rounded-l-none`}
            type="tel"
            inputMode="numeric"
            autoComplete="tel-national"
            placeholder="712 345 678"
            value={phoneDigits.replace(/(\d{3})(?=\d)/g, "$1 ")}
            onChange={(e) => {
              const digits = e.target.value.replace(/\D/g, "").slice(0, 9);
              setPhoneDigits(digits);
              setValue("phone", digits ? `+254${digits}` : "", { shouldValidate: true });
            }}
          />
        </div>
        <input type="hidden" {...register("phone")} />
      </Field>
      <Field label="Password" error={errors.password?.message}>
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
            className="absolute right-3 top-1/2 -translate-y-1/2 text-zinc-400 hover:text-zinc-600 dark:text-zinc-500 dark:hover:text-zinc-300 p-1"
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
      <Field label="Confirm password" error={errors.confirmPassword?.message}>
        <input className={inputClass} type="password" autoComplete="new-password" {...register("confirmPassword")} />
      </Field>
      <button type="submit" className={submitClass} disabled={isSubmitting}>
        {isSubmitting ? "Creating account…" : "Create account"}
      </button>
    </form>
  );
}
