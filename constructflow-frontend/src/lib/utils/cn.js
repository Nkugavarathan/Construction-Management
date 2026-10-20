import { clsx } from "clsx";

/** Joins class names, skipping falsy values. */
export function cn(...inputs) {
  return clsx(inputs);
}
