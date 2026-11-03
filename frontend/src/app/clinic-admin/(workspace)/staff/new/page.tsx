import type { Metadata } from "next";

import { AddStaffPage } from "@/features/clinic-admin/AddStaffPage";

export const metadata: Metadata = { title: "Add staff · Clinic admin · CarePoint" };

export default function Page() {
  return <AddStaffPage />;
}
