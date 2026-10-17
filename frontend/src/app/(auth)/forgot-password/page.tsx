import type { Metadata } from "next";

import { ForgotPasswordForm } from "@/features/auth/ForgotPasswordForm";

export const metadata: Metadata = { title: "Forgot password" };

export default function ForgotPasswordPage() {
  return (
    <>
      <h1 className="mb-2 text-center text-2xl font-bold text-heading">Reset password</h1>
      <p className="mb-6 text-center text-sm text-zinc-500">
        Enter your email and we&apos;ll send a 6-digit reset code.
      </p>
      <ForgotPasswordForm />
    </>
  );
}
