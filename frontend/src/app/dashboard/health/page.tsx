import type { Metadata } from "next";

import { HealthPage } from "@/features/health/HealthPage";

export const metadata: Metadata = { title: "My Health" };

export default function Page() {
  return <HealthPage />;
}
