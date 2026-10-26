import type { Metadata } from "next";

import { LoginForm } from "@/features/auth/LoginForm";

export const metadata: Metadata = { title: "Sign in" };

export default function LoginPage() {
  return (
    <>
      <h1 className="mb-3 text-3xl font-bold text-heading">Welcome Back</h1>
      <p className="mb-8 text-base text-zinc-500">
        Log in to your account to continue
      </p>
      <LoginForm />
    </>
  );
}
