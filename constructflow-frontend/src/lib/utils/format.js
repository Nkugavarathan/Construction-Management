/**
 * Display formatting. Money and quantities arrive from the API as decimal strings (ADR-0005). They are
 * converted to numbers only to format them for display; no arithmetic on money happens in the browser.
 */
const LOCALE = "en-LK";

export function formatMoney(value, currency = "LKR", { compact = false } = {}) {
  if (value === null || value === undefined || value === "") return "—";
  const number = Number(value);
  if (Number.isNaN(number)) return "—";
  return new Intl.NumberFormat(LOCALE, {
    style: "currency",
    currency,
    notation: compact ? "compact" : "standard",
    maximumFractionDigits: compact ? 1 : 2,
    minimumFractionDigits: compact ? 0 : 2,
  }).format(number);
}

export function formatNumber(value, { maxFractionDigits = 3 } = {}) {
  if (value === null || value === undefined || value === "") return "—";
  return new Intl.NumberFormat(LOCALE, { maximumFractionDigits: maxFractionDigits }).format(Number(value));
}

export function formatPercent(value) {
  if (value === null || value === undefined || value === "") return "—";
  return `${new Intl.NumberFormat(LOCALE, { maximumFractionDigits: 1 }).format(Number(value))}%`;
}

/** Dates like "2026-10-20" are calendar dates: format them without shifting through the browser time zone. */
export function formatDate(value) {
  if (!value) return "—";
  const [year, month, day] = String(value).slice(0, 10).split("-").map(Number);
  return new Intl.DateTimeFormat(LOCALE, { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" })
    .format(new Date(Date.UTC(year, month - 1, day)));
}

export function formatDateTime(value) {
  if (!value) return "—";
  return new Intl.DateTimeFormat(LOCALE, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export function formatRelative(value) {
  if (!value) return "";
  const seconds = Math.round((new Date(value).getTime() - Date.now()) / 1000);
  const units = [
    ["year", 31536000],
    ["month", 2592000],
    ["day", 86400],
    ["hour", 3600],
    ["minute", 60],
  ];
  const rtf = new Intl.RelativeTimeFormat(LOCALE, { numeric: "auto" });
  for (const [unit, size] of units) {
    if (Math.abs(seconds) >= size) return rtf.format(Math.round(seconds / size), unit);
  }
  return "just now";
}

export function humanize(value) {
  if (!value) return "";
  return String(value)
    .toLowerCase()
    .split("_")
    .map((part, index) => (index === 0 ? part.charAt(0).toUpperCase() + part.slice(1) : part))
    .join(" ");
}

export function todayIso() {
  const now = new Date();
  const offset = now.getTimezoneOffset() * 60000;
  return new Date(now.getTime() - offset).toISOString().slice(0, 10);
}

export function initials(name = "") {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}
