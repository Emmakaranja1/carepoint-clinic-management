import type { Metadata } from "next";

import { OverviewPage } from "@/features/clinic-admin/OverviewPage";

export const metadata: Metadata = { title: "Overview · Clinic admin · CarePoint" };

export default function Page() {
  return <OverviewPage />;
}
