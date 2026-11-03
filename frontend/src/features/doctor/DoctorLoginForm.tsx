"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { standardSchemaResolver } from "@hookform/resolvers/standard-schema";

import { loginSchema, type LoginValues } from "@/features/auth/schemas";
import { login } from "@/features/auth/api";
import { useSession } from "@/features/auth/session";
import { applyApiErrors } from "@/features/auth/ui";
import { EyeIcon } from "@/features/clinic-admin/icons";

const inputClass =
  "w-full rounded-lg border border-zinc-300 bg-white px-3.5 py-3 text-base text-heading outline-none transition " +
  "placeholder:text-zinc-400 focus:border-brand-500 focus:ring-4 focus:ring-brand-500/15 aria-[invalid=true]:border-red-400";

export function DoctorLoginForm({ next }: { next?: string }) {
  const router = useRouter();
  const { user, status, hydrate, setUser } = useSession();
  const [formError, setFormError] = useState("");
  const [showPassword, setShowPassword] = useState(false);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({ resolver: standardSchemaResolver(loginSchema) });

  useEffect(() => {
    void hydrate();
  }, [hydrate]);

  useEffect(() => {
    if (status !== "authenticated" || !user) return;
    const clinicId = Object.entries(user.clinicRoles).find(([, role]) => role === "DOCTOR")?.[0];

    if (clinicId) {
      router.replace(next && next.startsWith("/doctor/") ? next : `/doctor/${clinicId}/agenda`);
    }
  }, [next, router, status, user]);

  const onSubmit = handleSubmit(async (values) => {
    setFormError("");
    try {
      const signedIn = await login(values.email, values.password);
      setUser(signedIn);
      const clinicId = Object.entries(signedIn.clinicRoles).find(([, role]) => role === "DOCTOR")?.[0];

      if (!clinicId) {
        setFormError("This account does not have doctor access.");
        return;
      }

      router.replace(next && next.startsWith("/doctor/") ? next : `/doctor/${clinicId}/agenda`);
    } catch (e) {
      applyApiErrors(e, setError, setFormError);
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      {formError && (
        <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {formError}
        </p>
      )}

      <div>
        <label htmlFor="doctor-email" className="mb-1.5 block text-sm font-medium text-heading">
          Work email
        </label>
        <input
          id="doctor-email"
          type="email"
          autoComplete="username"
          placeholder="you@clinic.com"
          className={inputClass}
          aria-invalid={Boolean(errors.email)}
          aria-describedby={errors.email ? "doctor-email-error" : undefined}
          {...register("email")}
        />
        {errors.email && <p id="doctor-email-error" className="mt-1.5 text-sm text-red-600">{errors.email.message}</p>}
      </div>

      <div>
        <div className="mb-1.5 flex items-center justify-between">
          <label htmlFor="doctor-password" className="text-sm font-medium text-heading">Password</label>
          <Link href="/forgot-password" className="text-sm font-medium text-brand-600 hover:underline">
            Forgot password?
          </Link>
        </div>
        <div className="relative">
          <input
            id="doctor-password"
            type={showPassword ? "text" : "password"}
            autoComplete="current-password"
            className={`${inputClass} pr-12`}
            aria-invalid={Boolean(errors.password)}
            aria-describedby={errors.password ? "doctor-password-error" : undefined}
            {...register("password")}
          />
          <button
            type="button"
            onClick={() => setShowPassword((v) => !v)}
            aria-label={showPassword ? "Hide password" : "Show password"}
            aria-pressed={showPassword}
            className="absolute inset-y-0 right-0 flex w-12 items-center justify-center rounded-r-lg text-zinc-500 hover:text-heading"
          >
            <EyeIcon open={!showPassword} className="h-5 w-5" />
          </button>
        </div>
        {errors.password && (
          <p id="doctor-password-error" className="mt-1.5 text-sm text-red-600">{errors.password.message}</p>
        )}
      </div>

      <button
        type="submit"
        disabled={isSubmitting}
        className="flex w-full items-center justify-center rounded-lg bg-navy px-4 py-3 text-base font-semibold text-white transition hover:bg-heading disabled:cursor-not-allowed disabled:opacity-60"
      >
        {isSubmitting ? "Signing in…" : "Sign in as doctor"}
      </button>
    </form>
  );
}
