import type { Metadata } from "next";

import { AcceptInvitation } from "@/features/invitations/AcceptInvitation";

export const metadata: Metadata = { title: "Activate your account · CarePoint", referrer: "no-referrer" };

export default function Page() {
  return <AcceptInvitation />;
}
