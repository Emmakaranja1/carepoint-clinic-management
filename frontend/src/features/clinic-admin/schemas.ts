import { z } from "zod";

import { emailSchema } from "@/features/auth/schemas";

export const addStaffSchema = z.object({
  fullName: z.string().trim().min(2, "Enter the staff member's full name").max(120, "Keep it under 120 characters"),
  email: emailSchema.max(254),
  phone: z
    .string()
    .trim()
    .regex(/^\+[1-9]\d{7,14}$/, "Use international format, e.g. +254712345678")
    .optional()
    .or(z.literal("")),
  role: z.literal("RECEPTIONIST"),
});

export type AddStaffValues = z.infer<typeof addStaffSchema>;
