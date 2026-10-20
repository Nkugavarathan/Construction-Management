import { describe, expect, it } from "vitest";
import { formatDate, formatMoney, formatPercent, humanize, initials } from "./format";

describe("formatting", () => {
  it("formats decimal-string money without losing cents", () => {
    expect(formatMoney("125000.50", "LKR")).toMatch(/125,000\.50/);
    expect(formatMoney(null)).toBe("—");
  });

  it("formats calendar dates without shifting the day across time zones", () => {
    expect(formatDate("2026-10-20")).toMatch(/20/);
    expect(formatDate("2026-01-01")).toMatch(/2026/);
    expect(formatDate(null)).toBe("—");
  });

  it("formats percentages and enum names", () => {
    expect(formatPercent("4.53")).toBe("4.5%");
    expect(humanize("PARTIALLY_RECEIVED")).toBe("Partially received");
    expect(initials("Nuwan Silva")).toBe("NS");
  });
});
