import type { Metadata } from "next";

import { StaffDetailPage } from "@/features/clinic-admin/StaffDetailPage";

export const metadata: Metadata = { title: "Staff member · Clinic admin · CarePoint" };

export default async function Page({ params }: { params: Promise<{ membershipId: string }> }) {
  const { membershipId } = await params;
  return <StaffDetailPage membershipId={membershipId} />;
}
