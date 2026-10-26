import { Suspense } from "react";
import type { Metadata } from "next";

import { ResetPasswordForm } from "@/features/auth/ResetPasswordForm";

export const metadata: Metadata = { title: "Set new password" };

export default function ResetPasswordPage() {
  return (
    <>
      <h1 className="mb-6 text-center text-2xl font-bold text-heading">Set a new password</h1>
      <Suspense>
        <ResetPasswordForm />
      </Suspense>
    </>
  );
}
