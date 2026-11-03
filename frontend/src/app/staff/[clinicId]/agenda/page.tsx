import type { Metadata } from "next";

import { StaffAgendaPage } from "@/features/staff/StaffAgendaPage";
import { StaffLayout } from "@/features/staff/StaffLayout";

export const metadata: Metadata = { title: "Front Desk Agenda · CarePoint" };

export default async function Page({ params }: { params: Promise<{ clinicId: string }> }) {
  const { clinicId } = await params;

  return (
    <StaffLayout clinicId={clinicId}>
      <StaffAgendaPage clinicId={clinicId} />
    </StaffLayout>
  );
}
