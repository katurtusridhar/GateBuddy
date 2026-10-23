import { describe, it, expect } from "vitest";
import {
  formatDateStr,
  getTodayDateStr,
  compareDateStrings,
  addDaysToDateStr,
  daysBetween,
  isSundayDate,
  formatDisplayDate,
} from "../dateUtils";

describe("Date Utilities (Asia/Kolkata timezone compliance)", () => {
  it("formats YYYY-MM-DD reliably in IST", () => {
    const str = formatDateStr("2026-10-23");
    expect(str).toBe("2026-10-23");
  });

  it("returns a valid today date string", () => {
    const today = getTodayDateStr();
    expect(today).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it("correctly compares date strings chronologically", () => {
    expect(compareDateStrings("2026-10-21", "2026-10-23")).toBeLessThan(0);
    expect(compareDateStrings("2026-10-23", "2026-10-23")).toBe(0);
    expect(compareDateStrings("2026-10-25", "2026-10-23")).toBeGreaterThan(0);
  });

  it("correctly adds and subtracts days without timezone drift", () => {
    expect(addDaysToDateStr("2026-10-21", 2)).toBe("2026-10-23");
    expect(addDaysToDateStr("2026-10-23", -2)).toBe("2026-10-21");
    expect(addDaysToDateStr("2026-10-31", 1)).toBe("2026-11-01");
  });

  it("calculates exact calendar days between two dates", () => {
    expect(daysBetween("2026-10-21", "2026-10-23")).toBe(2);
    expect(daysBetween("2026-10-23", "2026-10-21")).toBe(-2);
  });

  it("correctly identifies Sunday dates", () => {
    expect(isSundayDate("2026-10-25")).toBe(true); // Sunday
    expect(isSundayDate("2026-10-21")).toBe(false); // Wednesday
    expect(isSundayDate("2026-10-23")).toBe(false); // Friday
  });

  it("formats human-readable display date", () => {
    const display = formatDisplayDate("2026-10-21");
    expect(display).toContain("21");
    expect(display).toContain("Oct");
    expect(display).toContain("2026");
  });
});
