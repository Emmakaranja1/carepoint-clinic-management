import type { Metadata } from "next";

import { DoctorAgendaPage } from "@/features/doctor/DoctorAgendaPage";
import { DoctorLayout } from "@/features/doctor/DoctorLayout";

export const metadata: Metadata = { title: "Doctor Agenda · CarePoint" };

export default async function Page({ params }: { params: Promise<{ clinicId: string }> }) {
  const { clinicId } = await params;

  return (
    <DoctorLayout clinicId={clinicId}>
      <DoctorAgendaPage clinicId={clinicId} />
    </DoctorLayout>
  );
}
