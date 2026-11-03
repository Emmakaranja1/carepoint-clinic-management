import type { Metadata } from "next";

import { StaffListPage } from "@/features/clinic-admin/StaffListPage";

export const metadata: Metadata = { title: "Staff · Clinic admin · CarePoint" };

export default function Page() {
  return <StaffListPage />;
}
