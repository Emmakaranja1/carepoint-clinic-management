import { z } from "zod";

import { passwordSchema } from "@/features/auth/schemas";

export const profileSchema = z.object({
  fullName: z.string().trim().min(1, "Required").max(120, "At most 120 characters"),
  phone: z
    .string()
    .trim()
    .regex(/^\+2547\d{8}$/, "Enter a valid number, e.g. +254712345678")
    .or(z.literal("")),
});

export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, "Required"),
    newPassword: passwordSchema,
    confirmPassword: z.string(),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    path: ["confirmPassword"],
    message: "Passwords do not match",
  })
  .refine((v) => v.newPassword !== v.currentPassword, {
    path: ["newPassword"],
    message: "Choose a password you haven't used here",
  });

export type ProfileValues = z.infer<typeof profileSchema>;
export type ChangePasswordValues = z.infer<typeof changePasswordSchema>;
