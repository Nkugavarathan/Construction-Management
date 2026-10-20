import { expect, test } from "@playwright/test";
import { login, logout, unique } from "./helpers";

test.describe("ConstructFlow core workflows", () => {
  test("unauthenticated visitors are sent to login", async ({ page }) => {
    await page.goto("/projects");
    await expect(page).toHaveURL(/\/login\?next=%2Fprojects/);
  });

  test("wrong password shows a generic error", async ({ page }) => {
    await page.goto("/login");
    await page.getByLabel("Email").fill("viewer@acme.test");
    await page.getByLabel("Password").fill("not-the-password");
    await page.getByRole("button", { name: "Log in" }).click();
    await expect(page.getByText("Email or password is incorrect.")).toBeVisible();
  });

  test("project manager creates a project and assigns a task; the engineer starts it", async ({ page }) => {
    const code = unique("E2E");
    await login(page, "pm@acme.test");
    await page.getByRole("link", { name: "Projects", exact: true }).click();
    await page.getByRole("link", { name: "New project" }).click();
    await page.getByLabel("Project code").fill(code);
    await page.getByLabel("Project name").fill(`E2E Library ${code}`);
    await page.getByLabel("Client", { exact: true }).fill("City Council");
    await page.getByLabel("Site address").fill("Main Street, Kandy");
    await page.getByLabel("Start date").fill("2026-01-05");
    await page.getByLabel("Planned completion").fill("2027-01-05");
    await page.getByLabel("Approved budget").fill("2500000.00");
    await page.getByRole("button", { name: "Create project" }).click();
    await expect(page.getByRole("heading", { level: 1 })).toHaveText(`E2E Library ${code}`);
    await expect(page.getByText("Planning", { exact: true })).toBeVisible();

    // Add the site engineer to the project team.
    await page.getByRole("link", { name: "Team" }).last().click();
    await page.getByRole("button", { name: "Add member" }).click();
    await page.getByLabel("Person").selectOption({ label: "Kasun Fernando — Site engineer" });
    await page.getByRole("button", { name: "Add", exact: true }).click();
    await expect(page.getByText("Kasun Fernando")).toBeVisible();

    // Start the project, then create an assigned task.
    await page.getByRole("button", { name: "Change status" }).click();
    await page.getByRole("menuitem", { name: "Start / resume project" }).click();
    await page.getByRole("button", { name: "Confirm" }).click();
    await expect(page.getByText("Active", { exact: true }).first()).toBeVisible();

    await page.getByRole("link", { name: "Tasks", exact: true }).click();
    await page.getByRole("button", { name: "New task" }).click();
    await page.getByLabel("Title").fill(`Set out grid lines ${code}`);
    await page.getByLabel("Assignee").selectOption({ label: "Kasun Fernando" });
    await page.getByRole("button", { name: "Create task" }).click();
    await expect(page.getByRole("cell", { name: new RegExp(`Set out grid lines ${code}`) })).toBeVisible();
    await logout(page);

    await login(page, "engineer@acme.test");
    await page.getByRole("link", { name: "My tasks" }).click();
    await page.getByRole("cell", { name: new RegExp(`Set out grid lines ${code}`) }).click();
    await expect(page.getByRole("button", { name: "Cancel task" })).toHaveCount(0); // manager-only action is not offered
    await page.getByRole("button", { name: "Start", exact: true }).click();
    await expect(page.getByText("Task moved to in progress")).toBeVisible();
  });

  test("expense approval enforces segregation of duties", async ({ page }) => {
    const description = `E2E site fuel ${unique("X")}`;
    await login(page, "engineer@acme.test");
    await page.goto("/projects");
    await page.getByRole("link", { name: "Kandy Mixed-Use Block" }).click();
    await page.getByRole("link", { name: "Finance", exact: true }).click();
    await page.getByRole("button", { name: "Record expense" }).click();
    await page.getByLabel("Description").fill(description);
    await page.getByLabel("Category").selectOption({ label: "Equipment" });
    await page.getByLabel("Amount").fill("15000.50");
    await page.getByRole("button", { name: "Save draft" }).click();
    await page.getByRole("button", { name: "Submit for approval" }).click();
    await expect(page.getByText("Submitted for approval")).toBeVisible();
    await logout(page);

    await login(page, "finance@acme.test");
    await page.getByRole("link", { name: "Expenses", exact: true }).click();
    await page.getByRole("cell", { name: new RegExp(description) }).click();
    await page.getByRole("button", { name: "Approve" }).click();
    await page.getByRole("button", { name: "Approve" }).last().click();
    await expect(page.getByText("Expense approved")).toBeVisible();
  });

  test("storekeeper cannot issue more stock than is on hand", async ({ page }) => {
    await login(page, "store@acme.test");
    await page.getByRole("link", { name: "Inventory" }).click();
    await page.getByRole("button", { name: "Issue BLK-150" }).click();
    await page.getByRole("combobox", { name: "Project" }).selectOption({ index: 1 });
    await page.getByLabel(/Quantity to issue/).fill("999999");
    await expect(page.getByText("More than the stock on hand")).toBeVisible();
    await expect(page.getByRole("button", { name: "Confirm" })).toBeDisabled();
  });

  test("navigation and access follow the role", async ({ page }) => {
    await login(page, "viewer@acme.test");
    await expect(page.getByRole("link", { name: "Projects" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Audit log" })).toHaveCount(0);
    await expect(page.getByRole("link", { name: "Expenses" })).toHaveCount(0);
    await page.goto("/audit");
    await expect(page.getByText("You don't have access to this")).toBeVisible();
  });

  test("another organization's project is not visible", async ({ page }) => {
    await login(page, "pm@acme.test");
    await page.goto("/projects");
    await page.getByRole("link", { name: "Kandy Mixed-Use Block" }).click();
    await page.waitForURL(/\/projects\/[0-9a-f-]{36}$/);
    const acmeProjectUrl = page.url();
    await logout(page);

    await login(page, "admin@lanka.test");
    await page.goto(acmeProjectUrl);
    await expect(page.getByText("Not found")).toBeVisible();
    await page.goto("/projects");
    await expect(page.getByText("Kandy Mixed-Use Block")).toHaveCount(0);
  });

  test("administrators see important changes in the audit log", async ({ page }) => {
    await login(page, "admin@acme.test");
    await page.getByRole("link", { name: "Audit log" }).click();
    await page.getByLabel("Action").selectOption("EXPENSE_APPROVED");
    await expect(page.getByRole("cell", { name: /Approved EXP-/ }).first()).toBeVisible();
  });
});
