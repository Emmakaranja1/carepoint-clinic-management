import { describe, expect, it } from "vitest";

import { addStaffSchema } from "./schemas";

describe("addStaffSchema", () => {
  const valid = { fullName: "Faith Akinyi", email: "faith@sunrise.test", phone: "", role: "RECEPTIONIST" };

  it("accepts a receptionist with no phone", () => {
    expect(addStaffSchema.safeParse(valid).success).toBe(true);
  });

  it("accepts an international phone number", () => {
    expect(addStaffSchema.safeParse({ ...valid, phone: "+27710000009" }).success).toBe(true);
  });

  it("rejects missing name, bad email and local-format phone", () => {
    const result = addStaffSchema.safeParse({ ...valid, fullName: " ", email: "nope", phone: "0712345678" });
    expect(result.error?.issues.map((i) => i.path[0])).toEqual(
      expect.arrayContaining(["fullName", "email", "phone"]),
    );
  });

  it("only allows the receptionist role", () => {
    for (const role of ["CLINIC_ADMIN", "DOCTOR", "PATIENT"]) {
      expect(addStaffSchema.safeParse({ ...valid, role }).success).toBe(false);
    }
  });
});
