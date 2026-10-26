import { describe, expect, it } from "vitest";

import { otpSchema, passwordSchema, registerSchema } from "./schemas";

describe("passwordSchema", () => {
  it("accepts a policy-conforming password", () => {
    expect(passwordSchema.safeParse("Clinic2026").success).toBe(true);
  });

  it.each(["short", "alllowercase1", "ALLUPPER1", "NoDigitsHere"])(
    "rejects %s",
    (pwd) => {
      expect(passwordSchema.safeParse(pwd).success).toBe(false);
    },
  );
});

describe("otpSchema", () => {
  it("accepts exactly 6 digits", () => {
    expect(otpSchema.safeParse("123456").success).toBe(true);
    expect(otpSchema.safeParse("12345").success).toBe(false);
    expect(otpSchema.safeParse("abcdef").success).toBe(false);
  });
});

describe("registerSchema", () => {
  const base = {
    email: "patient@test.local",
    fullName: "Test Patient",
    password: "Clinic2026",
    confirmPassword: "Clinic2026",
  };

  it("rejects mismatched passwords on confirmPassword", () => {
    const result = registerSchema.safeParse({
      ...base,
      confirmPassword: "Different1",
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0].path).toEqual(["confirmPassword"]);
    }
  });

  it("treats an empty phone as absent", () => {
    expect(registerSchema.safeParse({ ...base, phone: "" }).success).toBe(true);
  });
});
