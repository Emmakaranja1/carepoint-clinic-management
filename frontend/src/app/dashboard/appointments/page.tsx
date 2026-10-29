import type { Metadata } from "next";

import { AppointmentsPage } from "@/features/appointments/AppointmentsPage";

export const metadata: Metadata = { title: "Appointments" };

export default async function Page({ searchParams }: { searchParams: Promise<{ booked?: string }> }) {
  const { booked } = await searchParams;
  return <AppointmentsPage justBooked={booked === "1"} />;
}
