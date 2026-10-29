import { describe, expect, it } from "vitest";

import { changePasswordSchema, profileSchema } from "./schemas";

describe("profileSchema", () => {
  it("accepts an empty phone and trims the name", () => {
    const result = profileSchema.safeParse({ fullName: "  Amani Demo ", phone: "" });
    expect(result.success && result.data.fullName).toBe("Amani Demo");
  });

  it("rejects a blank name and a malformed phone", () => {
    const result = profileSchema.safeParse({ fullName: "  ", phone: "0712345678" });
    expect(result.success).toBe(false);
    expect(result.error?.issues.map((i) => i.path[0])).toEqual(expect.arrayContaining(["fullName", "phone"]));
  });
});

describe("changePasswordSchema", () => {
  const valid = { currentPassword: "Old-Pass-1", newPassword: "New-Pass-2", confirmPassword: "New-Pass-2" };

  it("accepts a strong, confirmed new password", () => {
    expect(changePasswordSchema.safeParse(valid).success).toBe(true);
  });

  it("rejects mismatched confirmation", () => {
    const result = changePasswordSchema.safeParse({ ...valid, confirmPassword: "Other-Pass-3" });
    expect(result.error?.issues[0].path).toEqual(["confirmPassword"]);
  });

  it("rejects reusing the current password", () => {
    const result = changePasswordSchema.safeParse({ ...valid, newPassword: "Old-Pass-1", confirmPassword: "Old-Pass-1" });
    expect(result.error?.issues.some((i) => i.path[0] === "newPassword")).toBe(true);
  });
});
