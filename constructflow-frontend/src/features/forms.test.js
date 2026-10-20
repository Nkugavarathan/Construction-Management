import { describe, expect, it } from "vitest";
import { expenseSchema } from "@/features/finance/ExpenseForm";
import { projectSchema } from "@/features/projects/ProjectForm";
import { taskSchema } from "@/features/tasks/TaskForm";
import { purchaseOrderSchema } from "@/features/procurement/PurchaseOrderForm";
import { loginSchema, safeNext } from "@/features/auth/LoginForm";
import { changePasswordSchema } from "@/features/auth/ChangePasswordForm";
import { todayIso } from "@/lib/utils/format";

const issues = (result) => result.error?.issues.map((i) => `${i.path.join(".")}: ${i.message}`) ?? [];

describe("client-side validation (fast feedback; the API remains authoritative)", () => {
  it("expense amounts must be positive with at most two decimals and not dated in the future", () => {
    const base = { categoryId: "c1", description: "Cement", amount: "100.00", expenseDate: todayIso() };
    expect(expenseSchema.safeParse(base).success).toBe(true);
    expect(issues(expenseSchema.safeParse({ ...base, amount: "10.005" }))).toContain("amount: Enter an amount with up to 2 decimals");
    expect(issues(expenseSchema.safeParse({ ...base, amount: "0" }))).toContain("amount: Must be greater than zero");
    expect(issues(expenseSchema.safeParse({ ...base, expenseDate: "2999-01-01" }))).toContain("expenseDate: Cannot be in the future");
  });

  it("project completion date cannot precede the start date", () => {
    const base = { code: "KDY-1", name: "Block", clientName: "C", siteAddress: "S", startDate: "2026-05-01", plannedEndDate: "2026-04-01", approvedBudget: "1000" };
    expect(issues(projectSchema.safeParse(base))).toContain("plannedEndDate: Must be on or after the start date");
    expect(projectSchema.safeParse({ ...base, plannedEndDate: "2026-06-01" }).success).toBe(true);
    expect(issues(projectSchema.safeParse({ ...base, code: "bad code!" }))[0]).toMatch(/^code:/);
  });

  it("task weight is 1-10 and due date follows start date", () => {
    const base = { title: "Pour", priority: "HIGH", weight: "3", startDate: "2026-05-02", dueDate: "2026-05-01" };
    expect(issues(taskSchema.safeParse(base))).toContain("dueDate: Must be on or after the start date");
    expect(taskSchema.safeParse({ ...base, weight: "11", dueDate: "" }).success).toBe(false);
  });

  it("purchase orders need lines, each material once", () => {
    const line = { materialId: "m1", quantity: "10", unitPrice: "2.5" };
    expect(purchaseOrderSchema.safeParse({ supplierId: "s", items: [] }).success).toBe(false);
    expect(purchaseOrderSchema.safeParse({ supplierId: "s", items: [line, line] }).success).toBe(false);
    expect(purchaseOrderSchema.safeParse({ supplierId: "s", items: [line] }).success).toBe(true);
  });

  it("login and password rules", () => {
    expect(loginSchema.safeParse({ email: "not-an-email", password: "x" }).success).toBe(false);
    const pw = { currentPassword: "old-password-123", newPassword: "short", confirmPassword: "short" };
    expect(issues(changePasswordSchema.safeParse(pw))).toContain("newPassword: Use at least 12 characters");
    expect(issues(changePasswordSchema.safeParse({ ...pw, newPassword: "a-long-password-1", confirmPassword: "different-1234" })))
      .toContain("confirmPassword: Passwords don't match");
  });

  it("only allows same-site relative redirects after login", () => {
    expect(safeNext("/projects/1")).toBe("/projects/1");
    expect(safeNext("https://evil.example")).toBe("/dashboard");
    expect(safeNext("//evil.example")).toBe("/dashboard");
    expect(safeNext(null)).toBe("/dashboard");
  });
});
