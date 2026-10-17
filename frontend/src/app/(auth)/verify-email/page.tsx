import { Suspense } from "react";
import type { Metadata } from "next";

import { VerifyEmailForm } from "@/features/auth/VerifyEmailForm";

export const metadata: Metadata = { title: "Verify email" };

export default function VerifyEmailPage() {
  return (
    <>
      <h1 className="mb-2 text-center text-2xl font-bold text-heading">Verify your email</h1>
      <p className="mb-6 text-center text-sm text-zinc-500">
        We sent a 6-digit code to your inbox. It expires quickly — paste it here.
      </p>
      <Suspense>
        <VerifyEmailForm />
      </Suspense>
    </>
  );
}
