import type { Metadata } from "next";
import Link from "next/link";

import { RegisterForm } from "@/features/auth/RegisterForm";

export const metadata: Metadata = { title: "Create account" };

export default function RegisterPage() {
  return (
    <>
      <h1 className="mb-2 text-2xl font-bold text-heading">Create your account</h1>
      <p className="mb-8 text-sm text-zinc-500">
        Book appointments, consult with trusted doctors
      </p>
      <RegisterForm />
      <p className="mt-4 text-center text-sm text-zinc-500">
        Already registered?{" "}
        <Link href="/login" className="text-brand-600 hover:underline dark:text-brand-300">
          Sign in
        </Link>
      </p>
    </>
  );
}
