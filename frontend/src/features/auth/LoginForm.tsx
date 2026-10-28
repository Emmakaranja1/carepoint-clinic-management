"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { standardSchemaResolver } from "@hookform/resolvers/standard-schema";

import { GoogleButton } from "./GoogleButton";
import { login } from "./api";
import { loginSchema, type LoginValues } from "./schemas";
import { useSession } from "./session";
import { Field, applyApiErrors, inputClass, submitClass } from "./ui";

export function LoginForm() {
  const router = useRouter();
  const setUser = useSession((s) => s.setUser);
  const [formError, setFormError] = useState("");
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
      router.push("/");
    } catch (e) {
      applyApiErrors(e, setError, setFormError);
    }
  });

  return (
    <div className="space-y-4">
      <GoogleButton
        onSuccess={(user) => {
          setUser(user);
          router.push("/");
        }}
        onError={setFormError}
      />
      <div className="flex items-center gap-3 text-xs text-zinc-400">
        <span className="h-px flex-1 bg-zinc-200 dark:bg-zinc-800" />
        or
        <span className="h-px flex-1 bg-zinc-200 dark:bg-zinc-800" />
      </div>
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
          <input className={inputClass} type="password" autoComplete="current-password" {...register("password")} />
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
