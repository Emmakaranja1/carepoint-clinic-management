"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { standardSchemaResolver } from "@hookform/resolvers/standard-schema";

import { loginSchema, type LoginValues } from "@/features/auth/schemas";
import { useSession } from "@/features/auth/session";
import { applyApiErrors } from "@/features/auth/ui";
import { clinicAdminLogin } from "./api";
import { EyeIcon } from "./icons";
import { Notice } from "./ui";

const inputClass =
  "w-full rounded-lg border border-zinc-300 bg-white px-3.5 py-3 text-base text-heading outline-none transition " +
  "placeholder:text-zinc-400 focus:border-brand-500 focus:ring-4 focus:ring-brand-500/15 aria-[invalid=true]:border-red-400";

export function AdminLoginForm({ next, notice }: { next: string; notice: string | null }) {
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
    if (status === "authenticated" && user && Object.values(user.clinicRoles).includes("CLINIC_ADMIN")) {
      router.replace(next);
    }
  }, [next, router, status, user]);

  const onSubmit = handleSubmit(async (values) => {
    setFormError("");
    try {
      const signedIn = await clinicAdminLogin(values.email, values.password);
      setUser(signedIn);
      router.replace(next);
    } catch (e) {
      applyApiErrors(e, setError, setFormError);
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      {notice && <Notice tone="info">{notice}</Notice>}
      {formError && <Notice tone="error">{formError}</Notice>}

      <div>
        <label htmlFor="admin-email" className="mb-1.5 block text-sm font-medium text-heading">
          Work email
        </label>
        <input
          id="admin-email"
          type="email"
          autoComplete="username"
          placeholder="you@clinic.com"
          className={inputClass}
          aria-invalid={Boolean(errors.email)}
          aria-describedby={errors.email ? "admin-email-error" : undefined}
          {...register("email")}
        />
        {errors.email && <p id="admin-email-error" className="mt-1.5 text-sm text-red-600">{errors.email.message}</p>}
      </div>

      <div>
        <div className="mb-1.5 flex items-center justify-between">
          <label htmlFor="admin-password" className="text-sm font-medium text-heading">Password</label>
          <Link href="/forgot-password" className="text-sm font-medium text-brand-600 hover:underline">
            Forgot password?
          </Link>
        </div>
        <div className="relative">
          <input
            id="admin-password"
            type={showPassword ? "text" : "password"}
            autoComplete="current-password"
            className={`${inputClass} pr-12`}
            aria-invalid={Boolean(errors.password)}
            aria-describedby={errors.password ? "admin-password-error" : undefined}
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
          <p id="admin-password-error" className="mt-1.5 text-sm text-red-600">{errors.password.message}</p>
        )}
      </div>

      <button
        type="submit"
        disabled={isSubmitting}
        className="flex w-full items-center justify-center rounded-lg bg-navy px-4 py-3 text-base font-semibold text-white transition hover:bg-heading disabled:cursor-not-allowed disabled:opacity-60"
      >
        {isSubmitting ? "Signing in…" : "Sign in"}
      </button>
    </form>
  );
}
