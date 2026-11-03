"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

import { Header } from "@/components/Header";
import { Footer } from "@/components/Footer";
import { FeaturedDoctors } from "@/features/home/FeaturedDoctors";
import { Hero } from "@/features/home/Hero";
import { ClosingCta, HowItWorks, Proof } from "@/features/home/HomeStory";
import { Specialties } from "@/features/home/Specialties";
import { TrustBar } from "@/features/home/TrustBar";
import { getWorkspaceHref, useSession } from "@/features/auth/session";

export default function Home() {
  const router = useRouter();
  const { user, status } = useSession();

  useEffect(() => {
    if (status !== "authenticated" || !user) return;
    const next = getWorkspaceHref(user);
    if (next !== "/dashboard") {
      router.replace(next);
    }
  }, [router, status, user]);

  return (
    <>
      <Header />
      <main id="main" className="flex-1">
        <Hero />
        <TrustBar />
        <Specialties />
        <FeaturedDoctors />
        <HowItWorks />
        <Proof />
        <ClosingCta />
      </main>
      <Footer />
    </>
  );
}
