import { z } from "zod";

/** Mirrors the backend's password policy (M2): ≥8, upper, lower, digit. */
export const passwordSchema = z
  .string()
  .min(8, "At least 8 characters")
  .regex(/[A-Z]/, "Needs an uppercase letter")
  .regex(/[a-z]/, "Needs a lowercase letter")
  .regex(/[0-9]/, "Needs a digit");

export const emailSchema = z.string().email("Enter a valid email");

export const otpSchema = z
  .string()
  .regex(/^\d{6}$/, "The code is 6 digits");

export const registerSchema = z
  .object({
    email: emailSchema,
    fullName: z.string().min(1, "Required").max(120),
    phone: z.string().max(30).optional().or(z.literal("")),
    password: passwordSchema,
    confirmPassword: z.string(),
  })
  .refine((v) => v.password === v.confirmPassword, {
    path: ["confirmPassword"],
    message: "Passwords do not match",
  });

export const loginSchema = z.object({
  email: emailSchema,
  password: z.string().min(1, "Required"),
  /** Cosmetic for now — the backend always issues a 30d refresh cookie. */
  rememberMe: z.boolean().optional(),
});

export const verifyEmailSchema = z.object({
  email: emailSchema,
  code: otpSchema,
});

export const forgotPasswordSchema = z.object({
  email: emailSchema,
});

export const resetPasswordSchema = z
  .object({
    email: emailSchema,
    code: otpSchema,
    password: passwordSchema,
    confirmPassword: z.string(),
  })
  .refine((v) => v.password === v.confirmPassword, {
    path: ["confirmPassword"],
    message: "Passwords do not match",
  });

export type RegisterValues = z.infer<typeof registerSchema>;
export type LoginValues = z.infer<typeof loginSchema>;
export type VerifyEmailValues = z.infer<typeof verifyEmailSchema>;
export type ForgotPasswordValues = z.infer<typeof forgotPasswordSchema>;
export type ResetPasswordValues = z.infer<typeof resetPasswordSchema>;
