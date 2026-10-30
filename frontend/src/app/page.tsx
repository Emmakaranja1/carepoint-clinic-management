import { Header } from "@/components/Header";
import { Footer } from "@/components/Footer";
import { FeaturedDoctors } from "@/features/home/FeaturedDoctors";
import { Hero } from "@/features/home/Hero";
import { ClosingCta, HowItWorks, Proof } from "@/features/home/HomeStory";
import { Specialties } from "@/features/home/Specialties";
import { TrustBar } from "@/features/home/TrustBar";

export default function Home() {
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
