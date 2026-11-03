import { describe, expect, it } from "vitest";

import type { ActivityItem } from "./api";
import { accessState, clinicToday, describeActivity, relativeTime } from "./format";

const item = (over: Partial<ActivityItem>): ActivityItem => ({
  id: 1,
  action: "STAFF_ADDED",
  occurredAt: "2026-11-03T08:00:00Z",
  actorName: "Faith Akinyi",
  targetName: "Dennis Mutua",
  actorIsTarget: false,
  role: "RECEPTIONIST",
  from: null,
  to: null,
  ...over,
});

describe("accessState", () => {
  it("lets deactivation override any invitation state", () => {
    expect(accessState("DISABLED", "PENDING")).toBe("disabled");
    expect(accessState("DISABLED", "ACCEPTED")).toBe("disabled");
  });

  it("maps invitation lifecycle for active memberships", () => {
    expect(accessState("ACTIVE", "PENDING")).toBe("pending");
    expect(accessState("ACTIVE", "EXPIRED")).toBe("expired");
    expect(accessState("ACTIVE", "ACCEPTED")).toBe("active");
    expect(accessState("ACTIVE", "NONE")).toBe("active");
  });
});

describe("describeActivity", () => {
  it("describes staff lifecycle events in plain language", () => {
    expect(describeActivity(item({}))).toBe("Faith Akinyi added Dennis Mutua as receptionist");
    expect(describeActivity(item({ action: "STAFF_STATUS_CHANGED", to: "DISABLED" }))).toBe(
      "Faith Akinyi deactivated Dennis Mutua",
    );
    expect(describeActivity(item({ action: "STAFF_INVITATION_ACCEPTED" }))).toBe(
      "Dennis Mutua accepted their invitation and activated their account",
    );
  });

  it("phrases operator-issued (self-targeted) invitations as system events", () => {
    expect(describeActivity(item({ action: "STAFF_INVITATION_SENT", actorIsTarget: true }))).toBe(
      "An activation invitation was sent to Dennis Mutua",
    );
  });

  it("falls back for unknown actions and missing names", () => {
    expect(describeActivity(item({ action: "SOMETHING_NEW", actorName: null }))).toBe("Someone · something new");
  });
});

describe("clinicToday", () => {
  it("uses the clinic timezone, not the browser's", () => {
    const lateUtc = new Date("2026-11-03T22:30:00Z");
    expect(clinicToday("Africa/Nairobi", lateUtc)).toBe("2026-11-04");
    expect(clinicToday("UTC", lateUtc)).toBe("2026-11-03");
  });
});

describe("relativeTime", () => {
  it("formats recent timestamps compactly", () => {
    const now = new Date("2026-11-03T10:00:00Z").getTime();
    expect(relativeTime("2026-11-03T09:59:40Z", now)).toBe("just now");
    expect(relativeTime("2026-11-03T09:15:00Z", now)).toBe("45 min ago");
    expect(relativeTime("2026-11-03T05:00:00Z", now)).toBe("5 h ago");
  });
});
