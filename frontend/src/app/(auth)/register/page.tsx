"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";

import { RegisterForm } from "@/features/auth/RegisterForm";
import { GoogleButton } from "@/features/auth/GoogleButton";
import { useSession } from "@/features/auth/session";

export default function RegisterPage() {
  const router = useRouter();
  const { hydrate } = useSession();
  const [error, setError] = useState("");

  const handleGoogleSuccess = async (user: { fullName: string; email: string }) => {
    await hydrate();
    router.push("/");
  };

  const handleGoogleError = (message: string) => {
    setError(message);
  };

  return (
    <>
      <h1 className="mb-3 text-3xl font-bold text-heading">Create your account</h1>
      <p className="mb-8 text-base text-zinc-500">
        Book appointments, consult with trusted doctors
      </p>
      {error && (
        <p className="mb-4 rounded-md bg-red-50 px-4 py-3 text-base text-red-700 dark:bg-red-950 dark:text-red-300">
          {error}
        </p>
      )}
      <GoogleButton onSuccess={handleGoogleSuccess} onError={handleGoogleError} />
      <div className="my-8 flex items-center gap-4">
        <div className="flex-1 border-t border-zinc-200 dark:border-zinc-800" />
        <span className="text-sm text-zinc-400">or</span>
        <div className="flex-1 border-t border-zinc-200 dark:border-zinc-800" />
      </div>
      <RegisterForm />
      <p className="mt-6 text-center text-base text-zinc-500">
        Already registered?{" "}
        <Link href="/login" className="text-brand-600 hover:underline dark:text-brand-300">
          Sign in
        </Link>
      </p>
    </>
  );
}
