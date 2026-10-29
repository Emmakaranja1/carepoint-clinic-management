"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { standardSchemaResolver } from "@hookform/resolvers/standard-schema";

import { GoogleButton } from "./GoogleButton";
import { login } from "./api";
import { loginSchema, type LoginValues } from "./schemas";
import { useSession } from "./session";
import { Field, applyApiErrors, inputClass, submitClass } from "./ui";

export function LoginForm({ notice, next = "/dashboard" }: { notice?: string | null; next?: string }) {
  const router = useRouter();
  const setUser = useSession((s) => s.setUser);
  const [formError, setFormError] = useState("");
  const [noticeVisible, setNoticeVisible] = useState(Boolean(notice));
  const [showPassword, setShowPassword] = useState(false);

  useEffect(() => {
    if (!notice) {
      return;
    }
    const timer = setTimeout(() => {
      setNoticeVisible(false);
      router.replace(next === "/dashboard" ? "/login" : `/login?next=${encodeURIComponent(next)}`);
    }, 6000);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- router is stable
  }, [notice]);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: standardSchemaResolver(loginSchema),
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError("");
    try {
      const user = await login(values.email, values.password);
      setUser(user);
      router.push(next);
    } catch (e) {
      applyApiErrors(e, setError, setFormError);
    }
  });

  return (
    <div className="space-y-4">
      {notice && noticeVisible && (
        <p className="rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300">
          {notice}
        </p>
      )}
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        {formError && (
          <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
            {formError}
          </p>
        )}
        <Field label="Email" error={errors.email?.message}>
          <input className={inputClass} type="email" autoComplete="email" {...register("email")} />
        </Field>
        <Field label="Password" error={errors.password?.message}>
          <div className="relative">
            <input
              className={inputClass}
              type={showPassword ? "text" : "password"}
              autoComplete="current-password"
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
        <div className="flex items-center justify-between text-sm">
          <label className="flex items-center gap-2 text-zinc-600 dark:text-zinc-400">
            <input
              type="checkbox"
              className="h-4 w-4 rounded border-zinc-300 accent-brand-500"
              {...register("rememberMe")}
            />
            Remember me
          </label>
          <Link href="/forgot-password" className="text-brand-500 hover:underline dark:text-brand-300">
            Forgot password?
          </Link>
        </div>
        <button type="submit" className={submitClass} disabled={isSubmitting}>
          {isSubmitting ? "Signing in…" : "Sign in"}
        </button>
        <div className="flex items-center gap-3 text-xs text-zinc-400">
          <span className="h-px flex-1 bg-zinc-200 dark:bg-zinc-800" />
          or
          <span className="h-px flex-1 bg-zinc-200 dark:bg-zinc-800" />
        </div>
        <GoogleButton
          onSuccess={(user) => {
            setUser(user);
            router.push(next);
          }}
          onError={setFormError}
        />
        <p className="text-center text-sm text-zinc-500">
          Don&apos;t have an account?{" "}
          <Link href="/register" className="font-semibold text-brand-500 hover:underline dark:text-brand-300">
            Sign up
          </Link>
        </p>
      </form>
    </div>
  );
}
