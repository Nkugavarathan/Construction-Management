import { expect } from "@playwright/test";

export const PASSWORD = "ConstructFlow#2026";

export async function login(page, email) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Log in" }).click();
  await expect(page).toHaveURL(/\/dashboard/);
  await expect(page.getByRole("heading", { level: 1 })).toContainText("Good to see you");
}

export async function logout(page) {
  await page.getByRole("button", { name: "Account menu" }).click();
  await page.getByRole("menuitem", { name: "Log out" }).click();
  await expect(page).toHaveURL(/\/login/);
}

export function unique(prefix) {
  return `${prefix}-${Date.now().toString(36).slice(-5)}`.toUpperCase();
}

export async function openProjectByCode(page, code) {
  await page.goto("/projects");
  await page.getByRole("textbox", { name: "Search" }).fill(code);
  await page.getByRole("link", { name: new RegExp(code === "KDY-001" ? "Kandy" : ".") }).first().click();
  await expect(page.getByText(code, { exact: false }).first()).toBeVisible();
}
