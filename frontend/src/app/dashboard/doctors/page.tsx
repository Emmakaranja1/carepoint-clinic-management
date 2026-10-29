import type { Metadata } from "next";

import { DoctorSearch } from "@/features/discovery/DoctorSearch";
import { PageHeader } from "@/features/dashboard/ui";

export const metadata: Metadata = { title: "Doctors" };

export default async function Page({ searchParams }: { searchParams: Promise<{ q?: string }> }) {
  const { q } = await searchParams;
  const query = q?.slice(0, 200) ?? "";
  return (
    <>
      <PageHeader title="Doctors" description="Find a doctor and book an available slot." />
      <DoctorSearch key={query} initialQuery={query} />
    </>
  );
}
