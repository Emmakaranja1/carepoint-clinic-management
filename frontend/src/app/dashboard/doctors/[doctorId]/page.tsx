import type { Metadata } from "next";

import { BookDoctor } from "@/features/booking/BookDoctor";

export const metadata: Metadata = { title: "Book appointment" };

export default async function Page({ params }: { params: Promise<{ doctorId: string }> }) {
  const { doctorId } = await params;
  return <BookDoctor doctorId={doctorId} />;
}
