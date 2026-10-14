import { describe, expect, it } from "vitest";

import { ApiError } from "./api";

describe("ApiError", () => {
  it("preserves the backend error envelope", () => {
    const err = new ApiError("VALIDATION_FAILED", "Request validation failed", {
      email: "Must be a valid email",
    }, 400);
    expect(err.code).toBe("VALIDATION_FAILED");
    expect(err.fields?.email).toBe("Must be a valid email");
    expect(err.status).toBe(400);
    expect(err).toBeInstanceOf(Error);
  });
});
