/**
 * Date and Time utilities for GATE CSE Companion
 * Strict adherence to Asia/Kolkata timezone.
 */

export const IST_TIMEZONE = "Asia/Kolkata";

/**
 * Returns today's date in YYYY-MM-DD format in Asia/Kolkata timezone.
 */
export function getTodayDateStr(): string {
  const now = new Date();
  const formatter = new Intl.DateTimeFormat("en-CA", {
    timeZone: IST_TIMEZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  return formatter.format(now);
}

/**
 * Formats a Date object or timestamp into YYYY-MM-DD in Asia/Kolkata timezone.
 */
export function formatDateStr(date: Date | number | string): string {
  const d = typeof date === "string" ? new Date(date + "T00:00:00+05:30") : new Date(date);
  const formatter = new Intl.DateTimeFormat("en-CA", {
    timeZone: IST_TIMEZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  return formatter.format(d);
}

/**
 * Formats a YYYY-MM-DD date string into human-readable format, e.g. "Wed, 21 Oct 2026"
 */
export function formatDisplayDate(dateStr: string): string {
  try {
    const parts = dateStr.split("-").map(Number);
    const d = new Date(parts[0], parts[1] - 1, parts[2]);
    return d.toLocaleDateString("en-IN", {
      weekday: "short",
      day: "numeric",
      month: "short",
      year: "numeric",
    });
  } catch {
    return dateStr;
  }
}

/**
 * Compares two YYYY-MM-DD date strings.
 * Returns negative if d1 < d2, 0 if equal, positive if d1 > d2.
 */
export function compareDateStrings(d1: string, d2: string): number {
  return d1.localeCompare(d2);
}

/**
 * Adds days to a YYYY-MM-DD date string and returns the new YYYY-MM-DD string.
 */
export function addDaysToDateStr(dateStr: string, days: number): string {
  const parts = dateStr.split("-").map(Number);
  const d = new Date(parts[0], parts[1] - 1, parts[2]);
  d.setDate(d.getDate() + days);
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/**
 * Calculates days between two date strings (d2 - d1).
 */
export function daysBetween(d1: string, d2: string): number {
  const p1 = d1.split("-").map(Number);
  const p2 = d2.split("-").map(Number);
  const date1 = Date.UTC(p1[0], p1[1] - 1, p1[2]);
  const date2 = Date.UTC(p2[0], p2[1] - 1, p2[2]);
  return Math.round((date2 - date1) / (1000 * 60 * 60 * 24));
}

/**
 * Checks if date is a Sunday.
 */
export function isSundayDate(dateStr: string): boolean {
  const parts = dateStr.split("-").map(Number);
  const d = new Date(parts[0], parts[1] - 1, parts[2]);
  return d.getDay() === 0;
}
