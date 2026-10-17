import type { ReactNode } from "react";

import { Logo } from "@/components/Logo";

/**
 * Split-screen auth layout per the brand reference: form on the left,
 * a gradient "brand" panel with the tagline on the right (desktop only).
 */
export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <main className="flex flex-1 bg-canvas dark:bg-zinc-950">
      <section className="flex flex-1 flex-col items-center px-6 py-10">
        <div className="w-full max-w-sm">
          <Logo />
          <div className="mt-10">{children}</div>
        </div>
      </section>
      <aside className="relative m-4 hidden w-2/5 overflow-hidden rounded-3xl bg-gradient-to-br from-brand-400 via-brand-500 to-brand-700 lg:block">
        <div className="absolute inset-0 flex flex-col justify-end p-10 text-white">
          <h2 className="max-w-xs text-3xl font-bold leading-tight">
            Quality Healthcare Made Simple
          </h2>
          <p className="mt-3 max-w-xs text-sm text-brand-100">
            “Your health journey matters to us.”
          </p>
        </div>
      </aside>
    </main>
  );
}
