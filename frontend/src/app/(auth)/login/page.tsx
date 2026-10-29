import type { Metadata } from "next";

import { LoginForm } from "@/features/auth/LoginForm";

export const metadata: Metadata = { title: "Sign in" };

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ registered?: string; reset?: string; next?: string }>;
}) {
  const { registered, reset, next: nextParam } = await searchParams;
  const next = nextParam?.startsWith("/") && !nextParam.startsWith("//")
    ? nextParam
    : "/dashboard";
  return (
    <>
      <h1 className="mb-3 text-3xl font-bold text-heading">Welcome Back</h1>
      <p className="mb-8 text-base text-zinc-500">
        Log in to your account to continue
      </p>
      <LoginForm
        next={next}
        notice={
          registered === "1"
            ? "Your account was created successfully. Sign in with your email and password."
            : reset === "1"
              ? "Your password was reset. Sign in with your new password."
              : null
        }
      />
    </>
  );
}
